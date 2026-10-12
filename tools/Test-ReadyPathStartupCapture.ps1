[CmdletBinding()]
param()

# Offline only: generates a tiny JFR with java -version; never starts Hytale.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ReadyPathCapture.Core.ps1')
$runtime = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\jre\latest\bin'
$java = Join-Path $runtime 'java.exe'
$jfr = Join-Path $runtime 'jfr.exe'
$testRoot = Join-Path $env:TEMP ('ready-path-offline-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
function Assert([bool]$condition, [string]$message) {
    if (-not $condition) { throw "OFFLINE TEST FAILED: $message" }
}

$now = [datetime]::UtcNow
$jar = 'C:\Hytale\Server\HytaleServer.jar'
$client = [pscustomobject]@{ProcessId=1234;Name='HytaleClient.exe';CreationDate=$now.AddSeconds(2)}
$server = [pscustomobject]@{
    ProcessId=5678;CreationDate=$now.AddSeconds(3);ExecutablePath=$java
    CommandLine=('"'+$java+'" -jar "'+$jar+'" --client-pid 1234 --singleplayer')
}
Assert (-not (Select-ReadyPathServer @($client) @($server) $now.AddMinutes(1) $java $jar)) 'stale server was accepted'
Assert (-not (Select-ReadyPathServer @($client) @($server) $now $java 'C:\other.jar')) 'wrong server JAR was accepted'
Assert (-not (Select-ReadyPathServer @($client) @($server) $now 'C:\other-java.exe' $jar)) 'wrong Java runtime was accepted'
$matched = Select-ReadyPathServer @($client) @($server) $now $java $jar
Assert ($matched.Server.ProcessId -eq 5678 -and $matched.Client.ProcessId -eq 1234) 'current launch was not matched'

$options = New-ReadyPathJavaOptions 'test-run' (Join-Path $testRoot 'integrated-server.jfr') 150 $true
Assert ($options.Contains('-Dhywind.readypath.enabled=true -Dhywind.readypath.run=test-run ')) 'ReadyPath launch options changed'
Assert ($options.Contains('-Dhywind.cyclone.durable=true ')) 'durable Cyclone gate was not armed'
Assert ($options.Contains('-XX:StartFlightRecording=name=HywindStartup,')) 'JFR launch option was removed'
Assert (-not (New-ReadyPathJavaOptions 'test-run' 'recording.jfr' 150 $false).Contains('hywind.cyclone.durable')) 'default capture unexpectedly armed Cyclone'

$gateLog = Join-Path $testRoot 'server.log'
Assert (-not (Test-ReadyPathCycloneGate $gateLog).Confirmed) 'missing server log confirmed the gate'
Set-Content -LiteralPath $gateLog -Value 'RPG_CYCLONE_DURABLE_GATE enabled=false'
Assert (-not (Test-ReadyPathCycloneGate $gateLog).Confirmed) 'disabled gate was accepted'
Set-Content -LiteralPath $gateLog -Value 'RPG_CYCLONE_DURABLE_GATE enabled=true'
Assert ((Test-ReadyPathCycloneGate $gateLog).Confirmed) 'enabled gate marker was rejected'
Add-Content -LiteralPath $gateLog -Value 'RPG_CYCLONE_DURABLE_GATE enabled=false'
Assert (-not (Test-ReadyPathCycloneGate $gateLog).Confirmed) 'ambiguous gate markers were accepted'
Set-Content -LiteralPath $gateLog -Value 'unrelated startup output'
Assert (-not (Test-ReadyPathCycloneGate $gateLog).Confirmed) 'missing gate marker was accepted'

$missing = Join-Path $testRoot 'missing.jfr'
Assert (-not (Test-ReadyPathRecording $missing $jfr -1 0 $true).Ready) 'missing JFR was accepted'
$empty = Join-Path $testRoot 'empty.jfr'
[IO.File]::WriteAllBytes($empty, [byte[]]@())
Assert (-not (Test-ReadyPathRecording $empty $jfr -1 0 $true).Ready) 'empty JFR was accepted'
$incomplete = Join-Path $testRoot 'incomplete.jfr'
[IO.File]::WriteAllBytes($incomplete, (New-Object byte[] 4096))
Assert (-not (Test-ReadyPathRecording $incomplete $jfr 4096 2 $true).Ready) 'incomplete JFR was accepted'

$valid = Join-Path $testRoot 'valid.jfr'
$start = New-Object System.Diagnostics.ProcessStartInfo
$start.FileName = $java
$start.Arguments = "-XX:StartFlightRecording=filename=$valid,settings=profile,dumponexit=true -version"
$start.UseShellExecute = $false
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$start.CreateNoWindow = $true
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $start
try {
    [void]$process.Start()
    $null = $process.StandardOutput.ReadToEnd()
    $null = $process.StandardError.ReadToEnd()
    Assert ($process.WaitForExit(30000) -and $process.ExitCode -eq 0) 'offline Java JFR generation failed'
} finally { $process.Dispose() }
Assert (Test-Path -LiteralPath $valid -PathType Leaf) 'offline JFR file missing'
$bytes = (Get-Item -LiteralPath $valid).Length
Assert (-not (Test-ReadyPathRecording $valid $jfr $bytes 2 $false).Ready) 'unfinalized JFR was accepted'
$lock = [IO.File]::Open($valid, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try { Assert (-not (Test-ReadyPathRecording $valid $jfr $bytes 2 $true).Ready) 'temporarily locked JFR was accepted' }
finally { $lock.Dispose() }
Assert ((Test-ReadyPathRecording $valid $jfr $bytes 2 $true).Ready) 'valid finalized JFR was rejected'
Write-Output "OFFLINE TEST PASSED: PID identity, dual launch options, Cyclone log gate, missing/empty/incomplete/locked JFR retry, finalization gate, valid JFR ($bytes bytes)."
Write-Output "Test artifacts: $testRoot"
