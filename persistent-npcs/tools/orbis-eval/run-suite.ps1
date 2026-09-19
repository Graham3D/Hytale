param([ValidateSet('epistemic-core','full-deterministic')][string]$Suite='epistemic-core',
      [ValidateSet('STATIC_REPLAY')][string]$Mode='STATIC_REPLAY')
$ErrorActionPreference='Stop'
$project=(Resolve-Path "$PSScriptRoot\..\..").Path
if ($Suite -eq 'full-deterministic') { & "$project\test.ps1" -SkipLive; exit $LASTEXITCODE }
$java=Join-Path $project '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
$server="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$cp="$project\build\test-classes;$project\build\classes;$server"
foreach($test in @('com.inigmasgames.persistentnpcs.evaluation.R090H3DiagnosisTest',
  'com.inigmasgames.persistentnpcs.evaluation.R090H4FixVerificationTest',
  'com.inigmasgames.persistentnpcs.evaluation.R090H5FrozenFixtureTest',
  'com.inigmasgames.persistentnpcs.evaluation.R090H7LearningTest',
  'com.inigmasgames.persistentnpcs.evaluation.R090H8MultiAgentTest',
  'com.inigmasgames.persistentnpcs.evaluation.R090AuthoritativeRecallRegressionTest')) {
  & $java --add-modules jdk.httpserver -ea -classpath $cp $test
  if ($LASTEXITCODE -ne 0) { throw "Evaluation suite failed: $test" }
}
