param([string]$Scenario='h2-lycander-live',[string]$Mode='LIVE_HEADLESS',
      [string]$ProductionRoot="$env:APPDATA\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs")
$ErrorActionPreference='Stop'
if ($Mode -ne 'LIVE_HEADLESS') { throw 'This checkpoint supports LIVE_HEADLESS only.' }
$project=(Resolve-Path "$PSScriptRoot\..\..").Path
$jdk=Join-Path $project '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
$server="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$command=if($Scenario -in @('gate-a','gate-b','multi-agent','behavior-hardening','gate-b-cleanup','lycander-desire-stress')){$Scenario}else{'run'}
& $jdk --add-modules jdk.httpserver -classpath "$project\build\classes;$server" `
  com.inigmasgames.persistentnpcs.evaluation.OrbisEvaluationCli `
  --command $command --scenario $Scenario --production $ProductionRoot `
  --output "$project\build\orbis-eval"
if ($LASTEXITCODE -ne 0) { throw "Orbis evaluation campaign failed" }
