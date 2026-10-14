function Add-StagedParkPacks([string]$Destination,[string]$Packs) {
    # FNV binds package identity in the engine; SHA256 still validates transfer
    # integrity and the exported receipt. Fingerprints are not security hashes.
    if (!('Skate3PortAssets.ParkFingerprint' -as [type])) {
        Add-Type -TypeDefinition @'
namespace Skate3PortAssets {
 public static class ParkFingerprint {
  public static ulong Read(string path) {
   ulong h=14695981039346656037UL;
   using(var f=System.IO.File.OpenRead(path)) {
    byte[] b=new byte[65536]; int n;
    while((n=f.Read(b,0,b.Length))!=0) for(int i=0;i<n;i++) h=unchecked((h^b[i])*1099511628211UL);
   }
   return h;
  }
 }
}
'@
    }
    $records=[System.Collections.Generic.List[object]]::new()
    $maps=@{}
    $receipts=@(Get-ChildItem -LiteralPath $Packs -Filter '*.receipt.json' -File)
    if (!$receipts.Count) {throw 'No completed park package receipts.'}
    foreach ($file in $receipts) {
        $receipt=Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
        $name=$receipt.district
        if ($name -notmatch '^(MegaPark|IndustrialSkatePark|StartPark|DownTownSkatePark)$' -or $maps.ContainsKey($name)) {throw 'Invalid/duplicate park receipt.'}
        $base=Join-Path $Destination "maps/$name.skate"
        $package=Join-Path $Packs "$name.skate"
        if (!(Test-Path -LiteralPath $base -PathType Leaf) -or !(Test-Path -LiteralPath $package -PathType Leaf)) {throw 'Park package or base map missing.'}
        if ((Get-FileHash -LiteralPath $package).Hash -ne $receipt.package_sha256) {throw 'Park package does not match its receipt.'}
        foreach ($source in @((Get-Item -LiteralPath $package),$file)) {
            $relative="assets/private/park-layouts/$($source.Name)"
            $target=Join-Path $Destination $relative
            New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
            Copy-Item -LiteralPath $source.FullName -Destination $target
            $records.Add(@{path=$relative;size=$source.Length;sha256=(Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant()})
        }
        $maps[$name]=@{file="$name.skate";base_fingerprint=[Skate3PortAssets.ParkFingerprint]::Read((Resolve-Path -LiteralPath $base).Path);
            supplement_fingerprint=[Skate3PortAssets.ParkFingerprint]::Read((Resolve-Path -LiteralPath $package).Path)}
    }
    $relative='assets/private/park-layouts/catalog.json'
    $target=Join-Path $Destination $relative
    $json=@{version=1;maps=$maps} | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($target,$json,[System.Text.UTF8Encoding]::new($false))
    $records.Add(@{path=$relative;size=(Get-Item -LiteralPath $target).Length;sha256=(Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant()})
    return $records.ToArray()
}
