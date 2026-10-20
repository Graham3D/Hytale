[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$collisionRoot=(Resolve-Path "$PSScriptRoot\..").Path
$collisionOutput=Join-Path $collisionRoot 'evidence\stage-10\cohort-g\api'
New-Item -ItemType Directory -Force -Path $collisionOutput | Out-Null
$collisionServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$collisionJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
foreach($collisionClass in @(
 'com.hypixel.hytale.server.core.modules.collision.CollisionModule',
 'com.hypixel.hytale.server.core.modules.collision.CollisionConfig',
 'com.hypixel.hytale.server.core.modules.collision.CollisionFilter',
 'com.hypixel.hytale.server.core.modules.collision.CollisionResult',
 'com.hypixel.hytale.server.core.modules.collision.EntityCollisionProvider',
 'com.hypixel.hytale.server.core.modules.collision.TangiableEntitySpatialSystem',
 'com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision',
 'com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollisionConfig',
 'com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollisionConfigPacketGenerator')){
 $collisionBytes=& $collisionJavap -classpath $collisionServer -p -c $collisionClass 2>&1
 if($LASTEXITCODE -ne 0){throw "Cannot audit $collisionClass"}
 $collisionBytes | Set-Content -LiteralPath (Join-Path $collisionOutput (($collisionClass.Split('.')[-1])+'.txt')) -Encoding utf8
}
[ordered]@{scope='INSTALLED_COLLISION_QUERY_AND_REPLICATED_HITBOX_API';serverSha256=(Get-FileHash $collisionServer).Hash;connectedSelectiveCollisionProven=$false} |
 ConvertTo-Json | Set-Content -LiteralPath (Join-Path $collisionOutput 'manifest.json') -Encoding utf8
