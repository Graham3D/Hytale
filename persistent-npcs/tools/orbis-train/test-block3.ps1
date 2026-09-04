param([string]$OfflineRoot='G:\My Drive\Inigmas Games\Orbis Offline Training')
$ErrorActionPreference='Stop';$python=Join-Path $OfflineRoot 'envs\block3-py311\Scripts\python.exe';if(-not(Test-Path -LiteralPath $python)){throw "Pinned Python is missing: $python"}
& $python -m unittest discover -s (Join-Path $PSScriptRoot 'python') -p 'test_block3_gate.py' -v
if($LASTEXITCODE -ne 0){throw 'Block 3 gate tests failed.'}
