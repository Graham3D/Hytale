param([Parameter(Mandatory=$true)][string]$Fixture)
$ErrorActionPreference='Stop'
$project=(Resolve-Path "$PSScriptRoot\..\..").Path
$java=Join-Path $project '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
$server="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
& $java -classpath "$project\build\classes;$server" `
  com.inigmasgames.persistentnpcs.evaluation.EvaluationArtifactCli `
  --command replay-fixture --project $project --fixture $Fixture
if ($LASTEXITCODE -ne 0) { throw 'Fixture replay validation failed' }
