param([switch]$Check, [ValidateSet('dev','release')][string]$Profile = 'release')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$sdkRoot = $env:ANDROID_HOME
if (!$sdkRoot) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$ndkRoot = $env:ANDROID_NDK_HOME
if (!$ndkRoot) {
    $ndkRoot = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'ndk') -Directory |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1 -ExpandProperty FullName
}
$toolsRoot = Join-Path $ndkRoot 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$clang = Join-Path $toolsRoot 'aarch64-linux-android26-clang.cmd'
if (!(Test-Path -LiteralPath $clang)) { throw 'Official NDK ARM64 compiler not found.' }
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $clang
$env:CC_aarch64_linux_android = $clang
$env:CXX_aarch64_linux_android = Join-Path $toolsRoot 'aarch64-linux-android26-clang++.cmd'
$env:AR_aarch64_linux_android = Join-Path $toolsRoot 'llvm-ar.exe'
$pageFlag = '-C link-arg=-Wl,-z,max-page-size=16384'
if ($env:CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS -notlike '*max-page-size=16384*') {
    $env:CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS = "$($env:CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS) $pageFlag".Trim()
}
Push-Location $repoRoot
try {
    $operation = if ($Check) { 'check' } else { 'build' }
    & cargo $operation --locked -p skate3rust_android --target aarch64-linux-android --profile $Profile
    if ($LASTEXITCODE -ne 0) { throw "Android Rust $operation failed." }
    if (!$Check) {
        $metadata = & cargo metadata --locked --no-deps --format-version 1
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read Cargo target directory.' }
        $targetRoot = ($metadata | ConvertFrom-Json).target_directory
        $profileDir = if ($Profile -eq 'dev') { 'debug' } else { 'release' }
        $jniDir = Join-Path $PSScriptRoot 'app/src/main/jniLibs/arm64-v8a'
        New-Item -ItemType Directory -Force -Path $jniDir | Out-Null
        $library = Join-Path $jniDir 'libskate3rust_android.so'
        Copy-Item -LiteralPath (Join-Path $targetRoot "aarch64-linux-android/$profileDir/libskate3rust_android.so") -Destination $library
        $elfInfo = & (Join-Path $toolsRoot 'llvm-readelf.exe') -d $library
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect shared library dependencies.' }
        if ($elfInfo -match '\[libc\+\+_shared\.so\]') {
            Copy-Item -LiteralPath (Join-Path (Split-Path $toolsRoot -Parent) 'sysroot/usr/lib/aarch64-linux-android/libc++_shared.so') -Destination $jniDir
        } elseif (Test-Path -LiteralPath (Join-Path $jniDir 'libc++_shared.so')) {
            Remove-Item -LiteralPath (Join-Path $jniDir 'libc++_shared.so')
        }
        & (Join-Path $toolsRoot 'llvm-strip.exe') --strip-debug $library
        if ($LASTEXITCODE -ne 0) { throw 'Cannot strip packaged debug symbols.' }
    }
} finally { Pop-Location }
