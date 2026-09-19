[CmdletBinding()]
param([switch]$Deploy)

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Package-CanvasCursorHudR041.ps1') -Revision R042 -Deploy:$Deploy
