[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$s9AuraRoot=(Resolve-Path "$PSScriptRoot\..").Path
$s9AuraOut=Join-Path $s9AuraRoot 'evidence\stage-09\api-aura'
$s9AuraJar="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
New-Item -ItemType Directory -Force -Path $s9AuraOut | Out-Null
foreach($s9AuraClass in @(
    'com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler',
    'com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler$Cooldown',
    'com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction',
    'com.hypixel.hytale.protocol.InteractionCooldown',
    'com.hypixel.hytale.server.core.asset.type.item.config.ItemAbility',
    'com.hypixel.hytale.server.core.asset.type.item.config.Item')){
    $s9AuraCode=& javap -classpath $s9AuraJar -c -p $s9AuraClass 2>&1
    $s9AuraCode | Set-Content -LiteralPath (Join-Path $s9AuraOut "$s9AuraClass.txt") -Encoding utf8
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $s9AuraJar).Hash;capturedAtUtc=[DateTime]::UtcNow.ToString('o');connectedProof=$false} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $s9AuraOut 'identity.json') -Encoding utf8
