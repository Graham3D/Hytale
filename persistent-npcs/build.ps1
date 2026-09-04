param(
    [string]$ServerJar = "$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar"
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$bundledJdk = Join-Path $projectRoot '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin'
$javac = Join-Path $bundledJdk 'javac.exe'
$jarTool = Join-Path $bundledJdk 'jar.exe'

if (-not (Test-Path -LiteralPath $javac)) {
    $javacCommand = Get-Command javac -ErrorAction SilentlyContinue
    $jarCommand = Get-Command jar -ErrorAction SilentlyContinue
    if (-not $javacCommand -or -not $jarCommand) {
        throw 'Java 25 JDK was not found. Install Java 25 and add it to PATH.'
    }
    $javac = $javacCommand.Source
    $jarTool = $jarCommand.Source
}
if (-not (Test-Path -LiteralPath $ServerJar)) {
    throw "HytaleServer.jar was not found at: $ServerJar"
}

$classes = Join-Path $projectRoot 'build\classes'
$dist = Join-Path $projectRoot 'dist'
$outputJar = Join-Path $dist 'ImmersiveNPCs-0.6.3-R134.1-NPC-AUTHORING-STUDIO-A5-UI-PARSER-HOTFIX.jar'
$resolvedClasses = [IO.Path]::GetFullPath($classes)
$resolvedBuildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build'))
if (-not $resolvedClasses.StartsWith($resolvedBuildRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe build output path: $resolvedClasses"
}
if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classes, $dist | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java') -Recurse -File -Filter '*.java' | ForEach-Object FullName)
if ($sources.Count -eq 0) {
    throw 'No Java sources found.'
}

$javacArgs = Join-Path $resolvedBuildRoot 'javac-main.args'
$arguments = @(
    '-encoding', 'UTF-8', '-source', '25', '-target', '25',
    '-classpath', ('"' + $ServerJar.Replace('\', '/') + '"'),
    '-d', ('"' + $classes.Replace('\', '/') + '"')
) + @($sources | ForEach-Object { '"' + $_.Replace('\', '/') + '"' })
[IO.File]::WriteAllLines($javacArgs, $arguments, [Text.UTF8Encoding]::new($false))
& $javac "@$javacArgs"
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$resourcesRoot = Join-Path $projectRoot 'src\main\resources'
Get-ChildItem -LiteralPath $resourcesRoot -Recurse -File |
    Where-Object { $_.FullName -notmatch '[\\/]__pycache__[\\/]' -and $_.Extension -ne '.pyc' } |
    ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($resourcesRoot, $_.FullName)
        $resourceTarget = Join-Path $classes $relative
        New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($resourceTarget)) |
            Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $resourceTarget -Force
    }

# Custom ItemGrid section identity must be present in the document on its first
# client-visible construction. WindowManager IDs are positive and monotonically
# increase for a connection, so generate a practical connection-lifetime bundle
# instead of retaining the original probe-only NpcSection1..8 ceiling.
$sectionDocumentDirectory = Join-Path $classes 'Common\UI\Custom\Pages\NativeInventoryProbe'
New-Item -ItemType Directory -Force -Path $sectionDocumentDirectory | Out-Null
for ($sectionId = 9; $sectionId -le 1024; $sectionId++) {
    $sectionDocument = @"
`$Probe = "GridCommon.ui";
`$Probe.@ProbeGrid #NpcInventoryGrid { InventorySectionId: $sectionId; }
"@
    $sectionPath = Join-Path $sectionDocumentDirectory "NpcSection$sectionId.ui"
    [IO.File]::WriteAllText($sectionPath, $sectionDocument,
        [Text.UTF8Encoding]::new($false))
}
& $jarTool --create --file $outputJar -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

Write-Host "Built $outputJar"
