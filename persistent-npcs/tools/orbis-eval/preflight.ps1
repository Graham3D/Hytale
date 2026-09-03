param([string]$ProductionRoot="$env:APPDATA\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs")
$ErrorActionPreference='Stop'
$project=(Resolve-Path "$PSScriptRoot\..\..").Path
$jdk=Join-Path $project '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
$server="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
& $jdk --add-modules jdk.httpserver -classpath "$project\build\classes;$server" `
  com.inigmasgames.persistentnpcs.evaluation.OrbisEvaluationCli `
  --command preflight --production $ProductionRoot --output "$project\build\orbis-eval"
if ($LASTEXITCODE -ne 0) { throw "Orbis evaluation preflight failed" }
