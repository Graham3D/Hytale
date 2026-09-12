[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $repo 'evidence/stage-13/cohort-aj-live'
$save=(Resolve-Path (Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG')).Path
$mods=Join-Path $save 'mods'
$old=Join-Path $mods 'HyARPG.jar'
$target=Join-Path $mods 'HyARPG.jar'
$candidate=Join-Path $out 'artifacts/HyARPG.jar'
$package=Get-Content (Join-Path $out 'package-validation.json') -Raw|ConvertFrom-Json
$expected=$package.jarHashes.'HyARPG.jar'
if($package.jarHashes.'HyARPG.jar' -ne $expected -or (Get-FileHash $candidate).Hash -ne $expected){throw 'Wrong AJ-LIVE artifact'}
if((Get-FileHash $old).Hash -ne '272E9F8365FE877152B5890D9100E123AE1127D8DC881A88D93993D3C51FE692'){throw 'Live RPG build changed: review before replacing'}
function AssertStopped {
    if(@(Get-CimInstance Win32_Process|Where-Object {$_.Name -like '*Hytale*' -or ($_.Name -eq 'java.exe' -and $_.CommandLine -match 'HytaleServer')}).Count){throw 'Hytale must be stopped; no process is interrupted by this script'}
}
AssertStopped
$stamp=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$backup=Join-Path $out "before/save/$stamp/RPG"
if(Test-Path $backup){throw 'Backup already exists'}
New-Item -ItemType Directory -Force (Split-Path $backup)|Out-Null
Copy-Item -LiteralPath $save -Destination $backup -Recurse
$inventory=@();$bytes=0
foreach($file in Get-ChildItem -LiteralPath $save -Recurse -File){
    $relative=[IO.Path]::GetRelativePath($save,$file.FullName)
    $hash=(Get-FileHash -LiteralPath $file.FullName).Hash
    $copy=Join-Path $backup $relative
    if(-not(Test-Path -LiteralPath $copy) -or (Get-FileHash -LiteralPath $copy).Hash -ne $hash){throw "Backup mismatch: $relative"}
    $inventory+=@{path=$relative;sha256=$hash;bytes=$file.Length};$bytes+=$file.Length
}
if(@(Get-ChildItem $backup -Recurse -File).Count -ne $inventory.Count){throw 'Backup file count mismatch'}
$inventory|ConvertTo-Json -Depth 4|Set-Content (Join-Path (Split-Path $backup) 'inventory.json') -Encoding utf8
AssertStopped
$staging=Join-Path $mods 'HyARPG.jar.aj-live-staging'
if(Test-Path $staging){throw 'Staging file already exists'}
Copy-Item -LiteralPath $candidate -Destination $staging
if((Get-FileHash $staging).Hash -ne $expected){throw 'Staged JAR hash mismatch'}
$retired=Join-Path (Split-Path $backup) 'retired-live-name'
New-Item -ItemType Directory $retired|Out-Null
# Exact nonrecursive moves, with the complete verified stopped-save backup already retained.
Move-Item -LiteralPath $old -Destination (Join-Path $retired 'HyARPG.jar')
try{Move-Item -LiteralPath $staging -Destination $target}
catch{Move-Item -LiteralPath (Join-Path $retired 'HyARPG.jar') -Destination $old;throw}
if((Get-FileHash $target).Hash -ne $expected){throw 'Installed JAR hash mismatch'}
foreach($file in $inventory){
    if($file.path -eq 'mods\HyARPG.jar'){continue}
    if((Get-FileHash -LiteralPath (Join-Path $save $file.path)).Hash -ne $file.sha256){throw "Non-target save changed: $($file.path)"}
}
if(@(Get-ChildItem $save -Recurse -File).Count -ne $inventory.Count){throw 'Unexpected live file count change'}
$rpgJars=@(Get-ChildItem $mods -File|Where-Object {$_.Name -eq 'HyARPG.jar' -or $_.Name -like 'HytaleRPG-*.jar'})
if($rpgJars.Count -ne 1 -or $rpgJars[0].Name -ne 'HyARPG.jar'){throw 'Duplicate RPG installation'}
[ordered]@{deployedAtUtc=[DateTime]::UtcNow.ToString('o');target=$target;jarSha256=$expected;
    backup=$backup;backupFiles=$inventory.Count;backupBytes=$bytes;allBackupFileHashesVerified=$true;
    allNonTargetLiveFilesUnchanged=$true;oldJarMovedToRollback=$true;internalIdentityPreserved=$true;
    liveModJars=@(Get-ChildItem $mods -Filter '*.jar'|ForEach-Object Name);
    implemented=$true;packaged=$true;deployed=$true;connectedVerified=$false;liveStartupVerified=$false
}|ConvertTo-Json -Depth 5|Set-Content (Join-Path $out 'deployment-validation.json') -Encoding utf8
Get-Content (Join-Path $out 'deployment-validation.json')
