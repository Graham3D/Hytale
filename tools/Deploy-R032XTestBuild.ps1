[CmdletBinding()]
param([switch]$Deploy)
$ErrorActionPreference='Stop'
if(-not $Deploy){throw 'Explicit -Deploy required'}
$root=(Resolve-Path "$PSScriptRoot/..").Path
$evidence=Join-Path $root 'evidence/stage-13/cohort-x'
$save=(Resolve-Path "$env:APPDATA/Hytale/data/pre-release/Saves/RPG").Path
$receipt=Get-Content (Join-Path $evidence 'package.json') -Raw|ConvertFrom-Json
$smoke=Get-Content (Join-Path $evidence 'server-smoke-summary.json') -Raw|ConvertFrom-Json
if($receipt.tests.tests -ne 2203 -or $receipt.tests.failures -or $receipt.tests.errors -or $receipt.tests.skipped -or
    $receipt.rollback -ne 'PASS' -or -not $receipt.allOtherEntriesByteIdenticalToW -or
    $smoke.jarSha256 -ne $receipt.jarSha256 -or $smoke.processExitCode -ne 0 -or $smoke.failure -or
    -not $smoke.supportTetherAssetsResolved -or -not $smoke.networkBooted){throw 'Candidate validation mismatch'}
function Hash([string]$path){(Get-FileHash -LiteralPath $path).Hash}
function Assert-Stopped {
    if(Get-CimInstance Win32_Process|Where-Object {$_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')}){throw 'Close Hytale and server; no process is stopped automatically'}
}
function Snapshot {@(Get-ChildItem -LiteralPath $save -Recurse -File|ForEach-Object {[pscustomobject]@{relative=[IO.Path]::GetRelativePath($save,$_.FullName);hash=(Hash $_.FullName)}})}
$jarRelative='mods\HytaleRPG-0.0.25.jar'
$roleRelative='mods\ImmersiveNPCs\profiles\jonalith\native-role\Jonalith.json'
$jar=Join-Path $save $jarRelative;$role=Join-Path $save $roleRelative
$candidate=Join-Path $evidence 'artifacts/HytaleRPG-0.0.25.jar'
Assert-Stopped
if((Hash $jar) -ne '585E0A32B9682B5BE06FF8202EF39D9D88302E8FF178457A894440402E560EA3' -or (Hash $candidate) -ne $receipt.jarSha256){throw 'RPG JAR changed since audit'}
if((Hash $role) -ne '060476A2AAFBE35E12B54B6E8B0B2D00829EF6012FEB7F2B297151FB08F852E9'){throw 'Jonalith role changed since audit; no role is modified by this deployment'}
$modHashes=@{'CanvasUI-0.1.0.jar'='218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6';'HYTALEDEVLIB-0.5.0.jar'='DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230';'ImmersiveNPCs-0.6.3-R170-CREATIVE-FULL-PROFILE-GENERATION.jar'='0E231487E8BAE985E32488A082E5B6E592B897F6DA7E74D82356EFBC22C15814'}
if(@(Get-ChildItem (Join-Path $save 'mods') -Filter '*.jar' -File).Count -ne 4){throw 'Unexpected mod inventory'}
foreach($name in $modHashes.Keys){if((Hash (Join-Path $save ('mods/'+$name))) -ne $modHashes[$name]){throw "Supporting mod changed: $name"}}
$backup=Join-Path $evidence ('before/save/'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
New-Item -ItemType Directory $backup|Out-Null
$before=Snapshot
foreach($file in $before){$destination=Join-Path $backup $file.relative;New-Item -ItemType Directory -Force (Split-Path $destination)|Out-Null;Copy-Item -LiteralPath (Join-Path $save $file.relative) -Destination $destination;if((Hash $destination) -ne $file.hash){throw 'Save backup failed'}}
Assert-Stopped
if(Compare-Object $before (Snapshot) -Property relative,hash){throw 'Save changed during backup'}
if(Test-Path ($jar+'.pending')){throw 'Pending deployment already exists'}
try{
    Copy-Item $candidate ($jar+'.pending')
    if((Hash ($jar+'.pending')) -ne $receipt.jarSha256){throw 'Pending hash mismatch'}
    [IO.File]::Replace(($jar+'.pending'),$jar,[NullString]::Value)
    if((Hash $jar) -ne $receipt.jarSha256){throw 'Installed hash mismatch'}
}catch{
    Copy-Item (Join-Path $backup $jarRelative) ($jar+'.pending') -Force;[IO.File]::Replace(($jar+'.pending'),$jar,[NullString]::Value)
    throw
}
$unchanged=@($before|Where-Object relative -ne $jarRelative)
$after=@(Snapshot|Where-Object relative -ne $jarRelative)
if(Compare-Object $unchanged $after -Property relative,hash){throw 'Non-target files changed; inspect verified backup'}
$result=[ordered]@{deployedAtUtc=[DateTime]::UtcNow.ToString('o');cohort='R032-X';jar=$jar;jarSha256=(Hash $jar);role=$role;roleSha256=(Hash $role);roleChange='NONE';backup=$backup;backupFiles=$before.Count;unchangedFiles=$unchanged.Count;modCount=4;npcModJarReplaced=$false;playerAndNpcStatsModified=$false;connectedClientVerified=$false}
$result|ConvertTo-Json -Depth 5|Set-Content (Join-Path $evidence 'deployment.json') -Encoding utf8
$result|ConvertTo-Json -Depth 5
