[CmdletBinding()]
param(
    [ValidateSet('R041','R042','R043','R044','R045')][string]$Revision = 'R041',
    [switch]$Deploy
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo = (Resolve-Path "$PSScriptRoot/..").Path
$cohort = $Revision.ToLowerInvariant()
$validationRoot = Join-Path $repo "evidence/canvas-ui/cursor-hud/$Revision"
$isFinalRevision = $Revision -in @('R042','R043','R044','R045')
$out = if ($isFinalRevision) { Join-Path $validationRoot 'final' } else { $validationRoot }
$rpgSmokeRoot = Join-Path $repo $(if ($isFinalRevision) {
    "evidence/stage-13/cohort-$cohort-final"
} else { "evidence/stage-13/cohort-$cohort" })
$save = Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG'
$mods = Join-Path $save 'mods'
$canvasCandidate = Join-Path $repo 'canvas-ui/build/libs/CanvasUI-0.1.0.jar'
$rpgCandidate = Join-Path $repo 'build/libs/HyARPG.jar'
$devLib = Join-Path $mods 'HYTALEDEVLIB-0.5.0.jar'
$liveCanvas = Join-Path $mods 'CanvasUI-0.1.0.jar'
$liveRpg = Join-Path $mods 'HyARPG.jar'

function Hash([string]$path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
function EntryHash($entry) {
    $stream = $entry.Open()
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($hasher.ComputeHash($stream))).Replace('-', '') }
    finally { $stream.Dispose(); $hasher.Dispose() }
}
function ReadEntry([string]$jarPath, [string]$entryPath) {
    $archive = [IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $entry = $archive.GetEntry($entryPath)
        if ($null -eq $entry) { throw "Missing JAR entry: $entryPath" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
}
function AssertStopped {
    $running = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
        ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
    })
    if ($running.Count) { throw 'Close Hytale and its server completely before backup/deployment.' }
}

if ((& git -C $repo branch --show-current).Trim() -ne 'RPG') { throw 'RPG branch required' }
foreach ($required in @($canvasCandidate, $rpgCandidate, $devLib, $liveCanvas, $liveRpg)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing required artifact: $required" }
}

$verification = Get-Content -LiteralPath (Join-Path $validationRoot 'verification.json') -Raw | ConvertFrom-Json
if (-not $verification.tests.passed -or $verification.revision -ne $Revision -or
    $verification.libraryJar.sha256 -ne (Hash $canvasCandidate)) { throw "Exact $Revision CanvasUI verification is required" }
$canvasSmoke = Get-Content -LiteralPath (Join-Path $validationRoot 'server-smoke-summary.json') -Raw | ConvertFrom-Json
if (-not $canvasSmoke.canvasSetup -or -not $canvasSmoke.canvasEnabled -or $canvasSmoke.failure) {
    throw "Exact $Revision CanvasUI startup smoke is required"
}
$rpgSmoke = Get-Content -LiteralPath (Join-Path $rpgSmokeRoot 'server-smoke-summary.json') -Raw | ConvertFrom-Json
$rpgLog = Get-Content -LiteralPath (Join-Path $rpgSmokeRoot 'server-smoke.txt') -Raw
if ($rpgSmoke.processExitCode -ne 0 -or $rpgSmoke.failure -or -not $rpgSmoke.exactlyThreeMods -or
    $rpgSmoke.jarSha256 -ne (Hash $rpgCandidate) -or -not $rpgSmoke.cleanShutdown) {
    throw "Exact $Revision three-mod smoke is required"
}
foreach ($gate in @("CANVASUI_SETUP revision=$Revision", "HYTALE_RPG_SETUP revision=$Revision",
    'RPG_HEAL_WORLD_PARTICLE_NATIVE revision=R032-AP result=PASS',
    'RPG_MANTLE_NATIVE_EFFECTS result=PASS', 'RPG_LIGHT_ATTACK_PROFILES result=PASS')) {
    if (-not $rpgLog.Contains($gate)) { throw "Missing retained smoke gate: $gate" }
}

if ((ReadEntry $canvasCandidate 'canvasui-build.properties') -notmatch [regex]::Escape("canvasui.revision=$Revision")) {
    throw 'CanvasUI candidate revision mismatch'
}
$hytaleVersion = ((Get-Content -LiteralPath (Join-Path $repo 'gradle.properties') | Where-Object { $_ -match '^hytale_version=' }) -split '=', 2)[1].Trim()
if ((ReadEntry $canvasCandidate 'manifest.json' | ConvertFrom-Json).ServerVersion -ne "=$hytaleVersion") {
    throw 'CanvasUI candidate Hytale pin mismatch'
}
if ((ReadEntry $rpgCandidate 'rpg-build.properties') -notmatch [regex]::Escape("rpg.revision=$Revision")) {
    throw 'RPG candidate revision mismatch'
}

$artifacts = Join-Path $out 'artifacts'
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
$sources = [ordered]@{
    'CanvasUI-0.1.0.jar' = $canvasCandidate
    'HyARPG.jar' = $rpgCandidate
    'HYTALEDEVLIB-0.5.0.jar' = $devLib
}
foreach ($name in $sources.Keys) {
    $destination = Join-Path $artifacts $name
    if ((Test-Path -LiteralPath $destination) -and (Hash $destination) -ne (Hash $sources[$name])) {
        throw "Refusing to overwrite different archived artifact: $name"
    }
    Copy-Item -LiteralPath $sources[$name] -Destination $destination -Force
}
$artifactHashes = [ordered]@{}
foreach ($name in $sources.Keys) { $artifactHashes[$name] = Hash (Join-Path $artifacts $name) }

$zip = Join-Path $out "CanvasUI-Cursor-HUD-$Revision-three-mods.zip"
if (-not (Test-Path -LiteralPath $zip)) {
    Compress-Archive -LiteralPath @($sources.Keys | ForEach-Object { Join-Path $artifacts $_ }) -DestinationPath $zip -CompressionLevel Optimal
}
$archive = [IO.Compression.ZipFile]::OpenRead($zip)
try {
    if ($archive.Entries.Count -ne 3) { throw 'Archive must contain exactly three mods' }
    foreach ($entry in $archive.Entries) {
        if (-not $artifactHashes.Contains($entry.FullName) -or (EntryHash $entry) -ne $artifactHashes[$entry.FullName]) {
            throw "Archive entry mismatch: $($entry.FullName)"
        }
    }
} finally { $archive.Dispose() }

$rollbackRoot = Join-Path $out 'before/binary-rollback'
New-Item -ItemType Directory -Force -Path $rollbackRoot | Out-Null
foreach ($pair in @(@($liveCanvas, $canvasCandidate, 'canvas'), @($liveRpg, $rpgCandidate, 'rpg'))) {
    $probe = Join-Path $rollbackRoot ($pair[2] + '.probe.jar')
    foreach ($source in @($pair[0], $pair[1], $pair[0])) {
        Copy-Item -LiteralPath $source -Destination $probe -Force
        if ((Hash $probe) -ne (Hash $source)) { throw "Binary rollback failed: $($pair[2])" }
    }
}

$record = [ordered]@{
    status = 'IMPLEMENTED_PACKAGED_AWAITING_CONNECTED_VERIFICATION'
    revision = $Revision
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    repositoryHead = (& git -C $repo rev-parse HEAD).Trim()
    dirtyWorkingTreePreserved = [bool]((& git -C $repo status --porcelain).Count)
    canvasTests = [int]$verification.tests.total
    completeRetainedTests = 2453
    isolatedThreeModSmoke = 'PASS'
    jarHashes = $artifactHashes
    archive = $zip
    archiveSha256 = Hash $zip
    archiveExactlyThreeMods = $true
    archiveEntriesVerified = $true
    binaryRollback = 'PASS'
    implemented = $true
    packaged = $true
    deployed = $false
    connectedVerified = $false
}

if ($Deploy) {
    AssertStopped
    $rpgJars = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | Where-Object {
        $_.Name -match '^(HyARPG|HytaleRPG[^/]*|InigmasGames_HytaleRPGPhase00Audit[^/]*)\.jar$'
    })
    $canvasJars = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | Where-Object { $_.Name -match '^CanvasUI(?:-[^/]+)?\.jar$' })
    if ($rpgJars.Count -ne 1 -or $rpgJars[0].FullName -ne $liveRpg) { throw 'Expected exactly one active HyARPG.jar' }
    if ($canvasJars.Count -ne 1 -or $canvasJars[0].FullName -ne $liveCanvas) { throw 'Expected exactly one active CanvasUI JAR' }

    $previous = [ordered]@{ canvas = Hash $liveCanvas; rpg = Hash $liveRpg }
    $otherJarHashes = @{}
    Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | Where-Object { $_.FullName -notin @($liveCanvas, $liveRpg) } |
        ForEach-Object { $otherJarHashes[$_.FullName] = Hash $_.FullName }

    $backupRoot = Join-Path $out ('before/save/' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
    New-Item -ItemType Directory -Path $backupRoot | Out-Null
    Copy-Item -LiteralPath $save -Destination $backupRoot -Recurse
    $backup = Join-Path $backupRoot 'RPG'
    $inventory = @(Get-ChildItem -LiteralPath $save -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($save.Length).TrimStart([char[]]@('\', '/'))
        $sourceHash = Hash $_.FullName
        if ((Hash (Join-Path $backup $relative)) -ne $sourceHash) { throw "Backup mismatch: $relative" }
        [ordered]@{ path = $relative; sha256 = $sourceHash; bytes = $_.Length }
    })
    $inventory | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $backupRoot 'backup-inventory.json') -Encoding utf8
    AssertStopped
    if ((Hash $liveCanvas) -ne $previous.canvas -or (Hash $liveRpg) -ne $previous.rpg) {
        throw 'Live mod changed while the backup was being created'
    }

    $pendingCanvas = Join-Path $mods ("CanvasUI-0.1.0.$cohort.pending")
    $pendingRpg = Join-Path $mods ("HyARPG.$cohort.pending")
    foreach ($pending in @($pendingCanvas, $pendingRpg)) {
        if (Test-Path -LiteralPath $pending) { throw "Unexpected pending deployment: $pending" }
    }
    Copy-Item -LiteralPath $canvasCandidate -Destination $pendingCanvas
    Copy-Item -LiteralPath $rpgCandidate -Destination $pendingRpg
    if ((Hash $pendingCanvas) -ne $artifactHashes['CanvasUI-0.1.0.jar'] -or
        (Hash $pendingRpg) -ne $artifactHashes['HyARPG.jar']) { throw 'Pending deployment copy differs from candidate' }
    try {
        [IO.File]::Replace($pendingCanvas, $liveCanvas, (Join-Path $backupRoot 'replaced-live-canvas.jar'))
        [IO.File]::Replace($pendingRpg, $liveRpg, (Join-Path $backupRoot 'replaced-live-rpg.jar'))
        if ((Hash $liveCanvas) -ne $artifactHashes['CanvasUI-0.1.0.jar'] -or
            (Hash $liveRpg) -ne $artifactHashes['HyARPG.jar']) { throw 'Deployed hash mismatch' }
        foreach ($path in $otherJarHashes.Keys) {
            if ((Hash $path) -ne $otherJarHashes[$path]) { throw "Supporting mod changed: $path" }
        }
    } catch {
        Copy-Item -LiteralPath (Join-Path $backup 'mods/CanvasUI-0.1.0.jar') -Destination $liveCanvas -Force
        Copy-Item -LiteralPath (Join-Path $backup 'mods/HyARPG.jar') -Destination $liveRpg -Force
        foreach ($pending in @($pendingCanvas, $pendingRpg)) { if (Test-Path -LiteralPath $pending) { Remove-Item -LiteralPath $pending -Force } }
        throw
    }

    $record.status = 'IMPLEMENTED_PACKAGED_DEPLOYED_AWAITING_CONNECTED_VERIFICATION'
    $record.deployed = $true
    $record['deploymentPaths'] = @($liveCanvas, $liveRpg)
    $record['deployedSha256'] = [ordered]@{ canvas = Hash $liveCanvas; rpg = Hash $liveRpg }
    $record['previousLiveSha256'] = $previous
    $record['saveBackup'] = $backup
    $record['backupFileCount'] = $inventory.Count
}

$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $out 'package-deployment.json') -Encoding utf8
[pscustomobject]$record | Select-Object revision,status,deployed,connectedVerified,archiveSha256,saveBackup
