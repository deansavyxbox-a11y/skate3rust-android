param([string]$Maps)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/map-starts.ps1"
foreach($name in @('IndustrialSkatePark','MegaPark','StartPark')) {
 $original=[IO.File]::ReadAllBytes((Join-Path $Maps "$name.skate")); $copy=[byte[]]$original.Clone()
 Repair-AndroidMapStart $copy $name
 $offset=16+[BitConverter]::ToUInt32($original,12)
 if([Convert]::ToHexString($original,$offset,12) -eq [Convert]::ToHexString($copy,$offset,12)) {throw 'Expected spawn correction.'}
 [Array]::Copy($original,$offset,$copy,$offset,12)
 if([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($copy)) -ne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($original))) {throw 'Non-spawn content changed.'}
 $unknown=[byte[]]$original.Clone();$unknown[$unknown.Length-1]=$unknown[$unknown.Length-1] -bxor 1; $before=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($unknown));Repair-AndroidMapStart $unknown $name
 if($before -ne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($unknown))) {throw 'Unknown package changed.'}
}
'PASS: only spawn bytes changed; unknown package untouched.'
