[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$positionRoot=(Resolve-Path "$PSScriptRoot\..").Path
$positionOut=Join-Path $positionRoot 'evidence\stage-11\cohort-p\api'
$positionJar=Join-Path $positionRoot 'build\libs\HytaleRPG-0.0.23.jar'
$positionArchive=Join-Path $positionRoot 'evidence\stage-11\cohort-p\artifacts\HytaleRPG-0.0.23.jar'
if((Test-Path -LiteralPath $positionArchive) -and (Get-FileHash -LiteralPath $positionArchive).Hash -ne (Get-FileHash -LiteralPath $positionJar).Hash){throw 'Do not overwrite another cohort build audit'}
$positionServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$positionJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $positionOut | Out-Null
foreach($positionClass in @('com.hypixel.hytale.server.npc.role.Role',
    'com.hypixel.hytale.server.core.modules.collision.CollisionModule',
    'com.hypixel.hytale.server.core.modules.entity.component.TransformComponent',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleAreaQueries',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.execution.area.AreaDisplacementPlanner',
    'com.inigmasgames.hytalerpg.execution.area.RootDisplacementLedger',
    'com.inigmasgames.hytalerpg.execution.connection.ConnectionRuntime')){
    $positionText=(& $positionJavap -classpath "$positionJar;$positionServer" -c -p $positionClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Area position audit failed: $positionClass"}
    $positionText | Set-Content -LiteralPath (Join-Path $positionOut ($positionClass+'.txt')) -Encoding utf8
    if($positionClass.EndsWith('$Port') -and ($positionText -notmatch 'AreaDisplacementPlanner.plan' -or $positionText -notmatch 'RootDisplacementLedger.claim' -or $positionText -notmatch 'TransformComponent.setPosition' -or $positionText -notmatch 'claimAuraSecondary')){throw 'Missing guarded native position call site'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $positionServer).Hash;jarSha256=(Get-FileHash -LiteralPath $positionJar).Hash;
    scope='Bounded horizontal swept/supported steps, native role control/grounding, hostile/protected filter, root target one-second ICD, Aura rolling secondary allowance, native Transform position write.';
    evidence='Installed bytecode and packaged call-site structure; not connected NPC motion, collision, network replication or animation proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $positionOut 'audit.json') -Encoding utf8
