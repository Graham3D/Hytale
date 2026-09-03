param([Parameter(Mandatory=$true)][string]$Candidate,[switch]$Reviewed)
$ErrorActionPreference='Stop'
if (-not $Reviewed) { throw 'Explicit -Reviewed is required for promotion.' }
$project=(Resolve-Path "$PSScriptRoot\..\..").Path
$java=Join-Path $project '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
$server="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
& $java -classpath "$project\build\classes;$server" `
  com.inigmasgames.persistentnpcs.evaluation.EvaluationArtifactCli `
  --command promote-fixture --project $project --candidate $Candidate --reviewed true
if ($LASTEXITCODE -ne 0) { throw 'Fixture promotion failed' }
