[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$corpseRoot=(Resolve-Path "$PSScriptRoot\..").Path
$corpseOutput=Join-Path $corpseRoot 'evidence\stage-10\cohort-b\api'
$corpsePackage="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
New-Item -ItemType Directory -Force -Path $corpseOutput | Out-Null
foreach($corpseClass in @('com.hypixel.hytale.server.core.modules.entity.damage.DeferredCorpseRemoval',
    'com.hypixel.hytale.server.core.universe.world.storage.EntityStore')){
    $corpseText=& javap -classpath (Join-Path $corpsePackage 'Server\HytaleServer.jar') -p -c $corpseClass
    if($LASTEXITCODE -ne 0){throw "Missing native class: $corpseClass"}
    $corpseText | Set-Content -LiteralPath (Join-Path $corpseOutput (($corpseClass -split '\.')[-1]+'.txt')) -Encoding utf8
}
$corpseZip=[IO.Compression.ZipFile]::OpenRead((Join-Path $corpsePackage 'Assets.zip'))
try{
    $corpseEntry=$corpseZip.GetEntry('Server/NPC/Roles/_Core/Templates/Template_Predator.json')
    $corpseReader=[IO.StreamReader]::new($corpseEntry.Open())
    try{$corpseText=$corpseReader.ReadToEnd()}finally{$corpseReader.Dispose()}
    $corpseText.TrimEnd() | Set-Content -LiteralPath (Join-Path $corpseOutput 'Template_Predator.json.txt') -Encoding utf8
}finally{$corpseZip.Dispose()}
[ordered]@{scope='INSTALLED_ASSET_AND_BYTECODE_ONLY';connectedProof=$false;sourceRole='Wolf_Black';
    sourceTemplate='Server/NPC/Roles/_Core/Templates/Template_Predator.json';
    conservativeAuthoredAttackIntervalSeconds=3;nativeAttackPauseRange=@(2,3);nativeDeathAnimationTimeSeconds=1.5;
    sourceMaxHealthPolicy='Read actual native maximum at DeathComponent creation, not template defaults';
    nativeEliteClassificationProven=$false;nativeReviveProven=$false;
    files=@(Get-ChildItem -LiteralPath $corpseOutput -File | ForEach-Object {@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $corpseOutput 'manifest.json') -Encoding utf8
