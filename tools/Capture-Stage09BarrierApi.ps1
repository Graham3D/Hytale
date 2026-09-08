[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$s9BarrierRoot=(Resolve-Path "$PSScriptRoot\..").Path
$s9BarrierOut=Join-Path $s9BarrierRoot 'evidence\stage-09\api-barrier'
$s9BarrierJar="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
New-Item -ItemType Directory -Force -Path $s9BarrierOut | Out-Null
foreach($s9BarrierClass in @(
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageCause',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems$DamageSequence',
    'com.hypixel.hytale.server.core.entity.InteractionChain',
    'com.hypixel.hytale.server.core.entity.InteractionManager',
    'com.hypixel.hytale.server.core.entity.InteractionContext',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$FilterUnkillable',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$FilterPlayerWorldConfig',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$FilterNPCWorldConfig',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ScaleOutgoingDamageFromEntityEffects',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$WieldingDamageReduction',
    'com.hypixel.hytale.server.npc.systems.NPCDamageSystems$FilterDamageSystem')){
    $s9BarrierCode=& javap -classpath $s9BarrierJar -c -p $s9BarrierClass 2>&1
    # Missing candidate classes are audit findings, not invented integration dependencies.
    $s9BarrierCode | Set-Content -LiteralPath (Join-Path $s9BarrierOut "$s9BarrierClass.txt") -Encoding utf8
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $s9BarrierJar).Hash;capturedAtUtc=[DateTime]::UtcNow.ToString('o');connectedProof=$false} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $s9BarrierOut 'identity.json') -Encoding utf8
