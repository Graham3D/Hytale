$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R024'
$save = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG"
$trace = Join-Path $save 'mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg\skill-trace.jsonl'
$log = Join-Path $save 'logs\2026-09-07_14-02-02_server.log'
$server = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$master = 'C:\Users\Zemio\Downloads\Hytale RPG Master Implementation Specification v1.2.docx.md'
New-Item -ItemType Directory -Force -Path $evidence, (Join-Path $evidence 'rollback') | Out-Null
$rows = @(Get-Content -LiteralPath $trace | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object rpgRevision -eq 'R023')
$counts = [ordered]@{}
foreach ($name in @('NATIVE_ABILITY_SLOT_PROJECTED','NATIVE_ABILITY_INPUT_OBSERVED','SKILL_ACTIVATION_REQUEST',
        'SKILL_COMMITTED','EXECUTOR_DISPATCH','PROJECTILE_SPAWNED')) {
    $counts[$name] = @($rows | Where-Object eventType -eq $name).Count
}
$result = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R023'; serverLog = (Split-Path $log -Leaf)
    serverLogSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $log).Hash
    traceSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $trace).Hash
    masterV12Sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $master).Hash
    eventCounts = $counts
    controlSummaries = @($rows | Where-Object eventType -eq 'NATIVE_RUNE_CONTROL_SUMMARY' | Select-Object timestamp, correlationId, details)
    controlRestorations = @($rows | Where-Object eventType -eq 'NATIVE_RUNE_CONTROL_RESTORE' | Select-Object timestamp, correlationId, details)
    ownerConnectedObservation = 'Yes, native Fireball cast'
    ownerObservationSource = 'Owner reply in this task on 2026-09-07; not inferred from tests or smoke'
    connectedTransport = 'QuicheConnection / QuicheListener in the connected server log'
    conclusion = 'Vanilla Rune visibly cast while PacketAdapters inbound watcher was wholly silent. Observation boundary invalid for this connected transport. RPG casting remains unverified.'
}
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $evidence 'r023-connected-control.json') -Encoding utf8
foreach ($class in @('com.hypixel.hytale.lib.quiche.QuicheChannel',
        'com.hypixel.hytale.server.core.io.netty.PlayerChannelHandler',
        'com.hypixel.hytale.server.core.io.PacketHandler',
        'com.hypixel.hytale.server.core.io.handlers.GenericPacketHandler',
        'com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction',
        'com.hypixel.hytale.server.core.entity.InteractionContext')) {
    $output = & javap -classpath $server -c -p $class
    if ($LASTEXITCODE -ne 0) { throw "API audit failed: $class" }
    $output | Set-Content -LiteralPath (Join-Path $evidence (($class.Split('.')[-1]) + '-javap.txt')) -Encoding utf8
}
$old = Join-Path $save 'mods\HytaleRPG-0.0.16.jar'
if ((Get-FileHash -LiteralPath $old -Algorithm SHA256).Hash -ne 'D3CEEA9CEEA5995F515451317452AB9A9CBB62F3E6CF56953B5F707A1BF7FA42') {
    throw 'R023 deployed baseline changed; do not assume rollback identity.'
}
$backup = Join-Path $evidence 'rollback\HytaleRPG-0.0.16.jar'
Copy-Item -LiteralPath $old -Destination $backup -Force
if ((Get-FileHash -LiteralPath $old).Hash -ne (Get-FileHash -LiteralPath $backup).Hash) { throw 'Rollback copy mismatch.' }
$result
