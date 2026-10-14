param([Parameter(Mandatory=$true)][string]$Staging, [Parameter(Mandatory=$true)][string]$Archive)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/prepare-assets.ps1" -FunctionsOnly
Test-StagedAssets $Staging
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$bridge = Join-Path $sdkRoot 'platform-tools/adb.exe'
function Invoke-Bridge([string[]]$Arguments) {
    & $bridge @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')" }
}
$bytes = (Get-Content -LiteralPath (Join-Path $Staging 'complete.json') -Raw | ConvertFrom-Json).bytes
$storage = & $bridge shell df -k /data
if ($LASTEXITCODE -ne 0) { throw 'Cannot check phone storage.' }
$available = [long](($storage | Select-Object -Last 1).Trim() -split '\s+')[3] * 1024
if ($available -lt ($bytes * 3 + 512MB)) { throw 'Insufficient storage for validated staging and rollback.' }
& tar -cf $Archive -C $Staging .
if ($LASTEXITCODE -ne 0) { throw 'Asset archive creation failed.' }
$archiveHash = (Get-FileHash -LiteralPath $Archive).Hash.ToLowerInvariant()
$token = [guid]::NewGuid().ToString('N')
$remoteArchive = "/data/local/tmp/skate-assets-$token.tar"
$pending = "files/installation-$token.pending"
Invoke-Bridge @('push',$Archive,$remoteArchive)
Invoke-Bridge @('shell','chmod','644',$remoteArchive)
$remoteHash = & $bridge shell sha256sum $remoteArchive
if ($LASTEXITCODE -ne 0 -or ($remoteHash -split '\s+')[0] -ne $archiveHash) { throw 'Transferred archive hash mismatch.' }
Invoke-Bridge @('shell','run-as','com.skate3rust.android','mkdir','-p',$pending)
Invoke-Bridge @('shell','run-as','com.skate3rust.android','tar','-xf',$remoteArchive,'-C',$pending)
Invoke-Bridge @('shell','run-as','com.skate3rust.android','sh','-c',"'cd $pending && sha256sum -c sha256sum.checks > transfer-check.log'")
Invoke-Bridge @('shell','am','force-stop','com.skate3rust.android')
$exists = & $bridge shell run-as com.skate3rust.android test -d files/installation
$hadInstallation = $LASTEXITCODE -eq 0
$backup = "files/installation-$token.backup"
if ($hadInstallation) {
    # Preserve live preferences after transfer validation, before publication.
    Invoke-Bridge @('shell','run-as','com.skate3rust.android','sh','-c',"'if [ -d files/installation/settings ]; then cp -a files/installation/settings/. $pending/settings/; fi'")
}
if ($hadInstallation) { Invoke-Bridge @('shell','run-as','com.skate3rust.android','mv','files/installation',$backup) }
try { Invoke-Bridge @('shell','run-as','com.skate3rust.android','mv',$pending,'files/installation') }
catch {
    if ($hadInstallation) { Invoke-Bridge @('shell','run-as','com.skate3rust.android','mv',$backup,'files/installation') }
    throw
}
Invoke-Bridge @('shell','rm',$remoteArchive)
Write-Output 'Map installation published after hash validation; existing preferences preserved.'
