[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$base=Join-Path $repo 'evidence\stage-13\cohort-z\artifacts\HytaleRPG-0.0.25.jar'
$built=Join-Path $repo 'build\libs\HytaleRPG-0.0.25.jar'
$directory=Join-Path $repo 'build\candidate-aa'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$candidate=Join-Path $directory 'HytaleRPG-0.0.25.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Read-Bytes($entry){$stream=$entry.Open();$memory=[IO.MemoryStream]::new();try{$stream.CopyTo($memory);return ,$memory.ToArray()}finally{$stream.Dispose();$memory.Dispose()}}
function Hash([byte[]]$bytes){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))}
Copy-Item -LiteralPath $base -Destination $candidate -Force
$source=[IO.Compression.ZipFile]::OpenRead($built)
$out=[IO.Compression.ZipFile]::Open($candidate,[IO.Compression.ZipArchiveMode]::Update)
$changed=[Collections.Generic.List[string]]::new()
try{
  foreach($entry in $source.Entries){
    $name=$entry.FullName
    $allowed=$name -match '^com/inigmasgames/hytalerpg/execution/hytale/(HytaleSkillExecutionSystem|HytaleAreaQueries|NativeBeamTransform|NativeHealingText|HealingTextAccumulator|NativeBlizzardVisuals|NativeProjectileSpawnAuditCommand)(\$[^/]*)?\.class$' -or
      $name -match '^com/inigmasgames/hytalerpg/execution/area/(AreaRuntime|AreaWorldPort)(\$[^/]*)?\.class$' -or
      $name -eq 'com/inigmasgames/hytalerpg/input/NativeSupportTetherAudit.class' -or
      $name -eq 'com/inigmasgames/hytalerpg/diagnostics/RpgTraceEventType.class' -or
      $name -in @('rpg/catalog/skills.json','rpg/runtime/stage-06-area-cohort-b.json','Server/Models/RPG/RPG_Blizzard_Shard.json') -or
      $name -match '^(Server/Particles/RPG/Blizzard/|Common/VFX/RPG/Blizzard/)'
    if(!$allowed -or $name.EndsWith('/')){continue}
    $bytes=Read-Bytes $entry;$old=$out.GetEntry($name)
    if($old -and (Hash (Read-Bytes $old)) -eq (Hash $bytes)){continue}
    if($old){$old.Delete()}
    $replacement=$out.CreateEntry($name);$stream=$replacement.Open();try{$stream.Write($bytes)}finally{$stream.Dispose()}
    $changed.Add($name)
  }
}finally{$source.Dispose();$out.Dispose()}
$old=[IO.Compression.ZipFile]::OpenRead($base);$out=[IO.Compression.ZipFile]::OpenRead($candidate)
try{
  foreach($entry in $old.Entries){
    if($changed.Contains($entry.FullName)){continue}
    $other=$out.GetEntry($entry.FullName)
    if(!$other -or (Hash (Read-Bytes $entry)) -ne (Hash (Read-Bytes $other))){throw "Unrelated cumulative entry changed: $($entry.FullName)"}
  }
}finally{$old.Dispose();$out.Dispose()}
[ordered]@{baseline=(Get-FileHash $base).Hash;jarSha256=(Get-FileHash $candidate).Hash;changedEntries=@($changed);otherZEntriesByteIdentical=$true;deployed=$false} |
  ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $directory 'differential.json') -Encoding utf8
Get-FileHash -LiteralPath $candidate
