param([string]$OfflineRoot='G:\My Drive\Inigmas Games\Orbis Offline Training',[string]$ActiveSaveRoot="$env:APPDATA\Hytale\UserData\Saves\NPC")
$ErrorActionPreference = 'Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'));$offline=[IO.Path]::GetFullPath($OfflineRoot);$save=[IO.Path]::GetFullPath($ActiveSaveRoot);$separator=[IO.Path]::DirectorySeparatorChar
if(($offline.TrimEnd($separator)+$separator).StartsWith(($save.TrimEnd($separator)+$separator),[StringComparison]::OrdinalIgnoreCase)-or($save.TrimEnd($separator)+$separator).StartsWith(($offline.TrimEnd($separator)+$separator),[StringComparison]::OrdinalIgnoreCase)){throw 'Offline root must not overlap the active Hytale save.'}
$python=Join-Path $offline 'envs\block3-py311\Scripts\python.exe';if(-not(Test-Path -LiteralPath $python)){throw "Pinned Python is missing: $python"}
$baseRoot=Join-Path $offline 'models\base\nvidia-nemotron-3-nano-4b-bf16-dfaf35de'
& $python (Join-Path $PSScriptRoot 'python\block3_gate.py') preflight --project-root $projectRoot --offline-root $offline --base-root $baseRoot
exit $LASTEXITCODE
