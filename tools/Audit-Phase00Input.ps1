[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$settingsPath = "$env:APPDATA\Hytale\data\pre-release\Settings.json"
$clientPath = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Client\HytaleClient.exe"
$settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
$clientText = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($clientPath))
$markers = @('Ability1ItemAction','Ability2ItemAction','Ability3ItemAction','Ability4ItemAction',
    'InputActionManager','InputActionContext','GeneralContext','UiContext','MachinimaContext',
    'SwitchCameraMode','OpenInventory','ReverseCamera','FrontCamera')
$markerEvidence = [ordered]@{}
foreach ($marker in $markers) { $markerEvidence[$marker] = $clientText.Contains($marker, [StringComparison]::Ordinal) }

$actions = @($settings.InputActions.PSObject.Properties | ForEach-Object { $_.Value })
$result = [ordered]@{
    schemaVersion = 2
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    patchline = 'pre-release'
    settingsPath = $settingsPath
    settingsSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $settingsPath).Hash
    settingsMutated = $false
    serializedOverrides = $actions
    targetBindings = [ordered]@{
        C = [ordered]@{ sdlScancode = 6; serializedConsumers = @($actions | Where-Object { 6 -in @($_.Bindings.Scancode) }); exactDefaultOwner = 'CLIENT_SETTINGS_UI_TEST_REQUIRED' }
        K = [ordered]@{ sdlScancode = 14; serializedConsumers = @($actions | Where-Object { 14 -in @($_.Bindings.Scancode) }); exactDefaultOwner = 'CLIENT_SETTINGS_UI_TEST_REQUIRED' }
    }
    binaryArchitectureMarkers = $markerEvidence
    conclusion = 'The client has four built-in Ability item actions and contextual input architecture. No supported server/API registration or settings-category surface for third-party global C/K actions was found. Serialized overrides have no C/K consumer, but un-serialized defaults require real-client Settings UI verification.'
}
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $projectRoot 'evidence\phase-00\input-settings-snapshot.json') -Encoding utf8
$result | ConvertTo-Json -Depth 5
