[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$assets=Join-Path $env:APPDATA 'Hytale/install/pre-release/package/game/latest/Assets.zip'
$out=Join-Path $repo 'evidence/stage-13/cohort-am'
$video='C:/Users/Zemio/OneDrive/Desktop/beam.mp4'
$videoHash=(Get-FileHash -LiteralPath $video).Hash
if($videoHash -ne 'F3B77CCF6E8D4001BA6C8F961B794020C262BA48D3653861C7E1B5240FA48823'){throw 'Reference video changed; inspect metadata/frames again before updating evidence'}
function Equal-JsonValue($a,$b){
    if($null -eq $a -or $null -eq $b){return $null -eq $a -and $null -eq $b}
    if($a -is [pscustomobject]){
        if($b -isnot [pscustomobject]){return $false}
        $left=@($a.PSObject.Properties.Name|Sort-Object);$right=@($b.PSObject.Properties.Name|Sort-Object)
        if(($left -join '|') -cne ($right -join '|')){return $false}
        foreach($key in $left){if(-not(Equal-JsonValue $a.$key $b.$key)){return $false}};return $true
    }
    if($a -is [array]){if($b -isnot [array] -or $a.Count -ne $b.Count){return $false};for($i=0;$i -lt $a.Count;$i++){if(-not(Equal-JsonValue $a[$i] $b[$i])){return $false}};return $true}
    if($a -is [string] -or $b -is [string]){return $a -is [string] -and $b -is [string] -and $a -ceq $b}
    if($a -is [bool] -or $b -is [bool]){return $a -is [bool] -and $b -is [bool] -and $a -eq $b}
    # JSON's 1 and 1.0 denote the same number; text serialization alone is not structural equality.
    return [decimal]$a -eq [decimal]$b
}
$zip=[IO.Compression.ZipFile]::OpenRead($assets)
try{
    $children=@(foreach($name in @('Sparks','Glow','Plus')){
        $path="Server/Particles/_Test/HealBeams/Spawners/Beam_Heal_Green2_$name.particlespawner"
        $entry=$zip.GetEntry($path);$stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
        try{$hash=([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','')}finally{$stream.Dispose();$sha.Dispose()}
        $reader=[IO.StreamReader]::new($entry.Open());try{$stock=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
        $relative="src/main/resources/Server/Particles/RPG/HealingPath/RPG_Heal_Path_$name.particlespawner"
        $derived=Get-Content (Join-Path $repo $relative) -Raw|ConvertFrom-Json
        [ordered]@{child=$name;installedPath=$path;installedSha256=$hash;texture=$stock.Particle.Texture;renderMode=$stock.RenderMode;
            particleLifeSpan=$stock.ParticleLifeSpan;derivedPath=$relative;derivedSha256=(Get-FileHash (Join-Path $repo $relative)).Hash;
            appearanceUnchanged=(Equal-JsonValue $stock.Particle $derived.Particle);
            initialSpeed=$derived.InitialVelocity.Speed;anchorFollowMultiplier=$derived.TrailSpawnerPositionMultiplier;
            maximumConcurrent=$derived.MaxConcurrentParticles;spawnRate=$derived.SpawnRate}
    })
    if(@($children|Where-Object {-not $_.appearanceUnchanged}).Count){throw 'Child appearance changed'}
    [ordered]@{capturedAtUtc=[DateTime]::UtcNow.ToString('o');assetsSha256=(Get-FileHash $assets).Hash;
        video=$video;videoSha256=$videoHash;
        inspectedVideo=@{width=1242;height=314;fps=60;durationSeconds=2.02};children=$children;
        connectedRendererProof=$false}|ConvertTo-Json -Depth 30|Set-Content (Join-Path $out 'asset-provenance.json') -Encoding utf8
}finally{$zip.Dispose()}
