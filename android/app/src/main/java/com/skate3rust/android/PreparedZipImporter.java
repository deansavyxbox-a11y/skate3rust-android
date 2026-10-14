package com.skate3rust.android;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Streams a selected ZIP into private staging. It never creates filesystem links. */
final class PreparedZipImporter {
    private static final int MAX_ARCHIVE_ENTRIES=10_000;
    private static final int MAX_CENTRAL_TAIL=8*1024*1024;
    private PreparedZipImporter() {}

    static AssetManifest importFrom(InputStream source,File staging,ImportModel.Progress progress,
                                   ImportModel.Cancellation cancellation)throws IOException {
        File stage=staging.getCanonicalFile();
        if(!stage.isDirectory()||Files.isSymbolicLink(staging.toPath()))throw new IOException("Invalid import staging directory");
        String[] children=stage.list();if(children==null||children.length!=0)throw new IOException("Import staging directory is not empty");
        Set<String> names=new HashSet<>();List<String> observed=new ArrayList<>();long expanded=0;int count=0;byte[] buffer=new byte[128*1024];
        ZipTail tail=new ZipTail(source,MAX_CENTRAL_TAIL);
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(tail))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                cancellation.check();if(++count>MAX_ARCHIVE_ENTRIES)throw new IOException("Prepared ZIP contains too many entries");
                String raw=entry.getName();boolean directory=entry.isDirectory();
                if(directory&&raw.endsWith("/"))raw=raw.substring(0,raw.length()-1);
                AssetManifest.validatePath(raw);
                if(!allowed(raw))throw new IOException("Unexpected prepared ZIP entry: "+raw);
                String folded=raw.toLowerCase(Locale.ROOT);
                if(!names.add(folded))throw new IOException("Duplicate prepared ZIP entry: "+raw);
                observed.add(raw);
                File output=AssetManifest.contained(stage,raw);
                if(directory)Files.createDirectories(output.toPath());
                else {
                    Files.createDirectories(output.toPath().getParent());
                    try(OutputStream out=Files.newOutputStream(output.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                        int n;while((n=zip.read(buffer))!=-1) {
                            cancellation.check();expanded=Math.addExact(expanded,n);
                            if(expanded>AssetManifest.MAX_BYTES)throw new IOException("Prepared ZIP expands beyond the allowed size");
                            out.write(buffer,0,n);progress.report(ImportModel.Phase.IMPORTING,expanded,-1,raw);
                        }
                    } catch(ArithmeticException e){throw new IOException("Prepared ZIP size overflow",e);}
                }
                zip.closeEntry();
            }
            tail.verifyCentralDirectory(observed);
        } catch(UncheckedIOException e){throw e.getCause();}
        catch(IOException|RuntimeException e){
            cleanup(stage);
            if(e instanceof IOException io)throw io;
            if(e instanceof UncheckedIOException unchecked)throw unchecked.getCause();
            throw new IOException("Could not read prepared ZIP",e);
        }
        try {
            AssetManifest manifest=AssetManifest.read(stage);
            if(manifest.totalBytes>AssetManifest.MAX_BYTES)throw new IOException("Prepared asset package is too large");
            manifest.verify(stage,progress,cancellation);
            return manifest;
        } catch(IOException|RuntimeException e){cleanup(stage);if(e instanceof IOException io)throw io;throw new IOException("Could not validate prepared ZIP",e);}
    }

    private static boolean allowed(String path) {
        return path.equals("complete.json")||path.equals("transfer-manifest.json")||path.equals("sha256sum.checks")||
            path.equals("assets")||path.startsWith("assets/")||path.equals("maps")||path.startsWith("maps/")||path.equals("settings")||path.startsWith("settings/");
    }
    /** Retain only a bounded tail so ZIP central metadata is checked without copying the archive. */
    private static final class ZipTail extends FilterInputStream {
        private final byte[] ring;
        private long total;
        ZipTail(InputStream input,int capacity){super(input);ring=new byte[capacity];}
        @Override public int read()throws IOException {
            int value=super.read();if(value>=0){ring[(int)(total%ring.length)]=(byte)value;total++;}return value;
        }
        @Override public int read(byte[] bytes,int offset,int length)throws IOException {
            int count=super.read(bytes,offset,length);if(count>0){for(int i=0;i<count;i++)ring[(int)((total+i)%ring.length)]=bytes[offset+i];total+=count;}return count;
        }
        void verifyCentralDirectory(List<String> observed)throws IOException {
            int available=(int)Math.min(total,ring.length);long base=total-available;byte[] data=new byte[available];
            int first=(int)(base%ring.length),head=Math.min(available,ring.length-first);
            System.arraycopy(ring,first,data,0,head);if(head<available)System.arraycopy(ring,0,data,head,available-head);
            ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int eocd=-1;for(int p=available-22;p>=Math.max(0,available-65557);p--)if(u32(b,p)==0x06054b50L){eocd=p;break;}
            if(eocd<0)throw new IOException("Prepared ZIP central directory is missing or too large");
            int comment=u16(b,eocd+20);if(eocd+22+comment!=available)throw new IOException("Invalid ZIP end record");
            long count=u16(b,eocd+10),centralSize=u32(b,eocd+12),centralOffset=u32(b,eocd+16),centralEnd=base+eocd;
            if(u16(b,eocd+4)!=0||u16(b,eocd+6)!=0||u16(b,eocd+8)!=u16(b,eocd+10))throw new IOException("Multi-disk ZIP archives are unsupported");
            if(count==0xffff||centralSize==0xffffffffL||centralOffset==0xffffffffL) {
                int locator=eocd-20;if(locator<0||u32(b,locator)!=0x07064b50L||u32(b,locator+4)!=0||u32(b,locator+16)!=1)throw new IOException("Invalid ZIP64 locator");
                long recordOffset=u64(b,locator+8);int z=absoluteIndex(recordOffset,base,available);
                if(z<0||z+56>available||u32(b,z)!=0x06064b50L)throw new IOException("ZIP64 metadata exceeds allowed bounds");
                if(u32(b,z+16)!=0||u32(b,z+20)!=0)throw new IOException("Multi-disk ZIP64 archives are unsupported");
                count=u64(b,z+32);centralSize=u64(b,z+40);centralOffset=u64(b,z+48);centralEnd=base+z;
            }
            if(count!=observed.size()||count>MAX_ARCHIVE_ENTRIES||centralSize<0||centralOffset<0||centralOffset+centralSize!=centralEnd)throw new IOException("ZIP central directory does not match its entries");
            int p=absoluteIndex(centralOffset,base,available);if(p<0||centralSize>Integer.MAX_VALUE)throw new IOException("ZIP central directory is outside bounded metadata");
            int limit=p+(int)centralSize;
            for(int index=0;index<count;index++) {
                if(p+46>limit||u32(b,p)!=0x02014b50L)throw new IOException("Invalid ZIP central directory entry");
                int flags=u16(b,p+8),nameLength=u16(b,p+28),extraLength=u16(b,p+30),commentLength=u16(b,p+32),disk=u16(b,p+34);
                long external=u32(b,p+38);if((flags&1)!=0||(flags&0x40)!=0||disk!=0)throw new IOException("Encrypted or split ZIP entries are unsupported");
                long mode=(external>>>16)&0xffff;if((mode&0xf000)==0xa000)throw new IOException("ZIP symbolic links are not supported");
                int end=p+46+nameLength+extraLength+commentLength;if(end>limit)throw new IOException("Truncated ZIP central entry");
                Charset charset=(flags&0x800)!=0?StandardCharsets.UTF_8:Charset.forName("IBM437");
                String name=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data,p+46,nameLength)).toString();
                if(name.endsWith("/"))name=name.substring(0,name.length()-1);
                if(!name.equals(observed.get(index)))throw new IOException("ZIP local and central entry names differ");
                p=end;
            }
            if(p!=limit)throw new IOException("Unexpected bytes in ZIP central directory");
        }
        private static int absoluteIndex(long offset,long base,int length){long value=offset-base;return value<0||value>length? -1:(int)value;}
        private static int u16(ByteBuffer b,int p)throws IOException{if(p<0||p+2>b.limit())throw new IOException("Truncated ZIP metadata");return Short.toUnsignedInt(b.getShort(p));}
        private static long u32(ByteBuffer b,int p)throws IOException{if(p<0||p+4>b.limit())throw new IOException("Truncated ZIP metadata");return Integer.toUnsignedLong(b.getInt(p));}
        private static long u64(ByteBuffer b,int p)throws IOException{long value=b.getLong(p);if(value<0)throw new IOException("ZIP64 offset exceeds supported range");return value;}
    }
    private static void cleanup(File root)throws IOException {
        if(Files.isSymbolicLink(root.toPath()))throw new IOException("Refusing to clean linked staging");
        try(var paths=Files.walk(root.toPath())) {
            for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.deleteIfExists(path);
        }
        Files.createDirectories(root.toPath());
    }
}