$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo=(Resolve-Path "$PSScriptRoot/..").Path
$out=Join-Path $repo 'evidence/stage-13/cohort-at'
$package=Get-Content (Join-Path $out 'package-validation.json') -Raw|ConvertFrom-Json
if(-not $package.deployed){throw 'No deployed AT package receipt'}
$save=Split-Path (Split-Path $package.deploymentPath)
$mods=Split-Path $package.deploymentPath
$jars=@(foreach($f in Get-ChildItem $mods -Filter '*.jar'){
    $z=[IO.Compression.ZipFile]::OpenRead($f.FullName)
    try{$e=$z.GetEntry('manifest.json');if(-not $e){throw "Manifest missing: $($f.Name)"}
        $r=[IO.StreamReader]::new($e.Open());try{$m=$r.ReadToEnd()|ConvertFrom-Json}finally{$r.Dispose()}
        @{file=$f.Name;group=$m.Group;name=$m.Name;sha256=(Get-FileHash $f.FullName).Hash}
    }finally{$z.Dispose()}
})
$rpg=@($jars|Where-Object {$_.group -eq 'InigmasGames' -and $_.name -eq 'HytaleRPGPhase00Audit'})
if($rpg.Count -ne 1 -or $rpg[0].sha256 -ne $package.deployedSha256){throw 'Active mod identity/hash mismatch'}
$backupRoot=Split-Path $package.saveBackup
$inventory=Get-Content (Join-Path $backupRoot 'backup-inventory.json') -Raw|ConvertFrom-Json
$differences=@(foreach($item in $inventory){
    $path=Join-Path $save $item.path
    if(-not (Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path).Hash -ne $item.sha256){$item.path}
})
if($differences.Count -ne 1 -or $differences[0] -ne 'mods\HyARPG.jar'){throw "Unexpected live state changes since backup: $differences"}
$originalPaths=@{};foreach($item in $inventory){$originalPaths[$item.path]=$true}
$added=@(Get-ChildItem $save -File -Recurse|Where-Object {-not $originalPaths.ContainsKey([IO.Path]::GetRelativePath($save,$_.FullName))})
if($added.Count){throw 'Unexpected new live save files since backup'}
foreach($dir in @('stage11-matrix','stage13-hardening')){
    $dest=Join-Path $out $dir;New-Item -ItemType Directory -Force $dest|Out-Null
    Get-ChildItem (Join-Path $repo "build/$dir") -Filter '*.json' -File|Copy-Item -Destination $dest
}
@{revision='R032-AT';activeRpgJarCount=$rpg.Count;installedMods=$jars;changedLiveFiles=$differences;newLiveFiles=$added.Count;verifiedBackupFiles=$inventory.Count;connectedVerified=$false;runtimeEvidence='ISOLATED_EXACT_JAR_SMOKE_ONLY'}|ConvertTo-Json -Depth 6|Set-Content (Join-Path $out 'post-deployment-validation.json') -Encoding utf8
"AT confirmed: one RPG JAR; only mods/HyARPG.jar changed; $($inventory.Count) backup files checked."
