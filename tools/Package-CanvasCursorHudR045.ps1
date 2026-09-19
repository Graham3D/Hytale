[CmdletBinding()]
param([switch]$Deploy)

if ($Deploy) {
    & "$PSScriptRoot/Package-CanvasCursorHudR041.ps1" -Revision R045 -Deploy
} else {
    & "$PSScriptRoot/Package-CanvasCursorHudR041.ps1" -Revision R045
}
exit $LASTEXITCODE
