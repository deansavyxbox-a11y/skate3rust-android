$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/prepare-assets.ps1" -FunctionsOnly
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('skate-assets-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$file = Join-Path $testRoot 'map.bin'
[System.IO.File]::WriteAllBytes($file, [byte[]](1,2,3,4,5))
$record = @{path='map.bin'; size=5; sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()}
ConvertTo-Json -InputObject @($record) | Set-Content -LiteralPath (Join-Path $testRoot 'transfer-manifest.json')
Test-StagedAssets $testRoot
[System.IO.File]::WriteAllBytes($file, [byte[]](1,2))
$rejected = $false
try { Test-StagedAssets $testRoot } catch { $rejected = $true }
if (!$rejected) { throw 'Truncated asset was accepted.' }
Write-Output 'PASS: complete data accepted; truncated data rejected.'
Remove-Item -LiteralPath $file,(Join-Path $testRoot 'transfer-manifest.json')
Remove-Item -LiteralPath $testRoot
