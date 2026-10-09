param([string]$Resources = (Join-Path $PSScriptRoot '../src/main/resources'))
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Complete-NativeGearCarriers.ps1') -OutputRoot $Resources -VerifyOnly
& (Join-Path $PSScriptRoot 'Test-ManagedCarrierGraphs.ps1') -OutputRoot $Resources
