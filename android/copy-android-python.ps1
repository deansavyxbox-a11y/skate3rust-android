param(
    [Parameter(Mandatory=$true)][string]$SourceRoot,
    [Parameter(Mandatory=$true)][string]$OutputRoot,
    [Parameter(Mandatory=$true)][string]$AssetsRoot
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$androidBuildPath = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Path $androidBuildPath -Force | Out-Null
$androidBuild = (Resolve-Path -LiteralPath $androidBuildPath).Path
$appBuild = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'app/build'))
foreach ($target in @($OutputRoot, $AssetsRoot)) {
    $full = [IO.Path]::GetFullPath($target)
    $withinBuild = $full.StartsWith($androidBuild + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        $full.StartsWith($appBuild + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
    if (-not $withinBuild) {
        throw "Refusing generated Python output outside android build directories: $full"
    }
    if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Recurse -Force }
    New-Item -ItemType Directory -Path $full -Force | Out-Null
}
$extensions = @('.py', '.json', '.txt', '.xml', '.csv', '.toml', '.yaml', '.yml')
$includeRoots = @(
    'tools/asset_pipeline',
    'tools/owned_game',
    'tools/vendor/utt',
    'tools/vendor/skate3_ui',
    'tools/vendor/skate3_anim',
    'tools/vendor/university/tools/vanilla_map_extraction/tools'
)
$files = [Collections.Generic.List[IO.FileInfo]]::new()
foreach ($relative in $includeRoots) {
    $base = Join-Path $source ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $base -PathType Container)) { throw "Required converter source is missing: $relative" }
    Get-ChildItem -LiteralPath $base -Recurse -File | Where-Object {
        $extensions -contains $_.Extension.ToLowerInvariant() -and
        -not $_.Name.StartsWith('test_') -and
        $_.FullName -notmatch '[\\/]__pycache__[\\/]' -and
        $_.FullName -notmatch '[\\/]blender[\\/]'
    } | ForEach-Object { $files.Add($_) }
}
$scriptFiles = @(
    'tools/android_entry.py',
    'tools/prepare_hud.py',
    'tools/prepare_runtime_huds.py',
    'tools/install_prepared_hud.py',
    'tools/extract_session_marker.py',
    'tools/extract_default_skater.py',
    'tools/default_skater_retail_manifest.json'
)
foreach ($relative in $scriptFiles) {
    $path = Join-Path $source ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
    if ($relative -ne 'tools/android_entry.py' -and -not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required converter script is missing: $relative" }
    if (Test-Path -LiteralPath $path -PathType Leaf) { $files.Add((Get-Item -LiteralPath $path)) }
}
$licenseFiles = @(
    'tools/vendor/utt/LICENSE',
    'tools/vendor/skate3_ui/LICENSE',
    'tools/vendor/university/LICENSE-PROJECT.md'
)
foreach ($relative in $licenseFiles) {
    $path = Join-Path $source ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
    if (Test-Path -LiteralPath $path -PathType Leaf) { $files.Add((Get-Item -LiteralPath $path)) }
}
$files = $files | Sort-Object FullName -Unique
foreach ($file in $files) {
    $toolsRoot = (Join-Path $source 'tools').TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $relative = $file.FullName.Substring($toolsRoot.Length + 1)
    if ($relative.StartsWith('..')) { throw "Converter source escaped tools/: $relative" }
    # Keep the tools package for absolute imports; only the small Java bridge is top-level.
    if ($relative -ne 'android_entry.py') { $relative = Join-Path 'tools' $relative }
    $target = Join-Path $OutputRoot $relative
    $assets = Join-Path $AssetsRoot $relative
    foreach ($destination in @($target, $assets)) {
        $parent = Split-Path -Parent $destination
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $destination
    }
}
foreach ($baseOutput in @($OutputRoot, $AssetsRoot)) {
    $packageRoot = Join-Path $baseOutput 'tools'
    New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $packageRoot '__init__.py') -Value '# Android converter tools package.' -Encoding utf8
}
Write-Output "Packaged $($files.Count) converter source and metadata files (no test modules or game data)."
