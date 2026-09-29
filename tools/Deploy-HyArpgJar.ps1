[CmdletBinding()]
param([string]$CandidateJar = '')

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$save = (Resolve-Path -LiteralPath (Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG')).Path
$mods = Join-Path $save 'mods'
$installed = Join-Path $mods 'HyARPG.jar'
if (-not $CandidateJar) { $CandidateJar = Join-Path $projectRoot 'build/libs/HyARPG.jar' }
$candidate = (Resolve-Path -LiteralPath $CandidateJar).Path
if (-not (Test-Path -LiteralPath $installed -PathType Leaf)) { throw 'The canonical RPG save has no installed HyARPG.jar.' }
if (Test-Path -LiteralPath (Join-Path $mods 'Hywind.jar')) { throw 'Two RPG plugin JARs are present; resolve this before deployment.' }
if (@(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'HytaleClient.exe' -or
    ($_.Name -match '^javaw?\.exe$' -and $_.CommandLine -match 'HytaleServer')
}).Count) { throw 'Close Hytale before replacing the mod JAR.' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($candidate)
try {
    $entry = $archive.GetEntry('manifest.json')
    if ($null -eq $entry) { throw 'Candidate manifest missing.' }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
} finally { $archive.Dispose() }
if ($manifest.Group -ne 'InigmasGames' -or $manifest.Name -ne 'HyARPG' -or
    $manifest.Main -ne 'com.inigmasgames.hywind.HyArpgPlugin' -or
    $manifest.Metadata.Product -ne 'HyARPG') { throw 'Candidate is not the current HyARPG product.' }

& (Join-Path $PSScriptRoot 'Test-HyArpgPackage.ps1') -JarPath $candidate `
    -ExpectedVersion $manifest.Version -ExpectedRevision $manifest.Metadata.RpgRevision | Out-Null
if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw 'Offline package validation failed.' }

$candidateHash = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash
$oldHash = (Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$evidence = Join-Path $projectRoot "evidence/hyarpg/jar-deploy-$stamp"
New-Item -ItemType Directory -Path $evidence | Out-Null
$backup = Join-Path $evidence 'HyARPG-before.jar'
Copy-Item -LiteralPath $installed -Destination $backup
if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $oldHash) {
    throw 'Installed JAR backup failed checksum verification.'
}
$staged = Join-Path $mods ('.HyARPG-' + [Guid]::NewGuid().ToString('N') + '.pending')
$published = $false
try {
    Copy-Item -LiteralPath $candidate -Destination $staged
    if ((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash -ne $candidateHash) {
        throw 'Staged candidate checksum mismatch.'
    }
    if (@(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq 'HytaleClient.exe' -or
        ($_.Name -match '^javaw?\.exe$' -and $_.CommandLine -match 'HytaleServer')
    }).Count) { throw 'Hytale started during deployment.' }
    if ((Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash -ne $oldHash) {
        throw 'Installed JAR changed during deployment.'
    }
    [IO.File]::Replace($staged, $installed, [NullString]::Value)
    $published = $true
    if ((Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash -ne $candidateHash) {
        throw 'Installed JAR checksum mismatch.'
    }
    [ordered]@{
        result = 'DEPLOYED'; version = $manifest.Version; revision = $manifest.Metadata.RpgRevision
        installedJar = $installed; sha256 = $candidateHash
        backupJar = $backup; previousSha256 = $oldHash
        connectedAcceptance = 'USER_TEST_PENDING'; nativeServerStarted = $false
        saveMigration = $false
    } | ConvertTo-Json -Depth 4 | Tee-Object -FilePath (Join-Path $evidence 'deployment.json')
} catch {
    if ($published) { Copy-Item -LiteralPath $backup -Destination $installed -Force }
    throw
} finally {
    if (Test-Path -LiteralPath $staged) {
        Move-Item -LiteralPath $staged -Destination (Join-Path $evidence 'unpublished.pending')
    }
}
