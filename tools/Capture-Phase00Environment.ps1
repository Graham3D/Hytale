[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$hytaleRoot = "$env:APPDATA\Hytale"
$package = Join-Path $hytaleRoot 'install\pre-release\package\game\latest'
$server = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$client = Join-Path $package 'Client\HytaleClient.exe'
$htdev = Join-Path $hytaleRoot 'UserData\Saves\RPG\mods\HYTALEDEVLIB-0.5.0.jar'
$spec = (Resolve-Path (Join-Path $projectRoot '..\Hytale RPG Master Implementation Specification v1.1.docx.md')).Path
$serverManifest = Get-Content -LiteralPath (Join-Path $projectRoot 'evidence\phase-00\api\server-manifest.txt')

function File-Fingerprint([string]$Path) {
    $item = Get-Item -LiteralPath $Path
    [ordered]@{ path = $item.FullName; bytes = $item.Length; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash }
}
function Manifest-Value([string]$Name) {
    $line = $serverManifest | Where-Object { $_ -like "$Name`:*" } | Select-Object -First 1
    if ($line) { return ($line -split ':', 2)[1].Trim() }
    return $null
}

$javaVersion = ((& java -version 2>&1) -join ' ')
$javacVersion = ((& javac -version 2>&1) -join ' ')
$gradleVersion = ((& (Join-Path $projectRoot 'gradlew.bat') --version 2>&1) -join "`n")
$environment = [ordered]@{
    schemaVersion = 2
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    patchline = 'pre-release'
    repository = [ordered]@{
        path = $projectRoot
        branch = (& git -C $projectRoot branch --show-current).Trim()
        commit = (& git -C $projectRoot rev-parse HEAD).Trim()
        originalEvidenceCommit = '39924ecbf6e62fd6a16b87ab3143712f3a3fb43d'
        originalEvidenceBranch = 'phase-00/evidence-feasibility'
        workingTreeDirty = [bool](& git -C $projectRoot status --porcelain)
    }
    hytale = [ordered]@{
        version = (Manifest-Value 'Implementation-Version')
        revision = (Manifest-Value 'Implementation-Revision-Id')
        branch = (Manifest-Value 'Implementation-Branch')
        serverJar = File-Fingerprint $server
        assets = File-Fingerprint $assets
        client = File-Fingerprint $client
        sdk = [ordered]@{ source = 'installed pre-release HytaleServer.jar public Java API'; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $server).Hash }
    }
    toolchain = [ordered]@{
        java = $javaVersion
        javac = $javacVersion
        gradle = if ($gradleVersion -match 'Gradle ([^\s]+)') { $Matches[1] } else { 'unknown' }
    }
    masterSpecification = File-Fingerprint $spec
    htDevLib = [ordered]@{
        installedJar = $htdev
        version = '0.5.0'
        declaredServerVersion = '2026.02.17-255364b8e'
        fingerprint = File-Fingerprint $htdev
        sourceCommit = 'ac955f742da4fc174985ef7b36eac2f49a97b6ef'
        serverSmokeLoadOn070pre1 = $true
        compatibilityNote = 'Legacy pre-semver target logs the expected wildcard warning; setup and enable markers pass.'
    }
    dependencies = @(
        [ordered]@{ name = 'HytaleServer'; version = (Manifest-Value 'Implementation-Version'); scope = 'compileOnly'; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $server).Hash },
        [ordered]@{ name = 'HTDevLib'; version = '0.5.0'; scope = 'compileOnly/runtime save mod'; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $htdev).Hash }
    )
}
$environment | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $projectRoot 'evidence\phase-00\environment.json') -Encoding utf8
$environment | ConvertTo-Json -Depth 3
