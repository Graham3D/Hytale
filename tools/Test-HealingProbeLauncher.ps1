[CmdletBinding()]
param([switch]$NativeSmoke,[ValidateRange(1024,65535)][int]$Port=5592)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $repo 'evidence/stage-13/cohort-ai/launcher-auth'
New-Item -ItemType Directory -Force -Path $out|Out-Null
$live=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar'
$liveHash=(Get-FileHash $live).Hash
$candidate=Join-Path $repo 'evidence/stage-13/cohort-ai/artifacts/HyARPG.jar'
$candidateHash=(Get-FileHash $candidate).Hash
$capture=@{}
# Exercise the actual -Start branch, capturing its native arguments without starting a server.
function java { $capture.Arguments=@($args);$capture.Directory=(Get-Location).Path }
$messages=& (Join-Path $repo 'tools/New-HealingProbeAI.ps1') -Start -Port $Port 6>&1
Remove-Item Function:\java
$arguments=$capture.Arguments
if(-not $arguments){throw 'Launcher did not invoke java'}
$modeIndex=[Array]::IndexOf($arguments,'--auth-mode')
if($modeIndex -lt 0 -or $arguments[$modeIndex+1] -ne 'authenticated'){throw 'Direct Connect authentication mode regression'}
if(@($arguments|Where-Object {$_ -in @('offline','insecure','--singleplayer','--session-token','--identity-token')}).Count){throw 'Unsafe authentication fallback/credentials in launcher'}
$bindIndex=[Array]::IndexOf($arguments,'--bind')
if($bindIndex -lt 0 -or $arguments[$bindIndex+1] -ne "127.0.0.1:$Port"){throw 'Loopback bind regression'}
$root=(Resolve-Path -LiteralPath $capture.Directory).Path
$expectedPrefix=Join-Path $repo 'run/healing-probe-ai-'
if(-not $root.StartsWith($expectedPrefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Disposable path regression'}
if($arguments -notcontains "-Drpg.healingPresentationProbeRoot=$root" -or $arguments -notcontains '-Drpg.healingPresentationProbe=true'){throw 'Probe scope flags missing'}
$receipt=Get-Content (Join-Path $repo 'evidence/stage-13/cohort-ai/package-validation.json') -Raw|ConvertFrom-Json
$mods=Join-Path $root 'mods'
if(@(Get-ChildItem $mods -File -Filter '*.jar').Count -ne 3){throw 'Three-mod baseline changed'}
foreach($entry in $receipt.jarHashes.psobject.Properties){if((Get-FileHash (Join-Path $mods $entry.Name)).Hash -ne $entry.Value){throw 'Prepared mod checksum mismatch'}}
if(($messages|Out-String) -notmatch 'auth login device' -or ($messages|Out-String) -notmatch 'auth status'){throw 'Missing dedicated-server sign-in instructions'}
$native=@{performed=$false;connectedVerified=$false}
if($NativeSmoke){
    $info=[Diagnostics.ProcessStartInfo]::new('java')
    foreach($argument in $arguments){$info.ArgumentList.Add($argument)}
    $info.WorkingDirectory=$root;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try{
        if(-not $process.Start()){throw 'Native authenticated smoke did not start'}
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        Start-Sleep -Seconds 20
        if(-not $process.HasExited){$process.StandardInput.WriteLine('stop');$process.StandardInput.Flush()}
        if(-not $process.WaitForExit(30000)){throw 'Native authenticated smoke did not stop within deadline'}
        $log=(($stdout.Result,$stderr.Result)-join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]",''
        $native=@{performed=$true;exitCode=$process.ExitCode;connectedVerified=$false;
            authenticatedMode=[bool]($log -match 'Authentication mode: AUTHENTICATED');
            loopbackListening=[bool]($log -match [regex]::Escape("Listening on /127.0.0.1:$Port"));
            booted=[bool]($log -match 'Hytale Server Booted');
            cleanShutdown=[bool]($log -match 'Shutting down\.\.\. 0\s');
            loginStillRequired=[bool]($log -match 'No server tokens configured')}
        # Retain only diagnostic markers, never device codes or tokens from a future authenticated run.
        ($log -split "`r?`n"|Where-Object {$_ -match 'Authentication mode:|Listening on /127\.0\.0\.1:|Hytale Server Booted|No server tokens configured|RPG_HEAL_PROBE revision=|Shutting down\.\.\.'})|
            Set-Content (Join-Path $out 'native-startup-markers.txt') -Encoding utf8
        if($native.exitCode -ne 0 -or -not $native.authenticatedMode -or -not $native.loopbackListening -or -not $native.booted -or -not $native.cleanShutdown){throw 'Native authenticated startup gate failed'}
    }finally{
        if($process.Id -and -not $process.HasExited){$process.Kill($true)}
        $process.Dispose()
    }
}
if((Get-FileHash $live).Hash -ne $liveHash -or (Get-FileHash $candidate).Hash -ne $candidateHash){throw 'RPG JAR changed during launcher-only validation'}
[ordered]@{result='PASS';launcherSha256=(Get-FileHash (Join-Path $repo 'tools/New-HealingProbeAI.ps1')).Hash;
    argumentCapture='PASS';disposableRoot=[IO.Path]::GetFileName($root);exactThreeMods=$true;native=$native;
    liveJarUnchanged=$true;packagedJarUnchanged=$true;liveJarSha256=$liveHash;packagedJarSha256=$candidateHash;
    authFlowInvoked=$false;liveDeploymentPerformed=$false;connectedVerified=$false}|
    ConvertTo-Json -Depth 6|Set-Content (Join-Path $out 'validation.json') -Encoding utf8
Write-Host 'Launcher arguments/safeguards PASS. Native authentication completion and connected join remain owner QA.'
