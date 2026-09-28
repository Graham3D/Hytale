param(
    [string]$SaveName = 'ImmersiveNPCs',
    [string]$ModsDirectory,
    [string]$RollbackRoot = 'C:\HytaleRollback'
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$artifactName = 'ImmersiveNPCs-0.6.4-R171-PRE4-COMPAT.jar'
$sourceJar = Join-Path $projectRoot (Join-Path 'dist' $artifactName)
if (-not (Test-Path -LiteralPath $sourceJar)) {
    & (Join-Path $projectRoot 'build.ps1')
}

if ([string]::IsNullOrWhiteSpace($ModsDirectory)) {
    $ModsDirectory = Join-Path $env:APPDATA "Hytale\data\pre-release\Saves\$SaveName\mods"
}

New-Item -ItemType Directory -Force -Path $ModsDirectory | Out-Null
$resolvedMods = [IO.Path]::GetFullPath($ModsDirectory)
$legacyData = [IO.Path]::GetFullPath((Join-Path $resolvedMods 'InigmasGames_PersistentNPCs'))
$authoritativeData = [IO.Path]::GetFullPath((Join-Path $resolvedMods 'ImmersiveNPCs'))
if (-not $authoritativeData.StartsWith($resolvedMods, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe ImmersiveNPCs data path: $authoritativeData"
}
New-Item -ItemType Directory -Force -Path $authoritativeData | Out-Null
if (Test-Path -LiteralPath $legacyData -PathType Container) {
    $copiedLegacyFiles = 0
    $preservedAuthoritativeFiles = 0
    Get-ChildItem -LiteralPath $legacyData -Recurse -File | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($legacyData, $_.FullName)
        $target = [IO.Path]::GetFullPath((Join-Path $authoritativeData $relative))
        if (-not $target.StartsWith($authoritativeData, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Unsafe legacy migration target: $target"
        }
        if (-not (Test-Path -LiteralPath $target)) {
            New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
            Copy-Item -LiteralPath $_.FullName -Destination $target
            $copiedLegacyFiles++
        } else {
            $preservedAuthoritativeFiles++
        }
    }
    $saveRoot = [IO.Path]::GetDirectoryName($resolvedMods)
    $legacyBackupRoot = [IO.Path]::GetFullPath((Join-Path $saveRoot 'ImmersiveNPCs-Legacy-Backups'))
    if (-not $legacyBackupRoot.StartsWith($saveRoot, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetDirectoryName($legacyBackupRoot) -ne $saveRoot) {
        throw "Unsafe legacy backup root: $legacyBackupRoot"
    }
    New-Item -ItemType Directory -Force -Path $legacyBackupRoot | Out-Null
    $archiveBase = 'InigmasGames_PersistentNPCs_' + (Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')
    $legacyArchive = Join-Path $legacyBackupRoot $archiveBase
    $archiveSuffix = 2
    while (Test-Path -LiteralPath $legacyArchive) {
        $legacyArchive = Join-Path $legacyBackupRoot ($archiveBase + '_' + $archiveSuffix)
        $archiveSuffix++
    }
    $resolvedArchive = [IO.Path]::GetFullPath($legacyArchive)
    if ([IO.Path]::GetDirectoryName($resolvedArchive) -ne $legacyBackupRoot) {
        throw "Unsafe legacy archive destination: $resolvedArchive"
    }
    Move-Item -LiteralPath $legacyData -Destination $resolvedArchive
    Write-Host "Migrated $copiedLegacyFiles missing legacy file(s) into $authoritativeData; preserved $preservedAuthoritativeFiles authoritative file(s)."
    Write-Host "Archived obsolete legacy data outside mods at $resolvedArchive"
}

# R037 global runtime LLM selection. This file is deliberately outside every NPC profile,
# so switching providers cannot modify identity, memory, relationships, or cognition state.
$llmProvidersPath = Join-Path $authoritativeData 'llm-providers.json'
$llmDefaultsPath = Join-Path $projectRoot 'src\main\resources\defaults\llm-providers.json'
if (-not (Test-Path -LiteralPath $llmProvidersPath -PathType Leaf)) {
    Copy-Item -LiteralPath $llmDefaultsPath -Destination $llmProvidersPath
} else {
    try {
        $llmProviders = Get-Content -LiteralPath $llmProvidersPath -Raw | ConvertFrom-Json
        $llmProviders.activeProvider = 'NEMOTRON'
        if ($null -ne $llmProviders.providers) {
            $llmProviders.providers.PSObject.Properties.Remove('ORBIS_LLAMA_CPP_NEMOTRON')
        }
        if ($null -ne $llmProviders.providers.QWEN) {
            $llmProviders.providers.QWEN | Add-Member -NotePropertyName toolChoiceMode `
                -NotePropertyValue 'REQUIRED' -Force
        }
        if ($null -ne $llmProviders.providers.NEMOTRON) {
            $llmProviders.providers.NEMOTRON | Add-Member -NotePropertyName toolChoiceMode `
                -NotePropertyValue 'NAMED_SINGLE' -Force
            $llmProviders.providers.NEMOTRON | Add-Member -NotePropertyName ollamaGpuLayers `
                -NotePropertyValue 4 -Force
        }
        $llmProviders | ConvertTo-Json -Depth 20 |
            Set-Content -LiteralPath $llmProvidersPath -Encoding UTF8
    } catch {
        throw "Could not select NEMOTRON in '$llmProvidersPath': $($_.Exception.Message)"
    }
}
Write-Host "Selected NEMOTRON in $llmProvidersPath"

foreach ($defaultName in 'ai-providers.json','latency-budgets.json') {
    $defaultTarget = Join-Path $authoritativeData $defaultName
    if (-not (Test-Path -LiteralPath $defaultTarget -PathType Leaf)) {
        Copy-Item -LiteralPath (Join-Path $projectRoot "src\main\resources\defaults\$defaultName") `
            -Destination $defaultTarget
        Write-Host "Installed modern ImmersiveNPCs default $defaultName at $defaultTarget"
    }
}

$orbisResourcesPath = Join-Path $authoritativeData 'orbis-resources.json'
if (-not (Test-Path -LiteralPath $orbisResourcesPath -PathType Leaf)) {
    Copy-Item -LiteralPath (Join-Path $projectRoot 'src\main\resources\defaults\orbis-resources.json') `
        -Destination $orbisResourcesPath
    Write-Host "Installed conservative BALANCED Orbis resource policy at $orbisResourcesPath"
}

# R039 named-profile layout. Copy only; never remove legacy authored sources.
$profilesDirectory = Join-Path $authoritativeData 'profiles'
New-Item -ItemType Directory -Force -Path $profilesDirectory | Out-Null
Get-ChildItem -LiteralPath $profilesDirectory -File -Filter '*.json' -ErrorAction SilentlyContinue |
    ForEach-Object {
        try {
            $profileData = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
            $profileName = [string]$profileData.name
            if (-not [string]::IsNullOrWhiteSpace($profileName) -and
                    $profileName -notmatch '[<>:"/\\|?*]' -and $profileName -notin '.', '..') {
                $profileDirectory = Join-Path $profilesDirectory $profileName
                $canonicalProfile = Join-Path $profileDirectory ($profileName + '.json')
                New-Item -ItemType Directory -Force -Path $profileDirectory | Out-Null
                if (-not (Test-Path -LiteralPath $canonicalProfile)) {
                    Copy-Item -LiteralPath $_.FullName -Destination $canonicalProfile
                }
                $saveRoot = [IO.Path]::GetDirectoryName($resolvedMods)
                $legacyVoice = Join-Path $saveRoot ("exports\voices\" + $profileName)
                foreach ($voiceName in 'reference.wav','sample-calm.wav','sample-curious.wav','sample-excited.wav','sample-uneasy.wav','sample-angry.wav','sample-sad.wav','sample-tender.wav','sample-amused.wav') {
                    $voiceSource = Join-Path $legacyVoice $voiceName
                    $voiceTarget = Join-Path $profileDirectory $voiceName
                    if ((Test-Path -LiteralPath $voiceSource -PathType Leaf) -and
                            -not (Test-Path -LiteralPath $voiceTarget)) {
                        Copy-Item -LiteralPath $voiceSource -Destination $voiceTarget
                    }
                }
                $skinSource = Join-Path $saveRoot ("exports\skins\$profileName\SS_SKIN_$profileName.json")
                $skinTarget = Join-Path $profileDirectory 'SS_Skin_Character.json'
                if ((Test-Path -LiteralPath $skinSource -PathType Leaf) -and
                        -not (Test-Path -LiteralPath $skinTarget)) {
                    Copy-Item -LiteralPath $skinSource -Destination $skinTarget
                }
            }
        } catch {
            Write-Warning "Skipped invalid legacy profile '$($_.FullName)': $($_.Exception.Message)"
        }
    }

# Older releases may have only profiles\<slug>\profile.json. Seed the authoritative named
# document when it is absent while retaining every legacy source file.
Get-ChildItem -LiteralPath $profilesDirectory -Directory -ErrorAction SilentlyContinue |
    ForEach-Object {
        $profileDirectory = $_.FullName
        $canonicalProfile = Join-Path $profileDirectory ($_.Name + '.json')
        if (-not (Test-Path -LiteralPath $canonicalProfile -PathType Leaf)) {
            $candidates = @(Get-ChildItem -LiteralPath $profileDirectory -File -Filter '*.json' |
                Where-Object { $_.Name -ne 'preset.json' -and $_.Name -notlike 'SS_*' } |
                Sort-Object @{ Expression = { if ($_.Name -ieq 'profile.json') { 0 } else { 1 } } })
            foreach ($candidate in $candidates) {
                try {
                    $profileData = Get-Content -LiteralPath $candidate.FullName -Raw | ConvertFrom-Json
                    $profileName = [string]$profileData.name
                    if (-not [string]::IsNullOrWhiteSpace($profileName) -and
                            $profileName -notmatch '[<>:"/\\|?*]' -and $profileName -notin '.', '..') {
                        Copy-Item -LiteralPath $candidate.FullName -Destination $canonicalProfile
                        break
                    }
                } catch {
                    Write-Warning "Skipped invalid grouped profile '$($candidate.FullName)': $($_.Exception.Message)"
                }
            }
        }
        $canonicalSkin = Join-Path $profileDirectory 'SS_Skin_Character.json'
        $canonicalTender = Join-Path $profileDirectory 'sample-tender.wav'
        $legacyTender = Join-Path $profileDirectory 'sample-affectionate.wav'
        if ((Test-Path -LiteralPath $legacyTender -PathType Leaf) -and
                -not (Test-Path -LiteralPath $canonicalTender)) {
            Copy-Item -LiteralPath $legacyTender -Destination $canonicalTender
        }
        if (-not (Test-Path -LiteralPath $canonicalSkin -PathType Leaf)) {
            $legacySkin = Get-ChildItem -LiteralPath $profileDirectory -File -Filter 'SS_SKIN_*.json' |
                Select-Object -First 1
            if ($null -ne $legacySkin) {
                Copy-Item -LiteralPath $legacySkin.FullName -Destination $canonicalSkin
            }
        }
    }

$destination = Join-Path $ModsDirectory $artifactName
$resolvedDestination = [IO.Path]::GetFullPath($destination)
$stagedDestination = $resolvedDestination + '.installing'
Copy-Item -LiteralPath $sourceJar -Destination $stagedDestination -Force
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $sourceJar).Hash -cne
        (Get-FileHash -Algorithm SHA256 -LiteralPath $stagedDestination).Hash) {
    throw 'Staged ImmersiveNPCs artifact hash does not match the build output.'
}

$priorBuilds = @(Get-ChildItem -LiteralPath $resolvedMods -File |
    Where-Object { $_.Name -like 'PersistentNPCs-*.jar' -or $_.Name -like 'ImmersiveNPCs-*.jar' } |
    Where-Object { $_.FullName -ne $resolvedDestination })
if (Test-Path -LiteralPath $resolvedDestination -PathType Leaf) {
    $priorBuilds += Get-Item -LiteralPath $resolvedDestination
}
if ($priorBuilds.Count -gt 0) {
    New-Item -ItemType Directory -Force -Path $RollbackRoot | Out-Null
    $rollbackDirectory = Join-Path $RollbackRoot `
        ('ImmersiveNPCs-Checkpoint-B-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Force -Path $rollbackDirectory | Out-Null
    foreach ($priorBuild in $priorBuilds) {
        Move-Item -LiteralPath $priorBuild.FullName `
            -Destination (Join-Path $rollbackDirectory $priorBuild.Name)
    }
    Write-Host "Archived prior ImmersiveNPCs JAR(s) at $rollbackDirectory"
}
Move-Item -LiteralPath $stagedDestination -Destination $resolvedDestination -Force
Write-Host "Installed $destination"
Write-Host 'Restart the world/local server before testing Java or configuration changes.'
