[CmdletBinding()]
param(
    [string]$ServerJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar",
    [string]$HtDevLibJar = "$env:APPDATA\Hytale\UserData\Saves\RPG\mods\HYTALEDEVLIB-0.5.0.jar",
    [string]$OutputDirectory = "$PSScriptRoot\..\evidence\phase-00\api"
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ServerJar)) { throw "Server jar not found: $ServerJar" }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$classes = @(
    'com.hypixel.hytale.protocol.InteractionType',
    'com.hypixel.hytale.protocol.MouseButtonEvent',
    'com.hypixel.hytale.protocol.MouseMotionEvent',
    'com.hypixel.hytale.protocol.MouseButtonType',
    'com.hypixel.hytale.protocol.MouseButtonState',
    'com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain',
    'com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains',
    'com.hypixel.hytale.protocol.packets.player.MouseInteraction',
    'com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType',
    'com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime',
    'com.hypixel.hytale.protocol.packets.interface_.HudComponent',
    'com.hypixel.hytale.protocol.packets.interface_.Page',
    'com.hypixel.hytale.server.core.entity.entities.Player',
    'com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud',
    'com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager',
    'com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage',
    'com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage',
    'com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager',
    'com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent',
    'com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent',
    'com.hypixel.hytale.server.core.io.adapter.PacketAdapters',
    'com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher',
    'com.hypixel.hytale.server.core.inventory.InventoryComponent',
    'com.hypixel.hytale.server.core.inventory.container.filter.AbilitySlotAddFilter',
    'com.hypixel.hytale.server.core.inventory.container.filter.AbilitySupportSlotFilter',
    'com.hypixel.hytale.server.core.asset.type.item.config.AbilitySlot',
    'com.hypixel.hytale.server.core.asset.type.item.config.ItemAbility',
    'com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenAbilityBenchInteraction',
    'com.hypixel.hytale.server.core.modules.interaction.util.AbilityCastUtil',
    'com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap',
    'com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems',
    'com.hypixel.hytale.server.core.ui.builder.UICommandBuilder',
    'com.hypixel.hytale.server.core.ui.builder.UIEventBuilder'
)

$output = & javap -classpath $ServerJar -public @classes 2>&1
if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
$output | Set-Content -LiteralPath (Join-Path $OutputDirectory 'javap-public-api.txt') -Encoding utf8

$htDevLibClasses = @(
    'org.hytaledevlib.lib.StatsHelper',
    'org.hytaledevlib.lib.UIHelper',
    'org.hytaledevlib.lib.ParticleHelper',
    'org.hytaledevlib.lib.SoundHelper',
    'org.hytaledevlib.lib.InventoryHelper',
    'org.hytaledevlib.lib.DeathHelper'
)
$htDevLibOutput = & javap -classpath "$HtDevLibJar;$ServerJar" -public @htDevLibClasses 2>&1
if ($LASTEXITCODE -ne 0) { throw ($htDevLibOutput -join [Environment]::NewLine) }
$htDevLibOutput | Set-Content -LiteralPath (Join-Path $OutputDirectory 'htdevlib-public-api.txt') -Encoding utf8

$manifestPath = Join-Path $OutputDirectory 'server-manifest.txt'
Push-Location $OutputDirectory
try {
    & jar xf $ServerJar 'META-INF/MANIFEST.MF'
    Move-Item -LiteralPath (Join-Path $OutputDirectory 'META-INF\MANIFEST.MF') -Destination $manifestPath -Force
    Remove-Item -LiteralPath (Join-Path $OutputDirectory 'META-INF') -Force
}
finally { Pop-Location }

[pscustomobject]@{
    serverJar = $ServerJar
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $ServerJar).Hash
    htDevLibJar = $HtDevLibJar
    htDevLibSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $HtDevLibJar).Hash
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    classes = $classes
    htDevLibClasses = $htDevLibClasses
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'api-capture.json') -Encoding utf8

Get-ChildItem -LiteralPath $OutputDirectory | Select-Object Name, Length
