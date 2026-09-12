[CmdletBinding()]
param([switch]$Start,[ValidateRange(1024,65535)][int]$Port=5591,[string]$NpcModPath='')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$package=Join-Path $repo 'evidence/stage-13/cohort-aj'
$receipt=Get-Content (Join-Path $package 'package-validation.json') -Raw|ConvertFrom-Json
if($receipt.liveDeploymentPerformed -ne $false){throw 'Expected packaging-only checkpoint'}
$root=Join-Path $repo ('run/healing-probe-aj-'+[Guid]::NewGuid().ToString('N'))
if(Test-Path -LiteralPath $root){throw 'Refuse to reuse a world'}
$mods=Join-Path $root 'mods'
New-Item -ItemType Directory -Path $mods -Force|Out-Null
$world=Join-Path $root 'universe/worlds/default'
New-Item -ItemType Directory -Path $world -Force|Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'tools/fixtures/healing-probe-world.json') -Destination (Join-Path $world 'config.json')
foreach($entry in $receipt.jarHashes.psobject.Properties){
    $source=Join-Path $package ('artifacts/'+$entry.Name)
    if((Get-FileHash -LiteralPath $source).Hash -ne $entry.Value){throw "Archived JAR checksum mismatch: $($entry.Name)"}
    Copy-Item -LiteralPath $source -Destination $mods
}
# Read-only copy of permission grants, not RPG gameplay/mod/save state.
$permissions=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG/permissions.json'
Copy-Item -LiteralPath $permissions -Destination (Join-Path $root 'permissions.json')
if($NpcModPath){
    $npc=Get-Item -LiteralPath $NpcModPath
    if($npc.Name -notlike 'ImmersiveNPCs*.jar'){throw 'Only explicit ImmersiveNPCs comparison supported'}
    Copy-Item -LiteralPath $npc.FullName -Destination $mods
}
$game=Join-Path $env:APPDATA 'Hytale/install/pre-release/package/game/latest'
$server=Join-Path $game 'Server/HytaleServer.jar'
$assets=Join-Path $game 'Assets.zip'
$pin=Get-Content (Join-Path $package 'visibility-boundary-audit.json') -Raw|ConvertFrom-Json
if((Get-FileHash $server).Hash -ne $pin.serverSha256 -or (Get-FileHash $assets).Hash -ne $pin.assetsSha256){throw 'Installed native build changed; re-audit before using this probe'}
Write-Host "Disposable world: $root"
Write-Host "Local direct-connect address: 127.0.0.1:$Port"
Write-Host 'R032-AJ: fresh flat fixture, spawn 8.5,64,8.5; recipient at 8.5,64,2.5. No terrain edits or ambient NPCs. Standalone controls use none, recipient controls use native.'
Write-Host 'Probe permission: inigmasgames.rpg.healingprobe. Existing local admin grants copied; /op self is available with --allow-op.'
Write-Host 'Nothing has been installed in Saves/RPG/mods. Do the three-mod native control before adding ImmersiveNPCs.'
Write-Host 'Direct Connect requires authenticated mode and an authenticated server session, not offline/singleplayer mode.'
Write-Host 'After boot, enter auth login device in the SERVER console, complete the displayed sign-in in your browser, then run auth status before joining.'
Write-Host 'If prompted to choose a profile, follow the server auth select instructions. No live server credentials are copied; a fresh probe directory may require sign-in again.'
$arguments=@('-Drpg.healingPresentationProbe=true',"-Drpg.healingPresentationProbeRoot=$root",'-jar',$server,'--bind',"127.0.0.1:$Port",'--auth-mode','authenticated','--allow-op','--disable-sentry',"--assets=$assets")
if($Start){
    Push-Location $root
    try{& java @arguments}finally{Pop-Location}
}else{
    Write-Host 'Prepared only. To run this exact world, execute the following in PowerShell:'
    Write-Host ('Set-Location -LiteralPath '''+$root.Replace("'","''")+'''')
    Write-Host ('& java '+(($arguments|ForEach-Object {"'"+$_.Replace("'","''")+"'"}) -join ' '))
}
