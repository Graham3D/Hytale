[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$movementRoot=(Resolve-Path "$PSScriptRoot\..").Path
$movementOut=Join-Path $movementRoot 'evidence\stage-11\cohort-d\api'
$movementJar="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$movementJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $movementOut | Out-Null
foreach($movementClass in @('com.hypixel.hytale.server.core.entity.entities.Player','com.hypixel.hytale.server.core.entity.LivingEntity','com.hypixel.hytale.server.core.entity.Entity')){
    & $movementJavap -classpath $movementJar -c -p $movementClass | Set-Content -LiteralPath (Join-Path $movementOut ($movementClass+'.txt')) -Encoding utf8
    if($LASTEXITCODE -ne 0){throw 'Installed movement API audit failed'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $movementJar).Hash;playerMoveTo='delegates after addLocationChange';entityMoveTo='TransformComponent.getPosition().set(x,y,z)';meaning='Server-side Transform is available for post-move measurement; not connected motion or client acceptance proof';connected='UNVERIFIED'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $movementOut 'audit.json') -Encoding utf8
