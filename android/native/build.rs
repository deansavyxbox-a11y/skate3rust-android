fn main() {
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        // Oboe's static archive contains C++ ABI references, but its build
        // script does not link the runtime. Package the official NDK library.
        println!("cargo:rustc-link-lib=c++_shared");
    }
}
