$ErrorActionPreference='Stop'
$root=Join-Path ([IO.Path]::GetTempPath()) ('skate-zip-package-test-'+[guid]::NewGuid().ToString('N'))
$staging=Join-Path $root 'staging';$output=Join-Path $root 'package.zip'
New-Item -ItemType Directory -Path (Join-Path $staging 'assets/private') -Force | Out-Null
[byte[]]$payload=1,2,3,4,5,6,7
[IO.File]::WriteAllBytes((Join-Path $staging 'assets/private/fixture.bin'),$payload)
$sha=(Get-FileHash (Join-Path $staging 'assets/private/fixture.bin') -Algorithm SHA256).Hash.ToLowerInvariant()
@(@{path='assets/private/fixture.bin';size=$payload.Length;sha256=$sha}) | ConvertTo-Json | Set-Content (Join-Path $staging 'transfer-manifest.json') -Encoding utf8
@{version=1;files=1;bytes=$payload.Length;maps=@('University')} | ConvertTo-Json | Set-Content (Join-Path $staging 'complete.json') -Encoding utf8
try {
    & "$PSScriptRoot/package-assets.ps1" -Staging $staging -Output $output
    $zip=[IO.Compression.ZipFile]::OpenRead($output)
    try {
        $expected=@('complete.json','transfer-manifest.json','assets/private/fixture.bin')
        $actual=@($zip.Entries | ForEach-Object FullName)
        if(($actual -join '|') -ne ($expected -join '|')){throw "Unexpected ZIP entries: $($actual -join ', ')"}
        $entry=$zip.GetEntry('assets/private/fixture.bin');$memory=[IO.MemoryStream]::new()
        try{$entry.Open().CopyTo($memory);if([Convert]::ToBase64String($memory.ToArray()) -ne [Convert]::ToBase64String($payload)){throw 'Packaged fixture bytes differ.'}}finally{$memory.Dispose()}
    } finally {$zip.Dispose()}
    Write-Output 'PASS: validated staging packaged with root manifests and unchanged payload.'
} finally {if(Test-Path $root){Remove-Item -LiteralPath $root -Recurse -Force}}