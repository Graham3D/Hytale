[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$controlRoot=(Resolve-Path "$PSScriptRoot\..").Path
$controlOutput=Join-Path $controlRoot 'evidence\stage-10\cohort-e\api'
New-Item -ItemType Directory -Force -Path $controlOutput | Out-Null
$controlServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$controlJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
foreach($controlClass in @(
 'com.hypixel.hytale.server.npc.role.support.WorldSupport',
 'com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport',
 'com.hypixel.hytale.server.npc.corecomponents.entity.ActionOverrideAttitude',
 'com.hypixel.hytale.server.npc.corecomponents.entity.builders.BuilderActionOverrideAttitude',
 'com.hypixel.hytale.server.npc.blackboard.view.attitude.IAttitudeProvider',
 'com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView',
 'com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeMap',
 'com.hypixel.hytale.server.npc.blackboard.view.PrioritisedProviderView',
 'com.hypixel.hytale.server.npc.blackboard.Blackboard',
 'com.hypixel.hytale.server.npc.role.Role',
 'com.hypixel.hytale.server.npc.NPCPlugin',
 'com.hypixel.hytale.server.flock.FlockMembership',
 'com.hypixel.hytale.server.core.entity.EntitySavingSystem')){
 $controlBytes=& $controlJavap -classpath $controlServer -p -c $controlClass 2>&1
 if($LASTEXITCODE -ne 0){throw "Cannot audit $controlClass"}
 $controlBytes | Set-Content -LiteralPath (Join-Path $controlOutput (($controlClass.Split('.')[-1])+'.txt')) -Encoding utf8
}
$controlZip=[IO.Compression.ZipFile]::OpenRead("$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip")
try{
 foreach($controlAsset in @('Server/Models/Human/Mannequin.json','Server/NPC/Roles/Empty_Role.json')){
  $controlEntry=$controlZip.GetEntry($controlAsset)
  if($null -eq $controlEntry){throw "Missing audited asset $controlAsset"}
  $controlReader=[IO.StreamReader]::new($controlEntry.Open())
  try{$controlRaw=$controlReader.ReadToEnd()}finally{$controlReader.Dispose()}
  $controlRaw | Set-Content -LiteralPath (Join-Path $controlOutput ([IO.Path]::GetFileName($controlAsset)+'.txt')) -Encoding utf8
 }
}finally{$controlZip.Dispose()}
[ordered]@{scope='INSTALLED_NATIVE_CONTROL_AND_RESTORATION_API';serverSha256=(Get-FileHash $controlServer).Hash;connectedProof=$false} |
 ConvertTo-Json | Set-Content -LiteralPath (Join-Path $controlOutput 'manifest.json') -Encoding utf8
