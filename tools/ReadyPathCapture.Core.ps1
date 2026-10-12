# Pure capture decisions and the native JFR reader, shared with offline tests.
function New-ReadyPathJavaOptions([string]$run, [string]$recording, [int]$recordingSeconds, [bool]$durableCyclone) {
    $cyclone = if ($durableCyclone) { '-Dhywind.cyclone.durable=true ' } else { '' }
    return "-Dhywind.readypath.enabled=true -Dhywind.readypath.run=$run $cyclone" +
        "-XX:StartFlightRecording=name=HywindStartup,settings=profile,filename=$recording," +
        "duration=${recordingSeconds}s,maxsize=256m,dumponexit=true"
}

function Test-ReadyPathDurableJar([string]$jarPath) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($jarPath)
        try {
            $entry = $archive.GetEntry('com/inigmasgames/hywind/HyArpgPlugin.class')
            if (-not $entry) { return $false }
            $stream = $entry.Open()
            try {
                $bytes = New-Object byte[] $entry.Length
                $read = 0
                while ($read -lt $bytes.Length) {
                    $count = $stream.Read($bytes, $read, $bytes.Length - $read)
                    if ($count -eq 0) { return $false }
                    $read += $count
                }
                $compiled = [Text.Encoding]::UTF8.GetString($bytes)
                return $compiled.Contains('hywind.cyclone.durable') -and
                    $compiled.Contains('RPG_CYCLONE_DURABLE_GATE enabled=%s') -and
                    $compiled.Contains('configureDurableCyclone')
            } finally { $stream.Dispose() }
        } finally { $archive.Dispose() }
    } catch { return $false }
}

function Test-ReadyPathCycloneGate([string]$serverLog) {
    if (-not $serverLog -or -not (Test-Path -LiteralPath $serverLog -PathType Leaf)) {
        return [pscustomobject]@{Confirmed=$false;Reason='server log snapshot unavailable'}
    }
    try {
        $values = @(Get-Content -LiteralPath $serverLog -ErrorAction Stop |
            ForEach-Object { if ($_ -match 'RPG_CYCLONE_DURABLE_GATE enabled=(true|false)(?:\s|$)') { $Matches[1] } })
        if ($values.Count -eq 1 -and $values[0] -ceq 'true') {
            return [pscustomobject]@{Confirmed=$true;Reason=$null}
        }
        $reason = if ($values.Count -eq 0) { 'gate marker missing from server log snapshot' }
            elseif ($values.Count -eq 1) { 'server reported enabled=false' }
            else { 'multiple gate markers in server log snapshot' }
        return [pscustomobject]@{Confirmed=$false;Reason=$reason}
    } catch {
        return [pscustomobject]@{Confirmed=$false;Reason="server log unreadable: $($_.Exception.Message)"}
    }
}

function Select-ReadyPathServer($clients, $servers, [datetime]$armedAt, [string]$javaPath, [string]$serverJar) {
    foreach ($server in @($servers)) {
        if (-not $server.CommandLine -or $server.CreationDate.ToUniversalTime() -lt $armedAt.AddSeconds(-2)) { continue }
        if (-not $server.ExecutablePath -or -not [string]::Equals($server.ExecutablePath, $javaPath, [StringComparison]::OrdinalIgnoreCase)) { continue }
        if ($server.CommandLine -notmatch '(?i)(?:^|\s)--singleplayer(?:\s|$)') { continue }
        $jarPattern = '(?i)(?:^|\s)-jar\s+"?' + [regex]::Escape($serverJar) + '"?(?:\s|$)'
        if ($server.CommandLine -notmatch $jarPattern) { continue }
        if ($server.CommandLine -notmatch '(?i)(?:^|\s)--client-pid\s+(\d+)(?:\s|$)') { continue }
        $clientPid = [int]$Matches[1]
        foreach ($client in @($clients)) {
            if ($client.ProcessId -ne $clientPid -or $client.Name -ine 'HytaleClient.exe') { continue }
            $clientCreated = $client.CreationDate.ToUniversalTime()
            if ($clientCreated -lt $armedAt.AddSeconds(-2) -or $clientCreated -gt $server.CreationDate.ToUniversalTime()) { continue }
            return [pscustomobject]@{Server=$server;Client=$client}
        }
    }
    return $null
}

function Invoke-ReadyPathJfrSummary([string]$jfrTool, [string]$recording) {
    # ProcessStartInfo avoids Windows PowerShell 5.1 converting native stderr
    # into a terminating NativeCommandError when ErrorActionPreference is Stop.
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $jfrTool
    $start.Arguments = 'summary "' + $recording + '"'
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.CreateNoWindow = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        if (-not $process.WaitForExit(15000)) {
            $process.Kill()
            return [pscustomobject]@{Valid=$false;Reason='jfr summary timed out'}
        }
        $valid = $process.ExitCode -eq 0 -and $stdout -match '(?m)^\s*Version:\s*\S+' -and $stdout -match '(?m)^\s*Chunks:\s*[1-9]\d*'
        $reason = if ($valid) { $null } elseif ($stderr.Trim()) { $stderr.Trim() } else { "jfr summary exit $($process.ExitCode)" }
        return [pscustomobject]@{Valid=$valid;Reason=$reason}
    } catch {
        return [pscustomobject]@{Valid=$false;Reason=$_.Exception.Message}
    } finally {
        $process.Dispose()
    }
}

function Test-ReadyPathRecording([string]$path, [string]$jfrTool, [long]$previousLength, [int]$stablePolls, [bool]$finalizationExpected) {
    try {
        $file = Get-Item -LiteralPath $path -ErrorAction Stop
        if ($file.Length -lt 1024) { return [pscustomobject]@{Ready=$false;Length=$file.Length;StablePolls=0;Reason='recording is absent or empty'} }
        $stable = if ($file.Length -eq $previousLength) { $stablePolls + 1 } else { 0 }
        if (-not $finalizationExpected -or $stable -lt 2) {
            return [pscustomobject]@{Ready=$false;Length=$file.Length;StablePolls=$stable;Reason='waiting for recording to finish and stabilize'}
        }
        $summary = Invoke-ReadyPathJfrSummary $jfrTool $path
        return [pscustomobject]@{Ready=$summary.Valid;Length=$file.Length;StablePolls=$stable;Reason=$summary.Reason}
    } catch {
        return [pscustomobject]@{Ready=$false;Length=0L;StablePolls=0;Reason='recording temporarily unavailable'}
    }
}
