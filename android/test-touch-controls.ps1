$ErrorActionPreference='Stop'
$output=Join-Path $PSScriptRoot 'build/touch-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& javac -d $output (Join-Path $PSScriptRoot 'app/src/main/java/com/skate3rust/android/TouchState.java') (Join-Path $PSScriptRoot 'app/src/main/java/com/skate3rust/android/TouchLayout.java') (Join-Path $PSScriptRoot 'tests/TouchStateTest.java') (Join-Path $PSScriptRoot 'tests/TouchLayoutTest.java')
if($LASTEXITCODE){throw 'Touch tests did not compile.'}
& java -cp $output com.skate3rust.android.TouchStateTest
if($LASTEXITCODE){throw 'Touch tests failed.'}
& java -cp $output com.skate3rust.android.TouchLayoutTest
if($LASTEXITCODE){throw 'Touch layout tests failed.'}
& rustc --edition=2024 --test (Join-Path $PSScriptRoot 'tests/startup_state_tests.rs') -o (Join-Path $output 'startup-state-tests.exe')
if($LASTEXITCODE){throw 'Startup state tests did not compile.'}
& (Join-Path $output 'startup-state-tests.exe')
if($LASTEXITCODE){throw 'Startup state tests failed.'}
