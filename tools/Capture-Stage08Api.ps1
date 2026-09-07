[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$stage8Root=(Resolve-Path "$PSScriptRoot\..").Path
$stage8Audit=Join-Path $stage8Root 'evidence\stage-08\api'
$stage8Package="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$stage8Jar=Join-Path $stage8Package 'Server\HytaleServer.jar'
New-Item -ItemType Directory -Path $stage8Audit -Force | Out-Null
foreach($stage8Class in @('com.hypixel.hytale.server.core.modules.collision.CollisionModule',
        'com.hypixel.hytale.server.core.modules.collision.BlockCollisionData',
        'com.hypixel.hytale.server.core.modules.entity.component.BoundingBox',
        'com.hypixel.hytale.server.core.modules.entity.component.TransformComponent',
        'com.hypixel.hytale.server.core.modules.entity.component.HeadRotation',
        'com.hypixel.hytale.server.npc.role.support.WorldSupport',
        'com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap',
        'com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent',
        'com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes',
        'com.hypixel.hytale.server.core.modules.entity.damage.DamageCause',
        'com.hypixel.hytale.server.core.modules.debug.DebugUtils')) {
    $stage8Code=& javap -classpath $stage8Jar -c -p $stage8Class
    if($LASTEXITCODE -ne 0){throw "Missing native class $stage8Class"}
    $stage8Code | Set-Content -LiteralPath (Join-Path $stage8Audit "$stage8Class.txt") -Encoding utf8
}
$stage8Zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $stage8Package 'Assets.zip'))
try {
    $stage8Assets=@{}
    foreach($stage8Name in @('Server/Entity/Damage/Wind.json','Server/Entity/Damage/Lightning.json','Server/Entity/Damage/Elemental.json')) {
        $stage8Entry=$stage8Zip.GetEntry($stage8Name)
        if(-not $stage8Entry){throw "Missing shipped damage channel $stage8Name"}
        $stage8Reader=[IO.StreamReader]::new($stage8Entry.Open())
        try {$stage8Assets[$stage8Name]=$stage8Reader.ReadToEnd() | ConvertFrom-Json}finally{$stage8Reader.Dispose()}
    }
    $stage8Assets | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $stage8Audit 'shipped-damage-channels.json') -Encoding utf8
} finally {$stage8Zip.Dispose()}
[ordered]@{serverSha256=(Get-FileHash $stage8Jar).Hash;assetsSha256=(Get-FileHash (Join-Path $stage8Package 'Assets.zip')).Hash;
    inspectedAtUtc=[DateTime]::UtcNow.ToString('o');connectedProof=$false} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stage8Audit 'identity.json') -Encoding utf8
