[CmdletBinding()]
param([switch]$Deploy)
$ErrorActionPreference='Stop'
if(-not $Deploy){throw 'Explicit -Deploy required. This script is for the owner-authorized R032-V test deployment only.'}
$root=(Resolve-Path "$PSScriptRoot\..").Path
$evidence=Join-Path $root 'evidence/stage-13/cohort-v'
$save=(Resolve-Path 'C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG').Path
$mods=Join-Path $save 'mods'
$candidate=Join-Path $evidence 'artifacts/HytaleRPG-0.0.25.jar'
$expected='1103268939C69FA6B3D9E58DB2AE10F66A2412776AC00A43A9C13C64D24889FF'
$prior='BFF7421FA765834E32EEE819072AA9FF665C085FFCB0ECCFF9CBFF38BC3B965E'
$dependencies=@{'CanvasUI-0.1.0.jar'='218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6';'HYTALEDEVLIB-0.5.0.jar'='DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230'}
function Assert-Stopped {
    if(Get-CimInstance Win32_Process|Where-Object {$_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')}){
        throw 'Close Hytale and its server before deployment. No process will be stopped automatically.'
    }
}
function Hash([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
function Snapshot {
    @(Get-ChildItem -LiteralPath $save -Recurse -File|ForEach-Object {
        [pscustomobject]@{relative=[IO.Path]::GetRelativePath($save,$_.FullName);hash=(Hash $_.FullName)}
    })
}
Assert-Stopped
if((Hash $candidate) -ne $expected){throw 'Archived V JAR hash mismatch'}
$receipt=Get-Content -LiteralPath (Join-Path $evidence 'support-tethers.json') -Raw|ConvertFrom-Json
$smoke=Get-Content -LiteralPath (Join-Path $evidence 'server-smoke-summary.json') -Raw|ConvertFrom-Json
$diff=Get-Content -LiteralPath (Join-Path $evidence 'jar-differential.json') -Raw|ConvertFrom-Json
if($receipt.tests -ne 2193 -or $receipt.failures -or $receipt.errors -or $receipt.skipped -or
    $receipt.jarHashes.'HytaleRPG-0.0.25.jar' -ne $expected -or $smoke.jarSha256 -ne $expected -or
    $smoke.processExitCode -ne 0 -or -not $smoke.supportTetherAssetsResolved -or $smoke.failure -or
    $diff.candidateSha256 -ne $expected -or $diff.isolatedAtomicRollbackAndRollForward -ne 'PASS'){throw 'V validation receipt mismatch'}
if((Hash (Join-Path $evidence 'Hytale-RPG-Stage13-V-support-tethers.zip')) -ne 'FA96B4640349C5313517707C77941D49293DC94DEF0B30C369F4755F4D11C0EE'){throw 'Three-mod archive hash mismatch'}
if(@(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar').Count -ne 3){throw 'Unexpected live mod inventory'}
foreach($name in $dependencies.Keys){if((Hash (Join-Path $mods $name)) -ne $dependencies[$name]){throw "Changed supporting mod: $name"}}
$target=Join-Path $mods 'HytaleRPG-0.0.25.jar'
if((Hash $target) -ne $prior){throw 'Installed RPG no longer matches U; inspect before replacing'}
$pending=Join-Path $mods 'HytaleRPG-0.0.25.jar.pending'
if(Test-Path -LiteralPath $pending){throw 'A pending deployment already exists; inspect it'}
$backup=Join-Path $evidence ('before/save/'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
if(Test-Path -LiteralPath $backup){throw 'Backup path already exists'}
New-Item -ItemType Directory -Path $backup -Force|Out-Null
$before=Snapshot
foreach($file in $before){
    $destination=Join-Path $backup $file.relative
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($destination))|Out-Null
    Copy-Item -LiteralPath (Join-Path $save $file.relative) -Destination $destination
    if((Hash $destination) -ne $file.hash){throw "Backup integrity mismatch: $($file.relative)"}
}
Assert-Stopped
if(Compare-Object $before (Snapshot) -Property relative,hash){throw 'Save changed during backup; nothing deployed'}
Copy-Item -LiteralPath $candidate -Destination $pending
if((Hash $pending) -ne $expected){throw 'Staged JAR hash mismatch'}
[IO.File]::Replace($pending,$target,[NullString]::Value)
if((Hash $target) -ne $expected){throw 'Installed JAR hash mismatch'}
$unchanged=@($before|Where-Object relative -ne 'mods\HytaleRPG-0.0.25.jar')
$after=@(Snapshot|Where-Object relative -ne 'mods\HytaleRPG-0.0.25.jar')
if(Compare-Object $unchanged $after -Property relative,hash){throw 'Non-target save files changed during deployment; inspect backup before testing'}
$result=[ordered]@{deployedAtUtc=[DateTime]::UtcNow.ToString('o');ownerAuthorization='Deploy it so I can test.';
    cohort='R032-V';target=$target;jarSha256=$expected;priorJarSha256=$prior;backupRoot=$backup;
    completeSaveBackupFiles=$before.Count;nonTargetFilesVerifiedUnchanged=$unchanged.Count;exactlyThreeMods=$true;
    supportingModsReplaced=$false;saveDataModified=$false;connectedClientVerified=$false;
    performanceGate='UNMET; unchanged 4ms p95 / 8ms p99 targets';hudBadge='Unchanged R032-U; identify V by JAR hash and startup audit'}
$result|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $evidence 'authorized-deployment.json') -Encoding utf8
$result|ConvertTo-Json -Depth 5
