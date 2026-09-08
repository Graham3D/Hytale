[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$s9FiniteRoot=(Resolve-Path "$PSScriptRoot\..").Path
$s9FiniteOut=Join-Path $s9FiniteRoot 'evidence\stage-09\api-finite'
$s9FiniteJar="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
New-Item -ItemType Directory -Force -Path $s9FiniteOut | Out-Null
foreach($s9FiniteClass in @(
    'com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport',
    'com.hypixel.hytale.server.npc.role.support.CombatSupport',
    'com.hypixel.hytale.server.npc.corecomponents.entity.ActionSetMarkedTarget',
    'com.hypixel.hytale.server.npc.corecomponents.entity.ActionReleaseTarget',
    'com.hypixel.hytale.server.npc.movement.steeringforces.SteeringForceEvade',
    'com.hypixel.hytale.server.npc.movement.steeringforces.SteeringForceWithTarget',
    'com.hypixel.hytale.server.npc.corecomponents.movement.BodyMotionMoveAway',
    'com.hypixel.hytale.server.npc.movement.controllers.MotionController',
    'com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase',
    'com.hypixel.hytale.server.npc.movement.controllers.MotionControllerWalk',
    'com.hypixel.hytale.server.npc.movement.controllers.ProbeMoveData',
    'com.hypixel.hytale.server.npc.systems.RoleSystems$BehaviourTickSystem',
    'com.hypixel.hytale.server.npc.systems.AvoidanceSystem',
    'com.hypixel.hytale.server.npc.systems.SteeringSystem',
    'com.hypixel.hytale.server.core.asset.type.entityeffect.config.ApplicationEffects',
    'com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction',
    'com.hypixel.hytale.server.core.modules.entity.livingentity.LivingEntityEffectSystem')) {
    $s9FiniteCode=& javap -classpath $s9FiniteJar -c -p $s9FiniteClass
    if($LASTEXITCODE -ne 0){throw "Missing $s9FiniteClass"}
    $s9FiniteCode | Set-Content -LiteralPath (Join-Path $s9FiniteOut "$s9FiniteClass.txt") -Encoding utf8
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $s9FiniteJar).Hash;capturedAtUtc=[DateTime]::UtcNow.ToString('o');connectedProof=$false} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $s9FiniteOut 'identity.json') -Encoding utf8
