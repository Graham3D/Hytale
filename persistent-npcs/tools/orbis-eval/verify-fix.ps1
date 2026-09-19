param([string]$Variants='required')
$ErrorActionPreference='Stop'
if ($Variants -ne 'required') { throw 'Only the fixed required variant suite is allowed.' }
$project=(Resolve-Path "$PSScriptRoot\..\..").Path
$java=Join-Path $project '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
$server="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$cp="$project\build\test-classes;$project\build\classes;$server"
foreach($test in @('com.inigmasgames.persistentnpcs.evaluation.R090H4FixVerificationTest',
  'com.inigmasgames.persistentnpcs.evaluation.R090AuthoritativeRecallRegressionTest')) {
  & $java --add-modules jdk.httpserver -ea -classpath $cp $test
  if ($LASTEXITCODE -ne 0) { throw "Fix verification failed: $test" }
}
