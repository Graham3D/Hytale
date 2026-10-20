[CmdletBinding()]
param([ValidateSet('q','r')][string]$Cohort='q')
$ErrorActionPreference='Stop'
$areaRoot=(Resolve-Path "$PSScriptRoot\..").Path
$areaOut=Join-Path $areaRoot "evidence\stage-11\cohort-$Cohort\api"
$areaJar=Join-Path $areaRoot 'build\libs\HytaleRPG-0.0.23.jar'
$areaArchive=Join-Path $areaRoot "evidence\stage-11\cohort-$Cohort\artifacts\HytaleRPG-0.0.23.jar"
if((Test-Path -LiteralPath $areaArchive) -and (Get-FileHash -LiteralPath $areaArchive).Hash -ne (Get-FileHash -LiteralPath $areaJar).Hash){throw 'Do not overwrite another cohort build audit'}
$areaServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$areaJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $areaOut | Out-Null
foreach($areaClass in @('com.hypixel.hytale.server.core.modules.collision.CollisionModule',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleAreaQueries',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.execution.area.AreaRuntime',
    'com.inigmasgames.hytalerpg.execution.connection.ConnectionRuntime',
    'com.inigmasgames.hytalerpg.execution.SkillExecutionContext',
    'com.inigmasgames.hytalerpg.execution.RootEffectBudget')){
    $areaText=(& $areaJavap -classpath "$areaJar;$areaServer" -c -p $areaClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Area controller audit failed: $areaClass"}
    $areaText | Set-Content -LiteralPath (Join-Path $areaOut ($areaClass+'.txt')) -Encoding utf8
    if($areaClass.EndsWith('AreaRuntime') -and ($areaText -notmatch 'cascadeCopy' -or $areaText -notmatch 'RootEffectBudget.claim' -or $areaText -notmatch 'AreaWorldPort.prepareImpact')){throw 'Missing guarded derived-area admission'}
    if($Cohort -eq 'r' -and ($areaClass.EndsWith('AreaRuntime') -or $areaClass.EndsWith('ConnectionRuntime')) -and $areaText -notmatch 'aftermathCopy'){throw 'Missing native-family natural-expiry child boundary'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $areaServer).Hash;jarSha256=(Get-FileHash -LiteralPath $areaJar).Hash;
    scope='Derived area -> legal native terrain/collision path -> existing finite field registry -> shared root budgets/status limits -> existing native damage adapter';
    evidence='Bytecode/call-site structure only, not connected damage, NPC state, terrain or presentation proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $areaOut 'audit.json') -Encoding utf8
