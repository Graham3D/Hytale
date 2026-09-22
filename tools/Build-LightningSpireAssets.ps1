[CmdletBinding()]
param([string]$Repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Ensure-Directory([string]$Path) { [IO.Directory]::CreateDirectory($Path) | Out-Null }
function Convert-HorizontalStrip([string]$Source,[string]$Destination,[int]$FrameWidth,[int]$FrameHeight,[int]$Frames) {
    $image=[Drawing.Bitmap]::new($Source)
    try {
        if($image.Width-ne $FrameWidth*$Frames-or$image.Height-ne$FrameHeight){throw "Unexpected strip dimensions: $Source"}
        $output=[Drawing.Bitmap]::new($FrameWidth,$FrameHeight*$Frames,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics=[Drawing.Graphics]::FromImage($output)
            try { for($i=0;$i-lt$Frames;$i++){$sourceRect=[Drawing.Rectangle]::new($i*$FrameWidth,0,$FrameWidth,$FrameHeight);$destRect=[Drawing.Rectangle]::new(0,$i*$FrameHeight,$FrameWidth,$FrameHeight);$graphics.DrawImage($image,$destRect,$sourceRect,[Drawing.GraphicsUnit]::Pixel)} }
            finally{$graphics.Dispose()}
            $output.Save($Destination,[Drawing.Imaging.ImageFormat]::Png)
        } finally {$output.Dispose()}
    } finally {$image.Dispose()}
}

$vfxDir=Join-Path $Repository 'src/main/resources/Common/VFX/RPG/LightningSpire'
$modelDir=$vfxDir
$uiDir=$vfxDir
Ensure-Directory $vfxDir

# Preserve the owner-authored model hierarchy/UVs. The editor's `format: prop`
# marker is converted to the native runtime's blockymodel schema marker only.
$model=(Get-Content (Join-Path $Repository 'art/Models/LightningSpire.blockymodel') -Raw | ConvertFrom-Json)
$model.PSObject.Properties.Remove('format');$model | Add-Member -NotePropertyName formatVersion -NotePropertyValue 1
$model | ConvertTo-Json -Depth 100 | Set-Content (Join-Path $modelDir 'LightningSpire_R072.blockymodel') -Encoding utf8
Copy-Item (Join-Path $Repository 'art/Models/LightningSpire.png') (Join-Path $modelDir 'LightningSpire.png') -Force

Convert-HorizontalStrip (Join-Path $Repository 'art/Particles/shock.png') (Join-Path $vfxDir 'Shock_Strip_Vertical.png') 56 49 8
Convert-HorizontalStrip (Join-Path $Repository 'art/Particles/shockwave.png') (Join-Path $vfxDir 'Shockwave_Strip_Vertical.png') 56 64 3
Copy-Item (Join-Path $Repository 'art/UI/skill_lightningspire_bar.png') (Join-Path $uiDir 'skill_lightningspire_bar.png') -Force
Copy-Item (Join-Path $Repository 'art/UI/skill_lightningspire_bg.png') (Join-Path $uiDir 'skill_lightningspire_bg.png') -Force
Copy-Item (Join-Path $Repository 'art/UI/skill_lightningspire_frame.png') (Join-Path $uiDir 'skill_lightningspire_frame.png') -Force

$gaugeModel=@'
{"formatVersion":1,"lod":"auto","nodes":[
{"id":"0","name":"GaugeZ","children":[],"position":{"x":0,"y":0,"z":0},"orientation":{"x":0,"y":0,"z":0,"w":1},"shape":{"type":"quad","offset":{"x":0,"y":0,"z":0},"stretch":{"x":0.5,"y":0.5,"z":0.5},"settings":{"size":{"x":128,"y":24},"normal":"+Z","isStaticBox":true},"visible":true,"doubleSided":true,"shadingMode":"fullbright","unwrapMode":"custom","textureLayout":{"front":{"offset":{"x":0,"y":0},"mirror":{"x":false,"y":false},"angle":0}}}},
{"id":"1","name":"GaugeX","children":[],"position":{"x":0,"y":0,"z":0},"orientation":{"x":0,"y":0,"z":0,"w":1},"shape":{"type":"quad","offset":{"x":0,"y":0,"z":0},"stretch":{"x":0.5,"y":0.5,"z":0.5},"settings":{"size":{"x":128,"y":24},"normal":"+X","isStaticBox":true},"visible":true,"doubleSided":true,"shadingMode":"fullbright","unwrapMode":"custom","textureLayout":{"front":{"offset":{"x":0,"y":0},"mirror":{"x":false,"y":false},"angle":0}}}}
]}
'@
$gaugeModel|Set-Content (Join-Path $uiDir 'LightningSpire_Gauge.blockymodel') -Encoding utf8
$background=[Drawing.Bitmap]::new((Join-Path $Repository 'art/UI/skill_lightningspire_bg.png'))
$bar=[Drawing.Bitmap]::new((Join-Path $Repository 'art/UI/skill_lightningspire_bar.png'))
$frame=[Drawing.Bitmap]::new((Join-Path $Repository 'art/UI/skill_lightningspire_frame.png'))
try {
    $serverModels=Join-Path $Repository 'src/main/resources/Server/Models/RPG';Ensure-Directory $serverModels
    foreach($percent in 0,25,50,75,100){
        $gauge=[Drawing.Bitmap]::new(128,24,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try{$g=[Drawing.Graphics]::FromImage($gauge);try{
            $g.Clear([Drawing.Color]::Transparent);$g.DrawImage($background,[Drawing.Rectangle]::new(4,0,120,24))
            $width=[Math]::Round(120*$percent/100);if($width-gt 0){$g.DrawImage($bar,[Drawing.Rectangle]::new(4,0,$width,24),[Drawing.Rectangle]::new(0,0,1,24),[Drawing.GraphicsUnit]::Pixel)}
            $g.DrawImage($frame,[Drawing.Rectangle]::new(0,0,128,24))
        }finally{$g.Dispose()};$texture="LightningSpire_Gauge_$percent.png";$gauge.Save((Join-Path $uiDir $texture),[Drawing.Imaging.ImageFormat]::Png)
        }finally{$gauge.Dispose()}
        $config=[ordered]@{Model='VFX/RPG/LightningSpire/LightningSpire_Gauge.blockymodel';Texture="VFX/RPG/LightningSpire/$texture";HitBox=[ordered]@{Max=[ordered]@{X=.01;Y=.01;Z=.01};Min=[ordered]@{X=-.01;Y=-.01;Z=-.01}};MinScale=1;MaxScale=1}
        $config|ConvertTo-Json -Depth 5|Set-Content (Join-Path $serverModels "Hywind_Lightning_Spire_Gauge_$percent.json") -Encoding utf8
    }
}finally{$background.Dispose();$bar.Dispose();$frame.Dispose()}

$summary=[ordered]@{
    modelNodes=$model.nodes.Count; modelSourceSha256=(Get-FileHash (Join-Path $Repository 'art/Models/LightningSpire.blockymodel') -Algorithm SHA256).Hash
    modelTextureSha256=(Get-FileHash (Join-Path $modelDir 'LightningSpire.png') -Algorithm SHA256).Hash
    shockFrames=8;shockFrame='56x49';shockwaveFrames=3;shockwaveFrame='56x64'
    gauge=[ordered]@{bar='1x24';background='120x24';frame='128x24'}
}
$summary|ConvertTo-Json -Depth 5
