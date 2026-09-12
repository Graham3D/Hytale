[CmdletBinding()]
param([switch]$Deploy)
$ErrorActionPreference='Stop'
if(!$Deploy){throw 'Explicit -Deploy required'}
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$evidence=Join-Path $repo 'evidence\stage-13\cohort-aa'
$save=(Resolve-Path "$env:APPDATA\Hytale\data\pre-release\Saves\RPG").Path
$candidate=Join-Path $evidence 'artifacts\HytaleRPG-0.0.25.jar'
$relative='mods\HytaleRPG-0.0.25.jar';$jar=Join-Path $save $relative
$expected='C39517031B70B77241EC0FDF676C6679F74DF09F8EE1945CB8FA784DC97E3588'
$old='0737EC5DA371A8BC9E0E910EE48A362158D20E3F3D463F0F0E946E0A818C7B84'
function Hash($path){(Get-FileHash -LiteralPath $path).Hash}
function Assert-Stopped {
    if(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')}){throw 'Close Hytale/server before deployment; no process is stopped automatically'}
}
function Snapshot {@(Get-ChildItem -LiteralPath $save -Recurse -File | ForEach-Object {[pscustomobject]@{relative=[IO.Path]::GetRelativePath($save,$_.FullName);hash=(Hash $_.FullName)}})}
Assert-Stopped
if((Hash $candidate) -ne $expected -or (Hash $jar) -ne $old){throw 'Candidate or live baseline changed'}
$smoke=Get-Content -Raw (Join-Path $evidence 'server-smoke-summary.json')|ConvertFrom-Json
if($smoke.jarSha256 -ne $expected -or !$smoke.cleanShutdown -or $smoke.failure -or $smoke.processExitCode -ne 0){throw 'Exact candidate native smoke required'}
$package=Get-Content -Raw (Join-Path $evidence 'package-validation.json')|ConvertFrom-Json
if(!$package.archiveBytesMatch -or $package.rollbackBinaryRoundTrip -ne 'PASS'){throw 'Packaging/rollback gate failed'}
# Owner explicitly requests this test deployment. Do not relabel its one known
# compression-ratio failure as passing or alter retained validation evidence.
if(@($package.suites|Where-Object {$_.errors -or $_.skipped}).Count -or
   ($package.suites|Measure-Object failures -Sum).Sum -ne 1){throw 'Unexpected validation state'}
$backup=Join-Path $evidence ('before\save\'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
New-Item -ItemType Directory -Path $backup | Out-Null
$before=Snapshot
foreach($file in $before){
    $destination=Join-Path $backup $file.relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
    Copy-Item -LiteralPath (Join-Path $save $file.relative) -Destination $destination
    if((Hash $destination) -ne $file.hash){throw 'Backup hash mismatch'}
}
Assert-Stopped
if(Compare-Object $before (Snapshot) -Property relative,hash){throw 'Save changed during backup'}
$pending=$jar+'.pending'
if(Test-Path -LiteralPath $pending){throw 'Pending deployment already exists'}
try{
    Copy-Item -LiteralPath $candidate -Destination $pending
    if((Hash $pending) -ne $expected){throw 'Pending candidate mismatch'}
    Assert-Stopped
    [IO.File]::Replace($pending,$jar,[NullString]::Value)
    if((Hash $jar) -ne $expected){throw 'Installed hash mismatch'}
}catch{
    Copy-Item -LiteralPath (Join-Path $backup $relative) -Destination $pending -Force
    [IO.File]::Replace($pending,$jar,[NullString]::Value)
    throw
}
$unchanged=@($before|Where-Object relative -ne $relative)
if(Compare-Object $unchanged @(Snapshot|Where-Object relative -ne $relative) -Property relative,hash){throw 'Non-target files changed; inspect backup'}
[ordered]@{deployedAtUtc=[DateTime]::UtcNow.ToString('o');cohort='R032-AA';implemented=$true;packaged=$true;deployed=$true;
    connectedVerified=$false;liveStartupVerified=$false;jar=$jar;jarSha256=(Hash $jar);artifactSha256=(Hash $candidate);
    backup=$backup;backupFiles=$before.Count;unchangedFiles=$unchanged.Count;previousJarSha256=$old;
    otherModsChanged=$false;savesAndModDataChanged=$false;traceFilesChanged=$false;
    fullRetainedPass=$false;knownIssue='One unchanged tiny UI trace compression-ratio test fails; explicit owner-authorized test deployment'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'deployment.json') -Encoding utf8
Get-Content -LiteralPath (Join-Path $evidence 'deployment.json')
