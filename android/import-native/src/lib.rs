use jni::{JNIEnv, objects::{JClass, JString}, sys::jstring};
use std::path::Path;

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
