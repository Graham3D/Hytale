param([Parameter(Mandatory=$true)][string]$PreflightReport)
$ErrorActionPreference='Stop';$report=Get-Content -LiteralPath $PreflightReport -Raw|ConvertFrom-Json
if($report.g2 -ne 'PASS'){throw 'Reference evaluation requires a completed G2 adapter round trip; current preflight did not pass.'}
throw 'No candidate is available. Reference evaluation cannot run before an authorized SFT-0 checkpoint exists.'
