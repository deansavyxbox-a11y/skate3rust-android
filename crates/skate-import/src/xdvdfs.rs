use serde::Serialize;
use std::{collections::HashSet, fs::{self,OpenOptions}, io::{self,Read,Seek,SeekFrom,Write}, path::Path};

const SECTOR:u64=2048;
const MAX_ENTRIES:usize=100_000;
const MAX_TABLE:usize=32*1024*1024;
const MAX_TOTAL:u64=32*1024*1024*1024;
const MAGIC:&[u8;20]=b"MICROSOFT*XBOX*MEDIA";
const PARTITIONS:[u64;4]=[0,0x0fd9_0000,0x0208_0000,0x1830_0000];

#[derive(Debug,Clone,Serialize,PartialEq,Eq)]
pub struct DiscEntry { pub path:String,pub offset:u64,pub size:u64,pub is_dir:bool }
#[derive(Debug,Clone,Serialize)]
pub struct DiscIndex { pub partition_offset:u64,pub files:Vec<DiscEntry>,pub total_bytes:u64 }

/// Reads only the fixed, documented volume descriptor positions for XISO, XGD2, XGD3 and XGD1 images.
pub fn inspect_iso<R:Read+Seek>(reader:&mut R)->Result<DiscIndex,String>{
    let image_len=reader.seek(SeekFrom::End(0)).map_err(io_error)?;
    let mut selected=None;
    for partition in PARTITIONS {
        let descriptor=partition.checked_add(0x10000).ok_or("Partition offset overflow")?;
        if descriptor.checked_add(0x20).is_none_or(|end|end>image_len){continue}
        reader.seek(SeekFrom::Start(descriptor)).map_err(io_error)?;
        let mut header=[0u8;32];reader.read_exact(&mut header).map_err(io_error)?;
        if &header[..20]!=MAGIC {continue;}
        let sector=u32le(&header[20..24]) as u64;let size=u32le(&header[24..28]) as usize;
        if size<16||size>MAX_TABLE{return Err("XDVDFS root directory table has an invalid size".into())}
        let root=partition.checked_add(sector.checked_mul(SECTOR).ok_or("Root directory offset overflow")?).ok_or("Root directory offset overflow")?;
        if root.checked_add(size as u64).is_none_or(|end|end>image_len){return Err("XDVDFS root directory table extends beyond the image".into())}
        selected=Some((partition,root,size));break;
    }
    let (partition,root,size)=selected.ok_or("Unsupported Xbox ISO layout: XDVDFS volume descriptor not found at a supported partition boundary")?;
    let mut output=Vec::new();let mut directories=vec![(String::new(),root,size,0usize)];let mut table_starts=HashSet::new();let mut table_extents=Vec::new();let mut all_paths=HashSet::new();let mut total=0u64;
    while let Some((parent,table_offset,table_size,depth))=directories.pop(){
        if depth>64{return Err("XDVDFS directory nesting is too deep".into())}
        if !table_starts.insert(table_offset){return Err("XDVDFS directory cycle or overlapping directory table".into())}
        let table_end=table_offset.checked_add(table_size as u64).ok_or("Directory table extent overflow")?;
        if table_extents.iter().any(|(start,end)|table_offset<*end&&*start<table_end){return Err("XDVDFS directory tables overlap".into())}
        table_extents.push((table_offset,table_end));
        if table_size<16||table_size>MAX_TABLE{return Err("XDVDFS directory table exceeds safe bounds".into())}
        let mut table=vec![0u8;table_size];reader.seek(SeekFrom::Start(table_offset)).map_err(io_error)?;reader.read_exact(&mut table).map_err(io_error)?;
        let mut stack=vec![0usize];let mut visited=HashSet::new();let mut local_names=HashSet::new();
        while let Some(position)=stack.pop(){
            if position==0xffff*4 {continue}
            if !visited.insert(position){return Err("XDVDFS directory tree contains a cycle".into())}
            if visited.len()>MAX_ENTRIES{return Err("XDVDFS directory has too many entries".into())}
            if position.checked_add(14).is_none_or(|end|end>table.len()){return Err("XDVDFS directory entry is outside its table".into())}
            let left=u16le(&table[position..position+2]) as usize;let right=u16le(&table[position+2..position+4]) as usize;
            let sector=u32le(&table[position+4..position+8]) as u64;let size=u32le(&table[position+8..position+12]) as u64;
            let attributes=table[position+12];let name_len=table[position+13] as usize;
            if name_len==0||name_len>255||position.checked_add(14+name_len).is_none_or(|end|end>table.len()){return Err("XDVDFS entry has an invalid name length".into())}
            let name_bytes=&table[position+14..position+14+name_len];
            if !name_bytes.iter().all(|b|b.is_ascii_graphic()&&*b!=b'/'&&*b!=b'\\'&&*b!=b':'){return Err("XDVDFS entry contains an unsafe path".into())}
            let name=std::str::from_utf8(name_bytes).map_err(|_|"XDVDFS filename is not ASCII")?;
            if name=="."||name==".."{return Err("XDVDFS directory traversal entry is unsupported".into())}
            if !local_names.insert(name.to_ascii_lowercase()){return Err("XDVDFS directory contains duplicate names".into())}
            let path=if parent.is_empty(){name.to_owned()}else{format!("{parent}/{name}")};
            if !all_paths.insert(path.to_ascii_lowercase()){return Err("XDVDFS contains duplicate paths".into())}
            if output.len()>=MAX_ENTRIES{return Err("XDVDFS contains too many files".into())}
            let offset=partition.checked_add(sector.checked_mul(SECTOR).ok_or("File extent overflow")?).ok_or("File extent overflow")?;
            if offset.checked_add(size).is_none_or(|end|end>image_len){return Err(format!("XDVDFS extent for {path} lies outside the image"))}
            let is_dir=attributes&0x10!=0;
            if is_dir {
                if size<16||size>MAX_TABLE as u64{return Err(format!("Directory {path} has an invalid table size"))}
                directories.push((path.clone(),offset,size as usize,depth+1));
            } else {total=total.checked_add(size).ok_or("Disc file total overflow")?;if total>MAX_TOTAL{return Err("Xbox image contents exceed the supported 32 GiB limit".into())}}
            output.push(DiscEntry{path,offset,size,is_dir});
            if left!=0{stack.push(left.checked_mul(4).ok_or("Directory child offset overflow")?)}
            if right!=0{stack.push(right.checked_mul(4).ok_or("Directory child offset overflow")?)}
        }
    }
    if output.is_empty(){return Err("Xbox image contains no directory entries".into())}
    let mut extents:Vec<(u64,u64)>=output.iter().filter(|entry|!entry.is_dir&&entry.size>0).map(|entry|(entry.offset,entry.offset+entry.size)).collect();
    extents.sort_unstable();if extents.windows(2).any(|pair|pair[0].1>pair[1].0){return Err("XDVDFS file extents overlap".into())}
    Ok(DiscIndex{partition_offset:partition,files:output,total_bytes:total})
}

/// Extracts a validated index without following links or trusting image paths.
pub fn extract_files<R:Read+Seek,C:Fn()->bool,P:Fn(u64,u64,&str)>(reader:&mut R,index:&DiscIndex,destination:&Path,cancelled:&C,progress:&P)->Result<(),String>{
    fs::create_dir_all(destination).map_err(io_error)?;let root=destination.canonicalize().map_err(io_error)?;let mut done=0u64;let mut buffer=vec![0u8;128*1024];
    for entry in &index.files {
        if cancelled(){return Err("Extraction cancelled".into())}
        validate_relative(&entry.path)?;let path=root.join(&entry.path);
        if entry.is_dir {fs::create_dir_all(&path).map_err(io_error)?;if path.canonicalize().map_err(io_error)?.strip_prefix(&root).is_err(){return Err("Destination directory escapes extraction root".into())}continue}
        let parent=path.parent().ok_or("Invalid destination path")?;fs::create_dir_all(parent).map_err(io_error)?;
        if parent.canonicalize().map_err(io_error)?.strip_prefix(&root).is_err(){return Err("Destination path escapes extraction root".into())}
        let mut output=OpenOptions::new().write(true).create_new(true).open(&path).map_err(io_error)?;reader.seek(SeekFrom::Start(entry.offset)).map_err(io_error)?;let mut remaining=entry.size;
        while remaining>0{if cancelled(){return Err("Extraction cancelled".into())}let length=buffer.len().min(remaining as usize);reader.read_exact(&mut buffer[..length]).map_err(io_error)?;output.write_all(&buffer[..length]).map_err(io_error)?;remaining-=length as u64;done+=length as u64;progress(done,index.total_bytes,&entry.path)}
        output.sync_all().map_err(io_error)?;
    }
    Ok(())
}
fn validate_relative(path:&str)->Result<(),String>{if path.is_empty()||path.starts_with('/')||path.contains(['\\',':'])||path.split('/').any(|p|p.is_empty()||p=="."||p==".."||p.ends_with(' ')||p.ends_with('.')){Err("Unsafe XDVDFS path".into())}else{Ok(())}}
fn u16le(bytes:&[u8])->u16{u16::from_le_bytes([bytes[0],bytes[1]])}
fn u32le(bytes:&[u8])->u32{u32::from_le_bytes([bytes[0],bytes[1],bytes[2],bytes[3]])}
fn io_error(error:io::Error)->String{error.to_string()}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{collections::BTreeMap, io::{Read, Seek, SeekFrom}};

    #[derive(Default)]
    struct Sparse { pos:u64, bytes:BTreeMap<u64,u8>, len:u64 }
    impl Read for Sparse { fn read(&mut self,out:&mut[u8])->std::io::Result<usize>{if self.pos>=self.len{return Ok(0)}let n=out.len().min((self.len-self.pos) as usize);for (i,b) in out[..n].iter_mut().enumerate(){*b=*self.bytes.get(&(self.pos+i as u64)).unwrap_or(&0)}self.pos+=n as u64;Ok(n)} }
    impl Seek for Sparse { fn seek(&mut self,from:SeekFrom)->std::io::Result<u64>{self.pos=match from{SeekFrom::Start(n)=>n,SeekFrom::End(n)=>(self.len as i64+n) as u64,SeekFrom::Current(n)=>(self.pos as i64+n) as u64};Ok(self.pos)} }
    fn put(image:&mut Sparse,at:u64,data:&[u8]){for (i,b) in data.iter().enumerate(){image.bytes.insert(at+i as u64,*b);}image.len=image.len.max(at+data.len() as u64)}
    fn put_u16(image:&mut Sparse,at:u64,value:u16){put(image,at,&value.to_le_bytes())}
    fn put_u32(image:&mut Sparse,at:u64,value:u32){put(image,at,&value.to_le_bytes())}
    fn image(partition:u64,root_sector:u32,entry_name:&[u8],file_sector:u32,file_size:u32)->Sparse{
        let mut image=Sparse::default();let volume=partition+0x10000;put(&mut image,volume,b"MICROSOFT*XBOX*MEDIA");put_u32(&mut image,volume+0x14,root_sector);put_u32(&mut image,volume+0x18,64);
        let root=partition+root_sector as u64*2048;put_u32(&mut image,root+4,file_sector);put_u32(&mut image,root+8,file_size);put(&mut image,root+12,&[0,entry_name.len() as u8]);put(&mut image,root+14,entry_name);put(&mut image,root+63,&[0]);image.len=image.len.max(partition+file_sector as u64*2048+file_size as u64);image
    }
    #[test] fn reads_supported_partition_layouts(){for partition in [0,0x02080000,0x0fd90000,0x18300000]{let mut image=image(partition,80,b"default.xex",100,64);let index=inspect_iso(&mut image).unwrap();assert_eq!(index.partition_offset,partition);assert!(index.files.iter().any(|entry|entry.path=="default.xex"));}}
    #[test] fn rejects_malformed_directory_tree(){let mut image=image(0,80,b"default.xex",100,64);put_u16(&mut image,80*2048,1);assert!(inspect_iso(&mut image).is_err());}
    #[test] fn rejects_directory_cycle(){let mut image=image(0,80,b"default.xex",100,64);let root=80*2048;put_u16(&mut image,root,8);put_u16(&mut image,root+32,8);put(&mut image,root+44,&[0,1]);put(&mut image,root+46,b"x");put(&mut image,root+47,&[0]);image.len=image.len.max(root+64);let error=inspect_iso(&mut image).unwrap_err();assert!(error.contains("cycle"),"{error}");}
    #[test] fn rejects_extent_overflow_and_traversal(){let mut image=image(0,80,b"../default.xex",u32::MAX,64);assert!(inspect_iso(&mut image).is_err());}
    #[test] fn rejects_unsupported_disc(){let mut image=Sparse::default();image.len=2*1024*1024;assert!(inspect_iso(&mut image).is_err());}
    #[test] fn cancellation_stops_extraction(){let mut image=image(0,80,b"default.xex",100,4096);let index=inspect_iso(&mut image).unwrap();let result=extract_files(&mut image,&index,std::path::Path::new("unused"),&||true,&|_,_,_|{});assert!(result.is_err());}
    #[test] fn extracts_bounded_file_bytes(){let mut image=image(0,80,b"default.xex",100,3);put(&mut image,100*2048,b"abc");let index=inspect_iso(&mut image).unwrap();let root=std::env::temp_dir().join(format!("skate-xdvdfs-{}",std::process::id()));let _=std::fs::remove_dir_all(&root);extract_files(&mut image,&index,&root,&||false,&|_,_,_|{}).unwrap();assert_eq!(std::fs::read(root.join("default.xex")).unwrap(),b"abc");std::fs::remove_dir_all(root).unwrap();}
}
//! Bounded XDVDFS reader for the fixed XISO/XGD1/XGD2/XGD3 volume locations.
//! Field offsets and partition bases follow the public XboxDev extract-xiso format constants.
