[CmdletBinding()]
param(
    [string]$JarPath = '',
    [string]$ArtRoot = '',
    [string]$BackupRoot = '',
    [switch]$CheckOnly,
    [switch]$RestoreLast
)
# Windows PowerShell 5.1 and .NET only. No Java, build tools, network, or save-data access.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
$utf8 = [Text.UTF8Encoding]::new($false)
$lock = $null
$pending = $null

function Assert-Stopped {
    $running = @(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
        ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
    })
    if ($running.Count) { throw 'Close Hytale and its server completely, then run Update RPG Icons again.' }
}
function Bytes-Hash([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '') }
    finally { $sha.Dispose() }
}
function Get-RpgFileHash([string]$LiteralPath, [string]$Algorithm = 'SHA256') {
    $stream = [IO.File]::OpenRead($LiteralPath)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [pscustomobject]@{Hash=([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '')} }
    finally { $sha.Dispose(); $stream.Dispose() }
}
function Entry-Bytes($Entry) {
    if ($Entry.Length -gt 64MB) { throw "Oversized JAR entry: $($Entry.FullName)" }
    $inputStream = $Entry.Open()
    $memory = [IO.MemoryStream]::new()
    try { $inputStream.CopyTo($memory); return ,$memory.ToArray() }
    finally { $inputStream.Dispose(); $memory.Dispose() }
}
function Jar-Hashes([string]$Path) {
    $hashes = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($hashes.ContainsKey($entry.FullName)) { throw "Duplicate JAR entry: $($entry.FullName)" }
            $hashes.Add($entry.FullName, (Bytes-Hash (Entry-Bytes $entry)))
        }
    } finally { $zip.Dispose() }
    return ,$hashes
}
function Read-JsonEntry($Zip, [string]$Path) {
    $entry = $Zip.GetEntry($Path)
    if ($null -eq $entry) { throw "Installed RPG build lacks $Path. Install R032-S or newer first." }
    return ($utf8.GetString((Entry-Bytes $entry)) | ConvertFrom-Json)
}
function Validate-Png([IO.FileInfo]$File) {
    if (($File.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Use a real PNG file, not a link: $($File.Name)"
    }
    if ($File.Length -gt 4MB -or $File.Length -lt 24) { throw "PNG must be no larger than 4 MiB: $($File.Name)" }
    $bytes = [IO.File]::ReadAllBytes($File.FullName)
    if ([BitConverter]::ToString($bytes, 0, 8) -ne '89-50-4E-47-0D-0A-1A-0A') {
        throw "Not a PNG image: $($File.Name). Export PNG; do not just rename another format."
    }
    # Inspect IHDR before decoding so huge dimensions cannot allocate unbounded bitmaps.
    $width = [uint64]$bytes[16] * 16777216 + [uint64]$bytes[17] * 65536 + [uint64]$bytes[18] * 256 + $bytes[19]
    $height = [uint64]$bytes[20] * 16777216 + [uint64]$bytes[21] * 65536 + [uint64]$bytes[22] * 256 + $bytes[23]
    if ($width -ne $height -or $width -lt 16 -or $width -gt 1024) {
        throw "Use a square PNG, 16-1024 px per side (128 recommended): $($File.Name) is $($width)x$($height)."
    }
    $memory = [IO.MemoryStream]::new($bytes, $false)
    $image = $null
    try {
        $image = [Drawing.Image]::FromStream($memory, $true, $true)
        if ($image.RawFormat.Guid -ne [Drawing.Imaging.ImageFormat]::Png.Guid -or
            $image.Width -ne $width -or $image.Height -ne $height) { throw "Invalid PNG: $($File.Name)" }
    } finally { if ($null -ne $image) { $image.Dispose() }; $memory.Dispose() }
    return ,$bytes
}

try {
    if ($CheckOnly -and $RestoreLast) { throw 'Choose CheckOnly or RestoreLast, not both.' }
    # Windows PowerShell initializes PSScriptRoot after default parameter expressions.
    $repository = Split-Path $PSScriptRoot -Parent
    if ([string]::IsNullOrWhiteSpace($ArtRoot)) { $ArtRoot = Join-Path $repository 'art' }
    if ([string]::IsNullOrWhiteSpace($BackupRoot)) { $BackupRoot = Join-Path $repository 'icon-backups' }
    if ([string]::IsNullOrWhiteSpace($JarPath)) {
        $mods = Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG\mods'
        $installed = @(Get-ChildItem -LiteralPath $mods -File | Where-Object { $_.Name -eq 'HyARPG.jar' -or $_.Name -like 'HytaleRPG-*.jar' })
        if ($installed.Count -ne 1) { throw 'Expected exactly one HyARPG.jar (or legacy HytaleRPG-*.jar) in the RPG world mods folder. Remove version ambiguity first.' }
        $JarPath = $installed[0].FullName
    }
    $JarPath = (Resolve-Path -LiteralPath $JarPath).Path
    if ([IO.Path]::GetExtension($JarPath) -ne '.jar') { throw 'Target must be an existing RPG .jar file.' }
    $BackupRoot = [IO.Path]::GetFullPath($BackupRoot)
    $lastPath = Join-Path $BackupRoot 'last-update.json'
    if (-not $CheckOnly) {
        Assert-Stopped
        # Lifetime lock excludes a second updater, even with a different backup directory.
        $lock = [IO.File]::Open($JarPath + '.icons.lock', [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    }
    $beforeHash = (Get-RpgFileHash -LiteralPath $JarPath -Algorithm SHA256).Hash
    if ($RestoreLast) {
        $last = Get-Content -Raw -LiteralPath $lastPath | ConvertFrom-Json
        if ($last.target -ne $JarPath -or $last.afterSha256 -ne $beforeHash -or
            $last.beforeSha256 -notmatch '^[A-F0-9]{64}$') {
            throw 'Undo refused: the installed JAR changed after the last icon update (possibly a newer RPG build).'
        }
        $backup = Join-Path $BackupRoot ("HytaleRPG-before-" + $last.beforeSha256 + '.jar')
        if ((Get-RpgFileHash -LiteralPath $backup).Hash -ne $last.beforeSha256) { throw 'Undo backup hash mismatch.' }
        $pending = $JarPath + '.icons-' + [Guid]::NewGuid().ToString('N') + '.pending'
        Copy-Item -LiteralPath $backup -Destination $pending
        if ((Get-RpgFileHash -LiteralPath $pending).Hash -ne $last.beforeSha256) { throw 'Undo staging hash mismatch.' }
        Assert-Stopped
        if ((Get-RpgFileHash -LiteralPath $JarPath).Hash -ne $beforeHash) { throw 'Installed JAR changed during undo.' }
        [IO.File]::Replace($pending, $JarPath, [NullString]::Value)
        $pending = $null
        if ((Get-RpgFileHash -LiteralPath $JarPath).Hash -ne $last.beforeSha256) { throw 'Undo verification failed; inspect backup before launching.' }
        Write-Output 'SUCCESS: restored the build from before your last icon update. No saves changed. Restart Hytale.'
        exit 0
    }

    $ArtRoot = (Resolve-Path -LiteralPath $ArtRoot).Path
    $replacements = [Collections.Generic.Dictionary[string,byte[]]]::new([StringComparer]::Ordinal)
    $selected = [Collections.Generic.List[object]]::new()
    $beforeEntries = Jar-Hashes $JarPath
    $zip = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $manifest = Read-JsonEntry $zip 'manifest.json'
        if ($manifest.Group -ne 'InigmasGames' -or $manifest.Name -ne 'HytaleRPGPhase00Audit') { throw 'Target is not the RPG mod.' }
        $index = Read-JsonEntry $zip 'rpg/presentation/icon-index.json'
        if ($index.schemaVersion -ne 1 -or @($index.entries).Count -notin @(153,156)) { throw 'Unsupported icon index; do not patch this build.' }
        $byFile = @{}
        $ids = @{}
        $catalogIds = @{}
        foreach ($kind in @('Skill','Passive')) {
            $catalog = if ($kind -eq 'Skill') { 'skills' } else { 'passives' }
            foreach ($record in (Read-JsonEntry $zip "rpg/catalog/$catalog.json")) { $catalogIds["$($kind):$($record.id)"] = $true }
        }
        foreach ($row in $index.entries) {
            if ($row.kind -notin @('Skill','Passive') -or $row.id -notmatch '^[a-z0-9_]+$' -or
                $row.fileName -cnotmatch ('^' + $row.kind + '[A-Z][A-Za-z0-9]*\.png$')) { throw 'Unsafe icon index entry.' }
            $key = "$($row.kind):$($row.id)"
            if ($ids.ContainsKey($key) -or -not $catalogIds.ContainsKey($key)) { throw "Duplicate/unknown icon ID: $key" }
            $ids[$key] = $true
            if ($byFile.ContainsKey($row.fileName)) { throw 'Ambiguous icon filename.' }
            if ($row.kind -eq 'Skill' -and ($row.itemAsset -cnotmatch '^Server/Item/Items/RPG/Abilities/RPG_Ability_[A-Za-z0-9_]+\.json$' -or
                $null -eq $zip.GetEntry($row.itemAsset))) { throw 'Invalid native skill icon target.' }
            if ($row.kind -eq 'Skill' -and
                ([IO.Path]::GetFileNameWithoutExtension($row.itemAsset) -replace '^RPG_Ability_', '').ToLowerInvariant() -cne $row.id) {
                throw 'Native item target does not match canonical skill ID.'
            }
            $byFile[$row.fileName] = $row
        }
        $skillCount=@($index.entries | Where-Object kind -eq 'Skill').Count
        $passiveCount=@($index.entries | Where-Object kind -eq 'Passive').Count
        if ($ids.Count -ne $catalogIds.Count -or -not (($skillCount -eq 87 -and $passiveCount -eq 66) -or
            ($skillCount -eq 89 -and $passiveCount -eq 67))) { throw 'Icon index does not cover the full catalog.' }
        foreach ($kind in @('Skill','Passive')) {
            $folder = Join-Path $ArtRoot ($kind + 's')
            if (-not (Test-Path -LiteralPath $folder -PathType Container)) { continue }
            foreach ($file in Get-ChildItem -LiteralPath $folder -File -Filter '*.png') {
                $row = $byFile[$file.Name]
                if ($null -eq $row -or $row.kind -ne $kind) {
                    throw "Unknown $kind PNG '$($file.Name)'. See art/ICON-FILENAMES.csv for the exact filename."
                }
                $bytes = Validate-Png $file
                $uiEntry = 'Common/UI/Custom/Icons/RPG/' + $row.fileName
                if ($replacements.ContainsKey($uiEntry)) { throw "Duplicate icon: $($row.fileName)" }
                $replacements.Add($uiEntry, $bytes)
                if ($kind -eq 'Skill') {
                    $nativeIcon = 'Icons/Items/RPG/' + $row.fileName
                    $replacements.Add('Common/' + $nativeIcon, $bytes)
                    $item = Read-JsonEntry $zip $row.itemAsset
                    # Repeated updates must be exact no-ops, including Item JSON formatting.
                    if ($item.Icon -cne $nativeIcon) {
                        $item.Icon = $nativeIcon
                        $replacements.Add($row.itemAsset, $utf8.GetBytes(($item | ConvertTo-Json -Depth 64)))
                    }
                }
                $selected.Add([pscustomobject]@{name=$row.name;fileName=$row.fileName;sha256=(Bytes-Hash $bytes)})
            }
        }
    } finally { $zip.Dispose() }
    $changed = @($replacements.Keys | Where-Object {
        -not $beforeEntries.ContainsKey($_) -or $beforeEntries[$_] -ne (Bytes-Hash $replacements[$_])
    })
    foreach ($row in $selected) { Write-Output ("{0} <- {1}" -f $row.name, $row.fileName) }
    if ($CheckOnly) { Write-Output "CHECK PASSED: $($selected.Count) supplied icons, $($changed.Count) JAR entries would change. Nothing written."; exit 0 }
    if (-not $changed.Count) { Write-Output 'Already up to date. No JAR or saves changed.'; exit 0 }

    $pending = $JarPath + '.icons-' + [Guid]::NewGuid().ToString('N') + '.pending'
    # Recreate rather than Update: .NET Framework's ZIP updater can corrupt Java's
    # empty deflated directory entries. Verify every uncompressed entry afterward.
    $source = [IO.Compression.ZipFile]::OpenRead($JarPath)
    $stream = [IO.File]::Open($pending, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $candidate = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $true)
        try {
            $names = @($beforeEntries.Keys) + @($changed | Where-Object { -not $beforeEntries.ContainsKey($_) })
            foreach ($entryName in $names) {
                $entry = $candidate.CreateEntry($entryName, [IO.Compression.CompressionLevel]::Optimal)
                $output = $entry.Open()
                try {
                    if ($changed -ccontains $entryName) {
                        $bytes = $replacements[$entryName]
                        $output.Write($bytes, 0, $bytes.Length)
                    } else {
                        $inputStream = $source.GetEntry($entryName).Open()
                        try { $inputStream.CopyTo($output) } finally { $inputStream.Dispose() }
                    }
                }
                finally { $output.Dispose() }
            }
        } finally { $candidate.Dispose() }
        $stream.Flush($true)
    } finally { $stream.Dispose(); $source.Dispose() }
    $afterEntries = Jar-Hashes $pending
    foreach ($name in $beforeEntries.Keys) {
        $expected = if ($changed -ccontains $name) { Bytes-Hash $replacements[$name] } else { $beforeEntries[$name] }
        if (-not $afterEntries.ContainsKey($name) -or $afterEntries[$name] -ne $expected) {
            throw "Unexpected JAR content change: $name expected=$expected actual=$($afterEntries[$name]) present=$($afterEntries.ContainsKey($name))"
        }
    }
    foreach ($name in $afterEntries.Keys) {
        if (-not $beforeEntries.ContainsKey($name) -and -not ($changed -ccontains $name)) { throw "Unexpected new JAR entry: $name" }
    }
    foreach ($name in $changed) {
        if ($afterEntries[$name] -ne (Bytes-Hash $replacements[$name])) { throw "Staged icon hash mismatch: $name" }
    }
    $afterHash = (Get-RpgFileHash -LiteralPath $pending).Hash
    New-Item -ItemType Directory -Path $BackupRoot -Force | Out-Null
    $backup = Join-Path $BackupRoot ("HytaleRPG-before-" + $beforeHash + '.jar')
    if (-not (Test-Path -LiteralPath $backup)) { Copy-Item -LiteralPath $JarPath -Destination $backup }
    if ((Get-RpgFileHash -LiteralPath $backup).Hash -ne $beforeHash) { throw 'Backup integrity check failed.' }
    Assert-Stopped
    if ((Get-RpgFileHash -LiteralPath $JarPath).Hash -ne $beforeHash) { throw 'Installed JAR changed during update; refusing overwrite.' }
    [IO.File]::Replace($pending, $JarPath, [NullString]::Value)
    $pending = $null
    if ((Get-RpgFileHash -LiteralPath $JarPath).Hash -ne $afterHash) { throw 'Deployment hash mismatch; inspect backup before launching.' }
    $receipt = [ordered]@{schemaVersion=1;target=$JarPath;beforeSha256=$beforeHash;afterSha256=$afterHash;
        backup=$backup;icons=@($selected);changedEntries=$changed;utc=[DateTime]::UtcNow.ToString('o');savesModified=$false}
    $json = $receipt | ConvertTo-Json -Depth 8
    [IO.File]::WriteAllText((Join-Path $BackupRoot ("update-" + [Guid]::NewGuid().ToString('N') + '.json')), $json, $utf8)
    [IO.File]::WriteAllText($lastPath, $json, $utf8)
    Write-Output "SUCCESS: installed $($selected.Count) icons. No gameplay code or saves changed."
    Write-Output "Backup: $backup"
    Write-Output "Installed SHA256: $afterHash"
    Write-Output 'Restart Hytale and rejoin RPG. Then check /rpg skilltree and native skill slots.'
} catch {
    Write-Error $_
    exit 1
} finally {
    if ($null -ne $pending -and (Test-Path -LiteralPath $pending)) { Remove-Item -LiteralPath $pending -Force }
    if ($null -ne $lock) { $lock.Dispose() }
}
