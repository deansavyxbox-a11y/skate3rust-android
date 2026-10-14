use jni::{JNIEnv, objects::{JClass, JObject, JString, JValue}, sys::jstring};
use std::{cell::RefCell, fs::File, path::Path};

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_skate3rust_android_NativeAssetValidator_nativeValidate(
    mut env: JNIEnv<'_>, _class: JClass<'_>, root: JString<'_>,
) -> jstring {
    let result = env.get_string(&root).map(|value| value.to_string_lossy().into_owned())
        .map_err(|e| format!("Invalid installation path: {e}"))
        .and_then(|path| skate_import::validate_installation(Path::new(&path)));
    match result {
        Ok(report) => env.new_string(format!("{{\"ok\":true,\"maps\":{},\"files\":{}}}", serde_json::to_string(&report.maps).unwrap_or_else(|_| "[]".into()), report.files)).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(message) => { let _ = env.throw_new("java/lang/IllegalArgumentException", message); std::ptr::null_mut() }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_skate3rust_android_NativeAssetValidator_nativeExtractIso(
    mut env: JNIEnv<'_>, _class: JClass<'_>, source: JString<'_>, destination: JString<'_>, observer: JObject<'_>,
) -> jstring {
    let source_path = match env.get_string(&source) { Ok(value)=>value.to_string_lossy().into_owned(),Err(e)=>return throw(&mut env,format!("Invalid ISO path: {e}")) };
    let destination_path = match env.get_string(&destination) { Ok(value)=>value.to_string_lossy().into_owned(),Err(e)=>return throw(&mut env,format!("Invalid extraction path: {e}")) };
    let callbacks=RefCell::new(env);
    let result=(|| {
        let mut image=File::open(&source_path).map_err(|e|e.to_string())?;
        let index=skate_import::xdvdfs::inspect_iso(&mut image)?;
        let cancel=|| -> bool {
            match callbacks.borrow_mut().call_method(&observer,"isCancelled","()Z",&[]) { Ok(value)=>value.z().unwrap_or(true),Err(_)=>true }
        };
        let progress=|completed:u64,total:u64,item:&str| {
            let mut env=callbacks.borrow_mut();
            if let Ok(name)=env.new_string(item) {let _=env.call_method(&observer,"onProgress","(JJLjava/lang/String;)V",&[JValue::Long(completed as i64),JValue::Long(total as i64),JValue::Object(&name)]);}
        };
        skate_import::xdvdfs::extract_files(&mut image,&index,Path::new(&destination_path),&cancel,&progress)?;
        serde_json::to_string(&index).map_err(|e|e.to_string())
    })();
    let mut env=callbacks.into_inner();
    match result {Ok(json)=>env.new_string(json).map(|s|s.into_raw()).unwrap_or(std::ptr::null_mut()),Err(message)=>throw(&mut env,message)}
}

fn throw(env:&mut JNIEnv<'_>,message:String)->jstring {let _=env.throw_new("java/io/IOException",message);std::ptr::null_mut()}
