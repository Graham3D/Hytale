[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$batchRoot=(Resolve-Path "$PSScriptRoot\..").Path
$batchOutput=Join-Path $batchRoot 'evidence\stage-10\cohort-c\api'
New-Item -ItemType Directory -Force -Path $batchOutput | Out-Null
$batchZip=[IO.Compression.ZipFile]::OpenRead("$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip")
try{
    $batchModels=@()
    foreach($batchName in @('Server/Models/Void/Crawler_Void.json','Server/Models/Beast/Scarak_Louse.json')){
        $batchReader=[IO.StreamReader]::new($batchZip.GetEntry($batchName).Open())
        try{$batchRaw=$batchReader.ReadToEnd()}finally{$batchReader.Dispose()}
        $batchModel=$batchRaw | ConvertFrom-Json
        if($null -eq $batchZip.GetEntry('Common/'+$batchModel.Model) -or $null -eq $batchZip.GetEntry('Common/'+$batchModel.Texture)){throw 'Native model/texture is not in installed Assets.zip'}
        $batchRaw.TrimEnd() | Set-Content -LiteralPath (Join-Path $batchOutput ([IO.Path]::GetFileName($batchName)+'.txt')) -Encoding utf8
        $batchModels+=@{asset=$batchName;model=$batchModel.Model;texture=$batchModel.Texture;animations=@($batchModel.AnimationSets.PSObject.Properties.Name);connectedAnimationProof=$false}
    }
}finally{$batchZip.Dispose()}
[ordered]@{scope='INSTALLED_MODELS_AND_RESOURCE_REFERENCES';models=$batchModels;connectedProof=$false;sourceCombatOrLootCopied=$false} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $batchOutput 'manifest.json') -Encoding utf8
