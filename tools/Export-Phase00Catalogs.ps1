[CmdletBinding()]
param(
    [string]$AssetsZip = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip",
    [string]$MasterSpecification = "$PSScriptRoot\..\..\Hytale RPG Master Implementation Specification v1.1.docx.md",
    [string]$OutputDirectory = "$PSScriptRoot\..\evidence\phase-00\catalogs",
    [string]$BaselineCatalog = "$PSScriptRoot\..\evidence\phase-00\history\0.6.3\catalogs\asset-catalog.json"
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path -LiteralPath $AssetsZip)) {
    throw "Assets archive not found: $AssetsZip"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$categoryRules = @(
    @{ Name = 'particle-systems';   Prefix = 'Server/Particles/';             Extension = '.particlesystem' },
    @{ Name = 'particle-spawners';  Prefix = 'Server/Particles/';             Extension = '.particlespawner' },
    @{ Name = 'sound-definitions';  Prefix = 'Server/Audio/';                 Extension = '.json' },
    @{ Name = 'items';              Prefix = 'Server/Item/Items/';            Extension = '.json' },
    @{ Name = 'item-animations';    Prefix = 'Server/Item/Animations/';       Extension = '.json' },
    @{ Name = 'character-animations'; Prefix = 'Common/Characters/Animations/'; Extension = '.blockyanim' },
    @{ Name = 'npc-roles';          Prefix = 'Server/NPC/Roles/';             Extension = '.json' },
    @{ Name = 'npc-groups';         Prefix = 'Server/NPC/Groups/';            Extension = '.json' }
)

function Get-EntrySha256([System.IO.Compression.ZipArchiveEntry]$Entry) {
    $stream = $Entry.Open()
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [Convert]::ToHexString($sha.ComputeHash($stream))
    }
    finally {
        $sha.Dispose()
        $stream.Dispose()
    }
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($AssetsZip)
try {
    $allRows = [System.Collections.Generic.List[object]]::new()
    foreach ($rule in $categoryRules) {
        $rows = foreach ($entry in $zip.Entries) {
            if ($entry.Length -eq 0 -or
                -not $entry.FullName.StartsWith($rule.Prefix, [StringComparison]::Ordinal) -or
                [IO.Path]::GetExtension($entry.FullName) -ne $rule.Extension) {
                continue
            }
            $relative = $entry.FullName.Substring($rule.Prefix.Length)
            [pscustomobject]@{
                category = $rule.Name
                id = [IO.Path]::GetFileNameWithoutExtension($entry.FullName)
                relativeId = $relative.Substring(0, $relative.Length - $rule.Extension.Length)
                archivePath = $entry.FullName
                bytes = $entry.Length
                sha256 = Get-EntrySha256 $entry
            }
        }
        $rows = @($rows | Sort-Object relativeId)
        $rows | Export-Csv -LiteralPath (Join-Path $OutputDirectory "$($rule.Name).csv") -NoTypeInformation -Encoding utf8
        foreach ($row in $rows) { $allRows.Add($row) }
    }

    $allRows | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'asset-catalog.json') -Encoding utf8

    $summary = $allRows | Group-Object category | Sort-Object Name | ForEach-Object {
        [pscustomobject]@{ category = $_.Name; count = $_.Count }
    }
    $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'catalog-summary.json') -Encoding utf8

    if (Test-Path -LiteralPath $MasterSpecification) {
        $candidateIds = foreach ($line in Get-Content -LiteralPath $MasterSpecification) {
            if ($line -match 'Candidate SystemIds') {
                $tail = ($line -split ':', 2)[1] -replace '\\_', '_'
                foreach ($candidate in ($tail -split ',')) {
                    $value = $candidate.Trim().Trim('*', '.', ' ')
                    if ($value) { $value }
                }
            }
        }
        $candidateIds = @($candidateIds | Sort-Object -Unique)
        $particleRows = @($allRows | Where-Object category -eq 'particle-systems')
        $baselineParticleRows = @()
        if (Test-Path -LiteralPath $BaselineCatalog) {
            $baselineParticleRows = @(Get-Content -LiteralPath $BaselineCatalog -Raw | ConvertFrom-Json |
                Where-Object category -eq 'particle-systems')
        }
        $crossCheck = foreach ($candidate in $candidateIds) {
            $matches = @($particleRows | Where-Object { $_.id -eq $candidate -or $_.relativeId -eq $candidate })
            $baselineMatches = @($baselineParticleRows | Where-Object { $_.id -eq $candidate -or $_.relativeId -eq $candidate })
            $classification = if ($matches.Count -gt 0) { 'VERIFIED_INSTALLED' }
                elseif ($baselineMatches.Count -gt 0) { 'PUBLIC_OR_HISTORICAL_CANDIDATE' }
                else { 'MISSING' }
            [pscustomobject]@{
                candidateSystemId = $candidate
                exactMatch = $matches.Count -gt 0
                classification = $classification
                matchingArchivePaths = @($matches.archivePath)
                baselineMatchingArchivePaths = @($baselineMatches.archivePath)
            }
        }
        $crossCheck | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'candidate-particle-cross-check.json') -Encoding utf8
    }

    if (Test-Path -LiteralPath $BaselineCatalog) {
        $baselineRows = @(Get-Content -LiteralPath $BaselineCatalog -Raw | ConvertFrom-Json)
        $baselineByKey = @{}
        foreach ($row in $baselineRows) { $baselineByKey["$($row.category)|$($row.relativeId)"] = $row }
        $currentByKey = @{}
        foreach ($row in $allRows) { $currentByKey["$($row.category)|$($row.relativeId)"] = $row }
        $allKeys = @($baselineByKey.Keys + $currentByKey.Keys | Sort-Object -Unique)
        $deltaRows = foreach ($key in $allKeys) {
            $before = $baselineByKey[$key]
            $after = $currentByKey[$key]
            $status = if ($null -eq $before) { 'ADDED' }
                elseif ($null -eq $after) { 'REMOVED' }
                elseif ($before.sha256 -ne $after.sha256) { 'CHANGED' }
                else { 'UNCHANGED' }
            [pscustomobject]@{
                key = $key
                status = $status
                previousSha256 = if ($before) { $before.sha256 } else { $null }
                currentSha256 = if ($after) { $after.sha256 } else { $null }
            }
        }
        $deltaRows | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'catalog-delta-from-0.6.3.json') -Encoding utf8
        $deltaRows | Group-Object status | Sort-Object Name | ForEach-Object {
            [pscustomobject]@{ status = $_.Name; count = $_.Count }
        } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'catalog-delta-summary.json') -Encoding utf8
    }
}
finally {
    $zip.Dispose()
}

Get-ChildItem -LiteralPath $OutputDirectory | Select-Object Name, Length
