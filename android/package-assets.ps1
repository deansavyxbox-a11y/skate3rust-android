param([Parameter(Mandatory=$true)][string]$Staging,[Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/prepare-assets.ps1" -FunctionsOnly
$root=(Resolve-Path -LiteralPath $Staging).Path
$target=[System.IO.Path]::GetFullPath($Output)
if(Test-Path -LiteralPath $target){throw 'Output must be a new ZIP path.'}
if($target.StartsWith($root+[System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)){throw 'ZIP output must be outside the staging directory.'}
Test-StagedAssets $root
$records=@(Get-Content -LiteralPath (Join-Path $root 'transfer-manifest.json') -Raw | ConvertFrom-Json)
$metadata=@('complete.json','transfer-manifest.json')
if(Test-Path -LiteralPath (Join-Path $root 'sha256sum.checks')){$metadata+='sha256sum.checks'}
$parent=Split-Path -Parent $target
if(!(Test-Path -LiteralPath $parent)){New-Item -ItemType Directory -Path $parent | Out-Null}
function Assert-RegularPath([string]$Relative) {
    $cursor=$root
    foreach($part in $Relative.Replace('/','\').Split('\')) {
        $cursor=Join-Path $cursor $part
        $item=Get-Item -LiteralPath $cursor -Force
        if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw "Links are not permitted in an asset package: $Relative"}
    }
}
$created=$false
try {
    $archiveStream=[IO.File]::Open($target,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$created=$true
    try {
        $archiveObject=[IO.Compression.ZipArchive]::new($archiveStream,[IO.Compression.ZipArchiveMode]::Create,$false,[Text.Encoding]::UTF8)
        try {
            foreach($name in $metadata) {
                Assert-RegularPath $name
                $entry=$archiveObject.CreateEntry($name,[IO.Compression.CompressionLevel]::Optimal)
                $sourceStream=[IO.File]::OpenRead((Join-Path $root $name))
                try{$entryStream=$entry.Open();try{$sourceStream.CopyTo($entryStream)}finally{$entryStream.Dispose()}}finally{$sourceStream.Dispose()}
            }
            foreach($record in $records) {
                Assert-RegularPath $record.path
                $entry=$archiveObject.CreateEntry($record.path,[IO.Compression.CompressionLevel]::Optimal)
                $sourceStream=[IO.File]::OpenRead((Join-Path $root $record.path))
                try{$entryStream=$entry.Open();try{$sourceStream.CopyTo($entryStream)}finally{$entryStream.Dispose()}}finally{$sourceStream.Dispose()}
            }
        } finally {$archiveObject.Dispose()}
    } finally {$archiveStream.Dispose()}
    Write-Output "Created prepared asset ZIP: $target"
} catch {
    if($created -and (Test-Path -LiteralPath $target)){Remove-Item -LiteralPath $target -Force}
    throw
}