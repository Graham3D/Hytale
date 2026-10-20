[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$stage9Root=(Resolve-Path "$PSScriptRoot\..").Path
$stage9Audit=Join-Path $stage9Root 'evidence\stage-09\api'
$stage9Package="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$stage9Jar=Join-Path $stage9Package 'Server\HytaleServer.jar'
New-Item -ItemType Directory -Path $stage9Audit -Force | Out-Null
foreach($stage9Class in @('com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap',
        'com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue',
        'com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems$Regenerate',
        'com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems$Recalculate',
        'com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems$Changes',
        'com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule$PlayerRegenerateStatsSystem',
        'com.hypixel.hytale.server.core.modules.entitystats.RegeneratingValue',
        'com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType$Regenerating',
        'com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType$Regenerating$RegenType',
        'com.hypixel.hytale.server.core.modules.entitystats.asset.modifier.RegeneratingModifier',
        'com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier',
        'com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier',
        'com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier$CalculationType$2',
        'com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor',
        'com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier$ModifierTarget',
        'com.hypixel.hytale.server.core.modules.entity.damage.Damage',
        'com.hypixel.hytale.server.core.modules.entity.damage.DamageModule',
        'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ApplyDamage',
        'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ArmorDamageReduction',
        'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$PlayerDamageFilterSystem',
        'com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent',
        'com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect',
        'com.hypixel.hytale.server.npc.role.support.WorldSupport')) {
    $stage9Code=& javap -classpath $stage9Jar -c -p $stage9Class
    if($LASTEXITCODE -ne 0){throw "Missing native class $stage9Class"}
    $stage9Code | Set-Content -LiteralPath (Join-Path $stage9Audit "$stage9Class.txt") -Encoding utf8
}
$stage9Zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $stage9Package 'Assets.zip'))
try {
    $stage9Assets=@{}
    foreach($stage9Name in @('Server/Entity/Stats/Mana.json','Server/Entity/Stats/Stamina.json')) {
        $stage9Entry=$stage9Zip.GetEntry($stage9Name)
        if(-not $stage9Entry){throw "Missing shipped stat asset $stage9Name"}
        $stage9Reader=[IO.StreamReader]::new($stage9Entry.Open())
        try {$stage9Assets[$stage9Name]=$stage9Reader.ReadToEnd() | ConvertFrom-Json}finally{$stage9Reader.Dispose()}
    }
    $stage9Assets | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $stage9Audit 'shipped-stats.json') -Encoding utf8
    $stage9ArmorRegen=@()
    foreach($stage9Entry in $stage9Zip.Entries) {
        if($stage9Entry.FullName -notlike 'Server/Item/Items/*.json'){continue}
        $stage9Reader=[IO.StreamReader]::new($stage9Entry.Open())
        try {$stage9Text=$stage9Reader.ReadToEnd()}finally{$stage9Reader.Dispose()}
        if($stage9Text -match '"Regenerating"') {
            $stage9ArmorRegen+= [ordered]@{asset=$stage9Entry.FullName;content=($stage9Text|ConvertFrom-Json)}
        }
    }
    ConvertTo-Json -InputObject @($stage9ArmorRegen) -Depth 30 | Set-Content -LiteralPath (Join-Path $stage9Audit 'shipped-item-regeneration.json') -Encoding utf8
} finally {$stage9Zip.Dispose()}
[ordered]@{serverSha256=(Get-FileHash $stage9Jar).Hash;assetsSha256=(Get-FileHash (Join-Path $stage9Package 'Assets.zip')).Hash;
    inspectedAtUtc=[DateTime]::UtcNow.ToString('o');connectedProof=$false} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stage9Audit 'identity.json') -Encoding utf8
