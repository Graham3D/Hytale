[CmdletBinding()]
param([string]$Checkpoint='cd7cdbd0299de4bf18740b2cca08756b64b77f69')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'docs/review'
New-Item -ItemType Directory -Force -Path $out|Out-Null
Push-Location $root
try {
    $resolved=(& git rev-parse --verify "$Checkpoint^{commit}").Trim()
    if($LASTEXITCODE -ne 0){throw 'Unknown implementation checkpoint'}
    function Read-Committed([string]$path){
        $lines=& git show "${resolved}:$path"
        if($LASTEXITCODE -ne 0){throw "Cannot read checkpoint source $path"}
        return ($lines -join "`n")
    }
    $paths=@(& git ls-tree -r --name-only $resolved -- docs | Where-Object {$_ -like '*.md' -and $_ -notlike 'docs/review/*'})
    $paths=@($paths|Sort-Object)
    $sources=@()
    $full=[Text.StringBuilder]::new()
    [void]$full.AppendLine('# Hytale RPG — full retained Stage 00–13 implementation record')
    [void]$full.AppendLine()
    [void]$full.AppendLine("Implementation checkpoint: ``$resolved``. Generated from committed documents and Git history; not a fabricated transcript of undocumented actions.")
    [void]$full.AppendLine()
    [void]$full.AppendLine('Read [current synthesis](implementation-history-00-13.md) and [current QA/QC](qa-qc-checklist.md) first. The owner subsequently reported that the L mod loads. That confirms reported loading only; it does not retrospectively make every historical gate PASS.')
    [void]$full.AppendLine()
    [void]$full.AppendLine('The source documents below are historical snapshots. Their old commands, pending statuses, ownership assumptions and version numbers may be superseded. Body text is retained with line endings normalized and relative Markdown links resolved to the pinned GitHub source. No failed gate is erased. Duplicated historical reports are intentional. Test output, binaries and code are linked by the original evidence paths; they are not all inlined. Uncommitted art and private attachments/conversation transcripts are not copied.')
    [void]$full.AppendLine()
    [void]$full.AppendLine('## Source index')
    [void]$full.AppendLine()
    foreach($path in $paths){
        $blob=(& git rev-parse "${resolved}:$path").Trim()
        $sources += [ordered]@{path=$path;gitBlob=$blob;url="https://github.com/Graham3D/Hytale/blob/$resolved/$path"}
        [void]$full.AppendLine("- [$path](https://github.com/Graham3D/Hytale/blob/$resolved/$path) — Git blob ``$blob``")
    }
    foreach($path in $paths){
        $body=Read-Committed $path
        $base=[Uri]"https://github.com/Graham3D/Hytale/blob/$resolved/$path"
        $body=[regex]::Replace($body,'(\[[^\]\r\n]*\]\()([^\s\)]+)(\))',{
            param($match)
            $target=$match.Groups[2].Value
            if($target -match '^(#|[a-zA-Z][a-zA-Z0-9+.-]*:|/|<)'){return $match.Value}
            return $match.Groups[1].Value+[Uri]::new($base,$target).AbsoluteUri+$match.Groups[3].Value
        })
        [void]$full.AppendLine("`n---`n`n## Archived source: $path`n`n> Historical snapshot; consult the current synthesis for supersession.`n")
        [void]$full.AppendLine($body)
    }
    [void]$full.AppendLine("`n---`n`n# Complete checkpoint Git chronology and file-change statistics`n")
    [void]$full.AppendLine('Dates are Git metadata, not measurements of work duration. Each commit can be inspected on GitHub for its full patch. Counts/statistics are not test counts.')
    [void]$full.AppendLine("`n``````text")
    $history=& git log --reverse --date=iso-strict '--format=COMMIT %H%nAuthor date: %aI%nCommit date: %cI%nSubject: %s%n%b' --stat $resolved
    if($LASTEXITCODE -ne 0){throw 'History export failed'}
    [void]$full.AppendLine(($history -join "`n"))
    [void]$full.AppendLine('```')
    [IO.File]::WriteAllText((Join-Path $out 'implementation-history-full.md'),$full.ToString(),[Text.UTF8Encoding]::new($false))

    $coverage=Read-Committed 'evidence/stage-13/cohort-f/hardening/coverage.json'|ConvertFrom-Json
    $profiles=@{}
    foreach($path in (& git ls-tree -r --name-only $resolved -- src/main/resources/rpg/runtime | Where-Object {$_ -match '/stage-.*\.json$'})){
        $document=Read-Committed $path|ConvertFrom-Json
        foreach($skill in $document.skills){
            if($profiles.ContainsKey($skill.skillId)){throw "Duplicate runtime skill $($skill.skillId)"}
            $profiles[$skill.skillId]=@{profile=$skill;source=$path}
        }
    }
    if($coverage.skills.Count -ne 87 -or $coverage.passives.Count -ne 66 -or $profiles.Count -ne 87){throw 'Canonical inventory mismatch'}
    $skills=@(foreach($skill in ($coverage.skills|Sort-Object family,id)){
        if(-not $profiles.ContainsKey($skill.id)){throw "Missing runtime profile $($skill.id)"}
        $record=$profiles[$skill.id];$p=$record.profile
        [pscustomobject][ordered]@{
            id=$skill.id;name=$skill.name;family=$p.family;equipCommand="/rpg equip skill01 $($skill.id)";
            mainHandKinds=($p.allowedMainHandKinds -join ';');offHandKinds=($p.requiredOffHandKinds -join ';');
            baseResource=$p.resourceType;baseCost=$p.resourceCost;baseCooldownSeconds=$p.cooldownSeconds;
            baseWindupSeconds=$p.windupSeconds;activationGate=$skill.activationGate;
            knownLimitations=($skill.limitations -join ' | ');profileSource="https://github.com/Graham3D/Hytale/blob/$resolved/$($record.source)";
            fullRuntimeProfile=($p|ConvertTo-Json -Depth 30 -Compress);
            positiveResult='NOT_RUN';negativeResult='NOT_RUN';cleanupResult='NOT_RUN';
            testTime='';heldItem='';loadoutRevision='';correlationRootInstance='';evidenceFiles='';notes=''
        }
    })
    $passives=@(foreach($passive in ($coverage.passives|Sort-Object id)){
        [pscustomobject][ordered]@{
            id=$passive.id;name=$passive.name;implementationStage=$passive.phase;
            equipCommand="/rpg equip passive01 $($passive.id)";linkCommand='/rpg link passive01 skill01';
            expectedModifier=($passive.modifierOps -join ' | ');knownLimitations=($passive.limitations -join ' | ');
            chosenCompatibleSkill='';baselineResult='NOT_RUN';modifiedResult='NOT_RUN';incompatibleResult='NOT_RUN';
            testTime='';loadoutRevision='';correlationRootInstance='';evidenceFiles='';notes=''
        }
    })
    $skills|Export-Csv -LiteralPath (Join-Path $out 'qa-skills.csv') -NoTypeInformation -Encoding utf8
    $passives|Export-Csv -LiteralPath (Join-Path $out 'qa-passives.csv') -NoTypeInformation -Encoding utf8
    $files=@(foreach($name in @('README.md','implementation-history-00-13.md','implementation-history-full.md','qa-qc-checklist.md','qa-skills.csv','qa-passives.csv')){
        $file=Get-Item -LiteralPath (Join-Path $out $name)
        [ordered]@{name=$name;bytes=$file.Length;sha256=(Get-FileHash -LiteralPath $file.FullName).Hash}
    })
    [ordered]@{implementationCheckpoint=$resolved;archivedDocumentCount=$sources.Count;
        gitCommitCount=[int](& git rev-list --count $resolved);skills=$skills.Count;passives=$passives.Count;
        ownerReportedLoading=$true;connectedAllMechanicsVerified=$false;
        skillInventorySource='evidence/stage-13/cohort-f/hardening/coverage.json';
        profileInputs='Pinned checkpoint src/main/resources/rpg/runtime/stage-*.json';
        files=$files;historicalSources=$sources}|ConvertTo-Json -Depth 8|
        Set-Content -LiteralPath (Join-Path $out 'review-manifest.json') -Encoding utf8
    Write-Output "Review packet: $($sources.Count) archived documents; $($skills.Count) skills; $($passives.Count) passives; source $resolved"
} finally {Pop-Location}
