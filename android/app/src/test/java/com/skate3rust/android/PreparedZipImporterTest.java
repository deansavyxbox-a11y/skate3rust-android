package com.skate3rust.android;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class PreparedZipImporterTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final ImportModel.Progress progress=(phase,done,total,item)->{};
    private final ImportModel.Cancellation cancel=()->{};

    private byte[] archive(String badName,boolean duplicate,boolean corrupt)throws Exception {
        File source=ImportFixtures.create(temp.newFolder(),"University");
        if(corrupt)ImportFixtures.write(source,"maps/University.skate","corrupted");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)) {
            try(var paths=Files.walk(source.toPath())) {
                for(Path path:paths.filter(Files::isRegularFile).sorted().toList()) {
                    String name=source.toPath().relativize(path).toString().replace('\\','/');
                    if(badName!=null&&name.equals("maps/University.skate"))name=badName;
                    zip.putNextEntry(new ZipEntry(name));Files.copy(path,zip);zip.closeEntry();
                    if(duplicate&&name.equals("maps/University.skate")){
                        zip.putNextEntry(new ZipEntry(name));zip.write(Files.readAllBytes(path));zip.closeEntry();
                    }
                }
            }
        }
        return bytes.toByteArray();
    }
    private File staging()throws Exception {
        File stage=temp.newFolder();for(File child:stage.listFiles())assertTrue(child.delete());return stage;
    }
    private byte[] asZip64EndRecords(byte[] archive) {
        java.nio.ByteBuffer source=java.nio.ByteBuffer.wrap(archive).order(java.nio.ByteOrder.LITTLE_ENDIAN);int eocd=-1;
        for(int i=archive.length-22;i>=0;i--)if(source.getInt(i)==0x06054b50){eocd=i;break;}
        if(eocd<0)throw new AssertionError("ZIP end record missing");
        int entries=Short.toUnsignedInt(source.getShort(eocd+10));long size=Integer.toUnsignedLong(source.getInt(eocd+12)),offset=Integer.toUnsignedLong(source.getInt(eocd+16));
        java.nio.ByteBuffer records=java.nio.ByteBuffer.allocate(76).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        records.putInt(0x06064b50).putLong(44).putShort((short)45).putShort((short)45).putInt(0).putInt(0).putLong(entries).putLong(entries).putLong(size).putLong(offset);
        records.putInt(0x07064b50).putInt(0).putLong(eocd).putInt(1);
        source.putShort(eocd+8,(short)0xffff).putShort(eocd+10,(short)0xffff).putInt(eocd+12,-1).putInt(eocd+16,-1);
        byte[] out=new byte[archive.length+76];System.arraycopy(archive,0,out,0,eocd);System.arraycopy(records.array(),0,out,eocd,76);System.arraycopy(archive,eocd,out,eocd+76,archive.length-eocd);return out;
    }
    @Test public void importsPreparedFixture()throws Exception {
        byte[] source=archive(null,false,false);byte[] original=source.clone();File stage=staging();
        AssetManifest result=PreparedZipImporter.importFrom(new ByteArrayInputStream(source),stage,progress,cancel);
        assertEquals(1,result.maps.size());assertEquals("University",result.maps.get(0));
        result.verify(stage,progress,cancel);assertArrayEquals(original,source);
    }
    @Test public void acceptsDocumentProviderWithShortReads()throws Exception {
        byte[] source=archive(null,false,false);
        InputStream fragmented=new ByteArrayInputStream(source){
            @Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(len,37));}
        };
        File stage=staging();
        AssetManifest result=PreparedZipImporter.importFrom(fragmented,stage,progress,cancel);
        result.verify(stage,progress,cancel);
    }
    @Test public void readsEndRecordBeyondZipStreamReadAhead()throws Exception {
        byte[] source=archive(null,false,false);
        byte[] extended=java.util.Arrays.copyOf(source,source.length+20000);
        java.nio.ByteBuffer.wrap(extended).order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort(source.length-2,(short)20000);
        java.util.Arrays.fill(extended,source.length,extended.length,(byte)'x');
        File stage=staging();
        AssetManifest result=PreparedZipImporter.importFrom(new ByteArrayInputStream(extended),stage,progress,cancel);
        result.verify(stage,progress,cancel);
    }
    private byte[] asUnixSymlink(byte[] archive) {
        byte[] marked=archive.clone();
        for(int i=0;i+46<marked.length;i++)if((marked[i]&255)==0x50&&(marked[i+1]&255)==0x4b&&(marked[i+2]&255)==0x01&&(marked[i+3]&255)==0x02) {
            java.nio.ByteBuffer central=java.nio.ByteBuffer.wrap(marked).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            central.putShort(i+4,(short)0x0314);central.putInt(i+38,0120777<<16);return marked;
        }
        throw new AssertionError("ZIP central directory missing");
    }
    @Test public void acceptsZip64EndRecords()throws Exception {
        byte[] archive=asZip64EndRecords(archive(null,false,false));File stage=staging();
        AssetManifest result=PreparedZipImporter.importFrom(new ByteArrayInputStream(archive),stage,progress,cancel);
        assertTrue(result.maps.contains("University"));
    }
    @Test public void rejectsTraversalAndDuplicatePaths()throws Exception {
        File stage=staging();
        File traversalStage=stage;
        assertThrows(IOException.class,()->PreparedZipImporter.importFrom(new ByteArrayInputStream(archive("../escape",false,false)),traversalStage,progress,cancel));
        assertFalse(new File(stage,"maps/University.skate").exists());
        stage=staging();File finalStage=stage;
        assertThrows(IOException.class,()->PreparedZipImporter.importFrom(new ByteArrayInputStream(archive(null,true,false)),finalStage,progress,cancel));
    }
    @Test public void rejectsHashMismatchAndProviderFailure()throws Exception {
        File stage=staging();
        File corruptStage=stage;
        assertThrows(IOException.class,()->PreparedZipImporter.importFrom(new ByteArrayInputStream(archive(null,false,true)),corruptStage,progress,cancel));
        byte[] source=archive(null,false,false);stage=staging();File finalStage=stage;
        InputStream broken=new ByteArrayInputStream(source){@Override public synchronized int read(byte[] b,int off,int len){if(pos>=120)throw new UncheckedIOException(new IOException("provider disconnected"));return super.read(b,off,Math.min(37,Math.min(len,source.length-pos)));}};
        assertThrows(IOException.class,()->PreparedZipImporter.importFrom(broken,finalStage,progress,cancel));
    }
    @Test public void rejectsUnlistedPayload()throws Exception {
        File source=ImportFixtures.create(temp.newFolder(),"University");ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes);var paths=Files.walk(source.toPath())) {
            for(Path path:paths.filter(Files::isRegularFile).sorted().toList()) {
                String name=source.toPath().relativize(path).toString().replace('\\','/');zip.putNextEntry(new ZipEntry(name));Files.copy(path,zip);zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("assets/private/unlisted.bin"));zip.write(new byte[]{1});zip.closeEntry();
        }
        File stage=staging();assertThrows(IOException.class,()->PreparedZipImporter.importFrom(new ByteArrayInputStream(bytes.toByteArray()),stage,progress,cancel));
        assertEquals(0,stage.list().length);
    }
    @Test public void rejectsUnixSymlinkMetadata()throws Exception {
        File stage=staging();byte[] linked=asUnixSymlink(archive(null,false,false));
        assertThrows(IOException.class,()->PreparedZipImporter.importFrom(new ByteArrayInputStream(linked),stage,progress,cancel));
        assertEquals(0,stage.list().length);
    }
    @Test public void cancellationAndOversizeLeaveNoExtractedFiles()throws Exception {
        byte[] source=archive(null,false,false);File stage=staging();
        ImportModel.Cancellation stop=()->{throw new InterruptedIOException("cancel");};
        assertThrows(InterruptedIOException.class,()->PreparedZipImporter.importFrom(new ByteArrayInputStream(source),stage,progress,stop));
        assertEquals(0,stage.list().length);
    }
}