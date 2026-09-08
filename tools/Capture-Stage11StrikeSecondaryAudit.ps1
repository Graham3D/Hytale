[CmdletBinding()]
param([ValidateSet('n','o')][string]$Cohort='n')
$ErrorActionPreference='Stop'
$secondaryRoot=(Resolve-Path "$PSScriptRoot\..").Path
$secondaryOut=Join-Path $secondaryRoot "evidence\stage-11\cohort-$Cohort\api"
$secondaryJar=Join-Path $secondaryRoot 'build\libs\HytaleRPG-0.0.23.jar'
$secondaryArchive=Join-Path $secondaryRoot "evidence\stage-11\cohort-$Cohort\artifacts\HytaleRPG-0.0.23.jar"
if((Test-Path -LiteralPath $secondaryArchive) -and (Get-FileHash -LiteralPath $secondaryArchive).Hash -ne (Get-FileHash -LiteralPath $secondaryJar).Hash){throw 'Do not overwrite another build audit'}
$secondaryServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$secondaryJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $secondaryOut | Out-Null
foreach($secondaryClass in @('com.hypixel.hytale.server.npc.role.support.WorldSupport',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems',
    'com.hypixel.hytale.server.core.modules.entity.component.BoundingBox',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleAreaQueries',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter',
    'com.inigmasgames.hytalerpg.execution.strike.StrikeSecondaryRuntime')){
    $secondaryText=(& $secondaryJavap -classpath "$secondaryJar;$secondaryServer" -c -p $secondaryClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Strike secondary API audit failed: $secondaryClass"}
    $secondaryText | Set-Content -LiteralPath (Join-Path $secondaryOut ($secondaryClass+'.txt')) -Encoding utf8
    if($secondaryClass.EndsWith('$Port') -and ($secondaryText -notmatch 'StrikeSecondaryRuntime.afterPrimary' -or $secondaryText -notmatch 'HytaleDamageAdapter.applyResolved')){throw 'Missing native secondary dispatch boundary'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $secondaryServer).Hash;jarSha256=(Get-FileHash -LiteralPath $secondaryJar).Hash;
    scope=$(if($Cohort -eq 'n'){'Cleave: bounded native hostile/LOS query -> inherited offensive calculation. Phantom: resolved original premitigation *.60 -> same DamageSystems path without another offensive calculation.'}else{'Shockwave: one root burst, collision-bounds cylinder/hostile/LOS query -> resolved original premitigation *.40 with component-scoped radius/Concentration -> same DamageSystems path without another offensive roll.'});
    evidence='Installed bytecode and packaged call-site structure only; no connected input, damage, LOS or presentation proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $secondaryOut 'audit.json') -Encoding utf8
