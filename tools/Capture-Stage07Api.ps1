[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$stage7Root=(Resolve-Path "$PSScriptRoot\..").Path
$stage7Audit=Join-Path $stage7Root 'evidence\stage-07\api'
$stage7Jar="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
New-Item -ItemType Directory -Path $stage7Audit -Force | Out-Null
foreach($stage7Class in @('com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider',
        'com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsConfig',
        'com.hypixel.hytale.server.core.modules.projectile.config.ImpactConsumer',
        'com.hypixel.hytale.server.core.modules.projectile.ProjectileModule',
        'com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem',
        'com.hypixel.hytale.server.core.modules.entity.component.TransformComponent',
        'com.hypixel.hytale.server.core.modules.physics.component.Velocity',
        'com.hypixel.hytale.server.core.modules.collision.EntityRefCollisionProvider',
        'com.hypixel.hytale.server.core.modules.projectile.component.PierceProjectile',
        'com.hypixel.hytale.component.CommandBuffer',
        'com.hypixel.hytale.server.core.modules.entity.DespawnComponent',
        'com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath',
        'com.hypixel.hytale.server.core.modules.physics.util.ForceProviderStandardState',
        'com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider$STATE')) {
    $stage7Code=& javap -classpath $stage7Jar -c -p $stage7Class
    if($LASTEXITCODE -ne 0){throw "Missing native class $stage7Class"}
    $stage7Code | Set-Content -LiteralPath (Join-Path $stage7Audit "$stage7Class.txt") -Encoding utf8
}
[ordered]@{serverSha256=(Get-FileHash $stage7Jar).Hash; inspectedAtUtc=[DateTime]::UtcNow.ToString('o'); connectedProof=$false} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stage7Audit 'identity.json') -Encoding utf8
