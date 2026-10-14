# Build-29 packages whose automatically selected start is on peripheral geometry.
# Correct app-owned copies only; unknown revisions retain their authored start.
function Repair-AndroidMapStart([byte[]]$Bytes,[string]$Name) {
 $rules=@{
 IndustrialSkatePark=@('C8E3D686BD3816CF93BD992BCE7B25E602CE51DA20EE6F57F3B066C5B5DAB8CF',@(-67.944664,1.0,-7.1576667))
 MegaPark=@('4CCC6FA9AC85BBA76D03A78A7F7C72E83806805D044BE9CD961603BE837FFB90',@(138.565,1.0,-79.75567))
 StartPark=@('42D4EE4DF5FBF78B93C89AD2C2613DBCDE3ACB7FFFD4844C4651769F6935C324',@(-61.156666,1.0,-12.666667))
 }
 if(!$rules.ContainsKey($Name)) {return}
 $rule=$rules[$Name]
 if([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)) -ne $rule[0]) {return}
 # SKATE header: magic[8], endian[4], name length[4], UTF-8 name, xyz[12].
 $offset=16+[BitConverter]::ToUInt32($Bytes,12)
 for($j=0;$j -lt 3;$j++){[BitConverter]::GetBytes([float]$rule[1][$j]).CopyTo($Bytes,$offset+4*$j)}
}
