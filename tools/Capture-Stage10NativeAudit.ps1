[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$summonRoot=(Resolve-Path "$PSScriptRoot\..").Path
$summonOutput=Join-Path $summonRoot 'evidence\stage-10\api'
$summonPackage="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$summonJar=Join-Path $summonPackage 'Server\HytaleServer.jar'
New-Item -ItemType Directory -Force -Path $summonOutput | Out-Null
foreach($summonClass in @(
    'com.hypixel.hytale.server.npc.NPCPlugin',
    'com.hypixel.hytale.server.npc.role.Role',
    'com.hypixel.hytale.server.npc.role.support.WorldSupport',
    'com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport',
    'com.hypixel.hytale.server.npc.entities.NPCEntity',
    'com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent',
    'com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems$OnDeathSystem',
    'com.hypixel.hytale.server.npc.systems.NPCDamageSystems$DropDeathItems',
    'com.hypixel.hytale.server.npc.systems.NPCSystems$OnDeathSystem',
    'com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems$CorpseRemoval',
    'com.hypixel.hytale.component.NonSerialized',
    'com.hypixel.hytale.component.Store',
    'com.hypixel.hytale.component.ComponentRegistry')) {
    $summonText=& javap -classpath $summonJar -c -p $summonClass
    if($LASTEXITCODE -ne 0){throw "Native class unavailable: $summonClass"}
    $summonText | Set-Content -LiteralPath (Join-Path $summonOutput (($summonClass -split '\.')[-1]+'.txt')) -Encoding utf8
}
$summonZip=[IO.Compression.ZipFile]::OpenRead((Join-Path $summonPackage 'Assets.zip'))
try{
    $summonAssets=@()
    foreach($summonName in @('Server/NPC/Roles/Empty_Role.json','Server/NPC/Roles/_Core/Templates/Template_Summoned_Ally.json',
        'Server/NPC/Roles/_Core/Tests/Test_Pet.json','Server/NPC/Roles/Creature/Mammal/Wolf_Black.json')){
        $summonEntry=$summonZip.GetEntry($summonName)
        if($null -eq $summonEntry){throw "Missing shipped asset: $summonName"}
        $summonReader=[IO.StreamReader]::new($summonEntry.Open())
        try{$summonText=$summonReader.ReadToEnd()}finally{$summonReader.Dispose()}
        $summonText | Set-Content -LiteralPath (Join-Path $summonOutput ([IO.Path]::GetFileName($summonName)+'.txt')) -Encoding utf8
        $summonAssets+=@{path=$summonName;bytes=$summonEntry.Length}
    }
}finally{$summonZip.Dispose()}
[ordered]@{capturedAtUtc=[DateTime]::UtcNow.ToString('o');serverSha256=(Get-FileHash $summonJar).Hash;
    assetsSha256=(Get-FileHash (Join-Path $summonPackage 'Assets.zip')).Hash;shippedAssets=$summonAssets;
    evidenceScope='INSTALLED_BYTECODE_AND_ASSETS_ONLY';connectedProof=$false} |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $summonOutput 'manifest.json') -Encoding utf8
