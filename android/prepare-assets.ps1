param([string]$Source, [string]$Destination, [switch]$FunctionsOnly, [switch]$ValidateOnly, [switch]$AllMaps, [string]$ParkLayouts)
$ErrorActionPreference = 'Stop'
function Test-StagedAssets([string]$Directory) {
    $root = [System.IO.Path]::GetFullPath($Directory)
    $records = @(Get-Content -LiteralPath (Join-Path $root 'transfer-manifest.json') -Raw | ConvertFrom-Json)
    if ($records.Count -eq 0) { throw 'Transfer manifest is empty.' }
    foreach ($record in $records) {
        if ($record.path -match '[\r\n\\:]' -or $record.path -match '(^/|(^|/)\.\.(/|$))') { throw 'Unsafe transfer path.' }
        $file = [System.IO.Path]::GetFullPath((Join-Path $root $record.path))
        if (!$file.StartsWith($root + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Transfer path escapes staging.' }
        if (!(Test-Path -LiteralPath $file -PathType Leaf) -or (Get-Item -LiteralPath $file).Length -ne $record.size) { throw "Missing/truncated asset: $($record.path)" }
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $record.sha256) { throw "Asset hash mismatch: $($record.path)" }
    }
}
. "$PSScriptRoot/map-starts.ps1"
. "$PSScriptRoot/prepare-park-packs.ps1"
if ($FunctionsOnly) { return }
if ($ValidateOnly) { Test-StagedAssets $Destination; return }
if (!$Source -or !$Destination) { throw 'Specify Source Windows package and Destination staging directory.' }
if (Test-Path -LiteralPath $Destination) { throw 'Use a fresh staging directory; existing data is preserved.' }
$package = (Resolve-Path -LiteralPath $Source).Path
$marker = Get-Content -LiteralPath (Join-Path $package 'data/installation.json') -Raw | ConvertFrom-Json
if ($marker.version -ne 1 -or $marker.directory -notmatch '^installations/[0-9a-f]{32}$') { throw 'Invalid source installation.' }
$installation = Join-Path $package "data/$($marker.directory)"
$mapFiles=@(Get-ChildItem -LiteralPath (Join-Path $installation 'maps') -Filter '*.skate' -File | Where-Object {$AllMaps -or $_.BaseName -eq 'University'})
if (!$mapFiles.Count) {throw 'No prepared maps found.'}
$mapNames=@($mapFiles.BaseName)
$expected = @{}
foreach ($group in $marker.outputs.PSObject.Properties) {
    foreach ($entry in $group.Value.PSObject.Properties) { $expected[$entry.Name] = $entry.Value }
}
$files = @(Get-ChildItem -LiteralPath (Join-Path $installation 'assets') -Recurse -File | Where-Object {
    $relative = [System.IO.Path]::GetRelativePath($installation, $_.FullName).Replace('\','/')
    $relative -notlike 'assets/private/customisation/*' -and
    ($relative -notmatch '^assets/private/(native-props|native-skies|native-backdrops)/' -or $AllMaps -or $_.Name -like 'University*')
})
foreach ($mapFile in $mapFiles) {
    $files += $mapFile
    $lighting=[System.IO.Path]::ChangeExtension($mapFile.FullName,'irradiance')
    if (Test-Path -LiteralPath $lighting -PathType Leaf) { $files += Get-Item -LiteralPath $lighting }
}
New-Item -ItemType Directory -Path $Destination | Out-Null
$records = [System.Collections.Generic.List[object]]::new()
foreach ($file in $files) {
    $relative = [System.IO.Path]::GetRelativePath($installation, $file.FullName).Replace('\','/')
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expected.ContainsKey($relative)) {
        if ($file.Length -ne $expected[$relative].size -or $hash -ne $expected[$relative].sha256) { throw "Source manifest mismatch: $relative" }
    }
    $target = Join-Path $Destination $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
    if ($AllMaps -and $file.Extension -eq '.skate') {
        $bytes=[System.IO.File]::ReadAllBytes($target)
        Repair-AndroidMapStart $bytes $file.BaseName
        [System.IO.File]::WriteAllBytes($target,$bytes)
        $hash=(Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant()
    }
    $records.Add(@{path=$relative; size=$file.Length; sha256=$hash})
}
# Resident customisation eagerly loads the entire wardrobe. Initial mobile
# gameplay uses the existing stock skater and the engine's unavailable-library fallback.
New-Item -ItemType Directory -Force -Path (Join-Path $Destination 'assets/private/customisation'),(Join-Path $Destination 'settings') | Out-Null
$generated = @{
    'assets/private/customisation/customiser-availability.json' = '{"version":1,"status":"unavailable","reason":"Initial Android build uses the stock skater"}'
    'settings/default-map.json' = '"maps/University.skate"'
    'settings/graphics.json' = '{"width":1280,"height":720,"scale":50,"fps":30,"hour":12,"day_speed":60}'
}
foreach ($entry in $generated.GetEnumerator()) {
    $target = Join-Path $Destination $entry.Key
    [System.IO.File]::WriteAllText($target, $entry.Value, [System.Text.UTF8Encoding]::new($false))
    $records.Add(@{path=$entry.Key; size=(Get-Item -LiteralPath $target).Length; sha256=(Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant()})
}
if ($ParkLayouts) {
    foreach ($record in (Add-StagedParkPacks $Destination $ParkLayouts)) {$records.Add($record)}
}
$records | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $Destination 'transfer-manifest.json') -Encoding utf8
$checks = $records | ForEach-Object { "$($_.sha256)  $($_.path)" }
[System.IO.File]::WriteAllText((Join-Path $Destination 'sha256sum.checks'), ($checks -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
@{version=1; map='University'; maps=$mapNames; revision='60efdef86600d8d8d4feb4b7c608fa0efd0643d7'; files=$records.Count; bytes=($records | Measure-Object size -Sum).Sum} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Destination 'complete.json') -Encoding utf8
Test-StagedAssets $Destination
Write-Output "Validated $($records.Count) files for $($mapNames.Count) maps."
