param([Parameter(Mandatory=$true)][string]$Run,
      [Parameter(Mandatory=$true)][string]$Scenario,[string]$Utterance='')
$ErrorActionPreference='Stop'
$project=(Resolve-Path "$PSScriptRoot\..\..").Path
$java=Join-Path $project '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
$server="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$cliArgs=@('--command','freeze-candidate','--project',$project,'--run',$Run,'--scenario',$Scenario)
if ($Utterance) { $cliArgs += @('--utterance',$Utterance) }
& $java -classpath "$project\build\classes;$server" `
  com.inigmasgames.persistentnpcs.evaluation.EvaluationArtifactCli @cliArgs
if ($LASTEXITCODE -ne 0) { throw 'Candidate freeze failed' }
