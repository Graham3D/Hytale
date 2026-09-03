param(
    [string]$ServerJar = "$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar"
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$localJdk = Join-Path $projectRoot '.tools\jdk-25.0.4+7\bin'
$javac = Join-Path $localJdk 'javac.exe'
$java = Join-Path $localJdk 'java.exe'

& (Join-Path $projectRoot 'build.ps1') -ServerJar $ServerJar
if ($LASTEXITCODE -ne 0) {
    throw "build.ps1 failed with exit code $LASTEXITCODE"
}

$testClasses = Join-Path $projectRoot 'build\test-classes'
$resolvedTests = [IO.Path]::GetFullPath($testClasses)
$resolvedBuildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build'))
if (-not $resolvedTests.StartsWith($resolvedBuildRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe test output path: $resolvedTests"
}
if (Test-Path -LiteralPath $testClasses) {
    Remove-Item -LiteralPath $testClasses -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null

$testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\test\java') -Recurse -File -Filter '*.java' | ForEach-Object FullName)
if ($testSources.Count -eq 0) {
    throw 'No Java test sources found.'
}

$classpath = (Join-Path $projectRoot 'build\classes') + ";" + $ServerJar
& $javac -encoding UTF-8 -source 25 -target 25 -classpath $classpath -d $testClasses @testSources
if ($LASTEXITCODE -ne 0) {
    throw "Test compilation failed with exit code $LASTEXITCODE"
}

$runtimeClasspath = $testClasses + ";" + $classpath
& $java -ea -classpath $runtimeClasspath com.inigmasgames.taverns.PreparedFoodFeatureTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.taverns.CoreFeatureTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.taverns.TavernRepositoryMigrationTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.taverns.ComfortTooltipMetadataTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.taverns.RelaxedFeatureTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.taverns.TavernServiceFeatureTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.taverns.TableServingFeatureTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.taverns.TavernPatronFeatureTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}

Write-Host 'All Taverns tests passed.'
