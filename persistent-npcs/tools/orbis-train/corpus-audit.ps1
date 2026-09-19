param(
    [Parameter(Mandatory = $true)][string]$CandidateJsonl
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $CandidateJsonl -PathType Leaf)) {
    throw "Candidate JSONL not found: $CandidateJsonl"
}
$counts = @{}
$lineNumber = 0
Get-Content -LiteralPath $CandidateJsonl | ForEach-Object {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($_)) { return }
    $entry = $_ | ConvertFrom-Json
    if ($entry.schemaVersion -ne 1 -or -not $entry.id -or -not $entry.contentHash) {
        throw "Invalid append-only envelope at line $lineNumber"
    }
    $candidate = $entry.payload
    if (-not $candidate.productionInput.providerInputSha256 -or
            -not $candidate.eligibility.eligibility -or -not $candidate.provenance) {
        throw "Incomplete candidate evidence at line $lineNumber"
    }
    if ($candidate.PSObject.Properties.Name -contains 'targetOutput') {
        throw "D2 candidate must not contain a teacher/gold target at line $lineNumber"
    }
    $key = [string]$candidate.eligibility.eligibility
    $counts[$key] = 1 + [int]($counts[$key] ?? 0)
}
[pscustomobject]@{ schemaVersion = 1; rows = $lineNumber; eligibility = $counts } |
    ConvertTo-Json -Depth 5
