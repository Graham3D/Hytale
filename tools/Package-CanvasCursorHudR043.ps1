[CmdletBinding()]
param([switch]$Deploy)

if ($Deploy) {
    & "$PSScriptRoot/Package-CanvasCursorHudR041.ps1" -Revision R043 -Deploy
} else {
    & "$PSScriptRoot/Package-CanvasCursorHudR041.ps1" -Revision R043
}
exit $LASTEXITCODE
