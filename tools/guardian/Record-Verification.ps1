[CmdletBinding()]
param(
    [string]$Repository = '.',
    [string]$CheckLogPath = 'build/guardian/gradle-check.log',
    [string]$JarPath = 'build/libs/HyARPG.jar',
    [switch]$FixtureMode
)

$ErrorActionPreference = 'Stop'
$root = (& git -C $Repository rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Not a Git worktree.' }
$root = [IO.Path]::GetFullPath($root)
function Repo-File([string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or $Relative -match '(^|[/\\])\.\.([/\\]|$)') {
        throw 'Validation evidence must stay inside the repository.'
    }
    $full = [IO.Path]::GetFullPath((Join-Path $root $Relative))
    $prefix = $root.TrimEnd([char[]]@('/', '\')) + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Evidence path escapes repository.' }
    $current = $root
    foreach ($part in ($Relative -split '[/\\]' | Where-Object { $_ })) {
        $current = Join-Path $current $part
        if (Test-Path -LiteralPath $current) {
            if (((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Refusing evidence through a symlink or junction.'
            }
        }
    }
    return $full
}
$guard = Join-Path $PSScriptRoot 'Invoke-Guardian.ps1'
$preflight = @(& $guard -Mode Preflight -Repository $root -FixtureMode:$FixtureMode)
if ($LASTEXITCODE -ne 0 -or $preflight.Count -ne 1 -or $preflight[0] -ne 'GUARDIAN: PASS') {
    throw ('Record verification only from a clean, current checkout: ' + ($preflight -join ' '))
}
$head = (& git -C $root rev-parse HEAD).Trim()
$commitTime = (& git -C $root show -s --format=%cI HEAD).Trim()
$properties = Get-Content -LiteralPath (Repo-File 'gradle.properties') -Raw
$revision = [regex]::Match($properties, '(?m)^rpg_revision=([^\r\n]+)').Groups[1].Value.Trim()
$version = [regex]::Match($properties, '(?m)^version=([^\r\n]+)').Groups[1].Value.Trim()
$checkLog = Repo-File $CheckLogPath
$jar = Repo-File $JarPath
if (-not (Test-Path -LiteralPath $checkLog -PathType Leaf) -or
    -not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Gradle check log or built JAR is missing.' }
if (((Get-Content -LiteralPath $checkLog -Tail 30) -join ' ') -notmatch 'BUILD SUCCESSFUL') {
    throw 'Gradle check log does not record BUILD SUCCESSFUL.'
}
if ((Get-Item -LiteralPath $checkLog).LastWriteTimeUtc -lt [DateTimeOffset]::Parse($commitTime).UtcDateTime) {
    throw 'Gradle check log predates the committed source.'
}

$reports = New-Object 'System.Collections.Generic.List[object]'
$reportDirs = @('build/test-results/test', 'build/test-results/nativeControlTest',
                'canvas-ui/build/test-results/test', 'hytale-taverns/build/test-results/test')
foreach ($relativeDir in $reportDirs) {
    $folder = Repo-File $relativeDir
    $files = @(Get-ChildItem -LiteralPath $folder -Filter 'TEST-*.xml' -File -ErrorAction Stop)
    if ($files.Count -eq 0) { throw "No JUnit reports in $relativeDir" }
    $moduleTests = 0
    foreach ($file in $files) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
        $suite = $xml.DocumentElement
        if ($suite.LocalName -ne 'testsuite') { throw "Unexpected JUnit report: $($file.FullName)" }
        $tests = [int]$suite.GetAttribute('tests')
        $failures = [int]$suite.GetAttribute('failures')
        $errors = [int]$suite.GetAttribute('errors')
        $skipped = [int]$suite.GetAttribute('skipped')
        if ($failures -gt 0 -or $errors -gt 0 -or $skipped -gt 0) {
            throw "JUnit failures/errors/skips in $($file.FullName)"
        }
        $moduleTests += $tests
        $reports.Add([ordered]@{
            path = ($relativeDir.Replace('\', '/') + '/' + $file.Name)
            sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
            tests = $tests
        })
    }
    if ($moduleTests -eq 0) { throw "No executed tests in $relativeDir" }
}

$validator = Repo-File 'tools/Test-HyArpgPackage.ps1'
$package = & $validator -JarPath $jar -ExpectedVersion $version -ExpectedRevision $revision | ConvertFrom-Json
if ($package.result -ne 'PASS') { throw 'Offline package validation failed.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    $entry = $zip.GetEntry('rpg-build.properties')
    if ($null -eq $entry) { throw 'Built JAR lacks rpg-build.properties.' }
    $reader = New-Object IO.StreamReader($entry.Open())
    try { $built = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($built -notmatch ('(?m)^rpg\.sourceCommit=' + [regex]::Escape($head) + '\r?$')) {
        throw 'Built JAR source commit differs from HEAD.'
    }
} finally { $zip.Dispose() }
$jarHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
if ($package.sha256 -ne $jarHash) { throw 'Package validator JAR hash differs.' }
if ((& git -C $root rev-parse HEAD).Trim() -ne $head -or
    @(& git -C $root status --porcelain=v1 --untracked-files=all).Count -gt 0) {
    throw 'Checkout changed during validation; no completion receipt was written.'
}

$receipt = [ordered]@{
    sourceCommit = $head
    revision = $revision
    version = $version
    gradleCheck = 'PASS'
    packageValidation = 'PASS'
    checkLogPath = $CheckLogPath.Replace('\', '/')
    checkLogSha256 = (Get-FileHash -LiteralPath $checkLog -Algorithm SHA256).Hash
    jarPath = $JarPath.Replace('\', '/')
    jarSha256 = $jarHash
    reports = @($reports.ToArray())
    validatedAtUtc = [DateTime]::UtcNow.ToString('o')
}
$receiptPath = Repo-File 'build/guardian/verification.json'
[IO.File]::WriteAllText($receiptPath, ($receipt | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
Write-Output 'GUARDIAN: PASS'
