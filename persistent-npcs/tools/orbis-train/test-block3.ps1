param(
    [string]$OfflineRoot = 'C:\HytaleTraining\Orbis',
    [string]$PythonPath
)
$ErrorActionPreference='Stop'
$python = if ([string]::IsNullOrWhiteSpace($PythonPath)) {
    Join-Path $OfflineRoot 'envs\block3-py311\Scripts\python.exe'
} else {
    [IO.Path]::GetFullPath($PythonPath)
}
if(-not(Test-Path -LiteralPath $python)){throw "Pinned Python is missing: $python"}
& $python -m unittest discover -s (Join-Path $PSScriptRoot 'python') -p 'test_block3_gate.py' -v
if($LASTEXITCODE -ne 0){throw 'Block 3 gate tests failed.'}
