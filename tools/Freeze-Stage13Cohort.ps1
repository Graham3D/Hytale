[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z]$')][string]$Cohort)
$ErrorActionPreference='Stop'
$freezeRoot=(Resolve-Path "$PSScriptRoot\..").Path
if((& git -C $freezeRoot branch --show-current).Trim() -ne 'RPG'){throw 'Expected RPG branch'}
$freezeOut=Join-Path $freezeRoot "evidence\stage-13\cohort-$Cohort\before"
if(Test-Path -LiteralPath $freezeOut){throw 'Preserve the first cohort snapshot'}
$freezeSave=Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG'
$running=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.CommandLine -match 'HytaleServer' -and $_.CommandLine -notmatch 'stage\d+-cohort-.*-smoke'})
if($running.Count){throw 'Cannot take a coordinated live-state snapshot while a Hytale server is active; no server is interrupted'}
New-Item -ItemType Directory -Path $freezeOut|Out-Null
$freezeData=Join-Path $freezeSave 'mods\InigmasGames_HytaleRPGPhase00Audit'
foreach($name in @('players','earned-rewards','encounters','diagnostics\native-rune-control')){
    $source=Join-Path $freezeData $name
    if(Test-Path -LiteralPath $source){
        $destination=Join-Path $freezeOut "save\$name"
        New-Item -ItemType Directory -Force -Path (Split-Path $destination)|Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Recurse
    }
}
$freezeFiles=@{}
foreach($base in @('src\main','src\test','canvas-ui\src')){
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $freezeRoot $base) -Recurse -File){
        $freezeFiles[[IO.Path]::GetRelativePath($freezeRoot,$file.FullName)]=(Get-FileHash -LiteralPath $file.FullName).Hash
    }
}
$saveHashes=@{}
foreach($file in Get-ChildItem -LiteralPath $freezeOut -File -Recurse){$saveHashes[[IO.Path]::GetRelativePath($freezeOut,$file.FullName)]=(Get-FileHash -LiteralPath $file.FullName).Hash}
[ordered]@{capturedAtUtc=[DateTime]::UtcNow.ToString('o');commit=(& git -C $freezeRoot rev-parse HEAD).Trim();
    sourceHashes=$freezeFiles;saveHashes=$saveHashes;liveServerRunning=$false;liveSaveModified=$false;
    backupScope='RPG-owned players, earned rewards, encounters and native control recovery; not a full world backup';
    connected='UNVERIFIED'}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $freezeOut 'checkpoint.json') -Encoding utf8
Write-Output "Frozen cohort $Cohort checkpoint in $freezeOut; live files untouched"
