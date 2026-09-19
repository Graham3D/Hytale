param(
    [Parameter(Mandatory = $true)][string]$ImportJsonl
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ImportJsonl -PathType Leaf)) {
    throw "Teacher import JSONL not found: $ImportJsonl"
}
$prohibited = @('reasoning', 'chainOfThought', 'chain_of_thought',
    'hiddenReasoning', 'hidden_reasoning')
$seen = @{}
$accepted = 0
$lineNumber = 0
Get-Content -LiteralPath $ImportJsonl | ForEach-Object {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($_)) { return }
    $raw = $_
    $entry = $raw | ConvertFrom-Json
    if (-not $entry.candidateId -or -not $entry.response.requestId) {
        throw "Candidate and request identity required at line $lineNumber"
    }
    foreach ($name in $prohibited) {
        if ($raw -match ('"' + [regex]::Escape($name) + '"\s*:')) {
            throw "Hidden-reasoning field '$name' is prohibited at line $lineNumber"
        }
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($entry.response | ConvertTo-Json -Compress -Depth 20))
    $hash = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
    if ($seen.ContainsKey($hash)) { throw "Duplicate teacher response at line $lineNumber" }
    $seen[$hash] = $true
    $accepted++
}
[pscustomobject]@{
    schemaVersion = 1
    accepted = $accepted
    trust = 'PROPOSED_LABEL'
    persistedHiddenReasoning = $false
    note = 'Audit only; Java ReviewedTeacherImport performs authoritative import/quarantine.'
} | ConvertTo-Json
