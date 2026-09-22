[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$JarPath,
    [Parameter(Mandatory=$true)][string]$ExpectedVersion,
    [Parameter(Mandatory=$true)][string]$ExpectedRevision
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sha256 = {
    param([string]$Path)
    $stream = [IO.File]::OpenRead($Path)
    $hash = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($hash.ComputeHash($stream))).Replace('-', '') }
    finally { $hash.Dispose(); $stream.Dispose() }
}
$bytesHash = {
    param([byte[]]$Bytes)
    $hash = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($hash.ComputeHash($Bytes))).Replace('-', '') }
    finally { $hash.Dispose() }
}
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$zip = [IO.Compression.ZipFile]::OpenRead($resolvedJar)
try {
    $names = @($zip.Entries | ForEach-Object FullName)
    $duplicates = @($names | Group-Object | Where-Object Count -gt 1)
    if ($duplicates.Count) { throw "Duplicate JAR entries: $($duplicates.Name -join ', ')" }
    if (@($names | Where-Object { $_ -eq 'manifest.json' }).Count -ne 1) { throw 'Hywind must contain exactly one root manifest.json.' }

    $manifestEntry = $zip.GetEntry('manifest.json')
    $reader = [IO.StreamReader]::new($manifestEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if ($manifest.Group -ne 'InigmasGames' -or $manifest.Name -ne 'Hywind' -or
        $manifest.Version -ne $ExpectedVersion -or
        $manifest.Main -ne 'com.inigmasgames.hywind.HywindPlugin' -or
        $manifest.Metadata.RpgRevision -ne $ExpectedRevision) {
        throw 'Hywind manifest identity/version/bootstrap/revision mismatch.'
    }
    if ($manifest.ServerVersion -ne '=0.7.0-pre.3.1') { throw "Unexpected server pin: $($manifest.ServerVersion)" }
    if ($manifest.Dependencies.'Hytale:NPC' -ne '*') { throw 'Required Hytale:NPC dependency is absent.' }
    if ($manifest.Dependencies.'Hytale:Mounts' -ne '*' -or $manifest.Dependencies.'Hytale:CosmeticsModule' -ne '*') {
        throw 'Required Tavern native dependencies are absent.'
    }
    if ($manifest.Metadata.TavernSourceRevision -ne 'R056') { throw 'Tavern source revision is absent or unexpected.' }

    $required = @(
        'com/inigmasgames/hywind/HywindPlugin.class',
        'com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.class',
        'com/inigmasgames/canvasui/CanvasUI.class',
        'com/inigmasgames/taverns/TavernsPlugin.class',
        'com/inigmasgames/taverns/TavernRepository.class',
        'com/inigmasgames/taverns/CoreModeManager.class',
        'Server/Languages/en-US/server.lang',
        'Server/Item/Items/Core/Core_Tavern.json',
        'Server/Item/Items/Core/Core_Kitchen.json',
        'Server/Item/Items/Core/Core_Bedroom.json',
        'Server/NPC/Roles/Taverns/Tavern_Patron.json',
        'Common/UI/Custom/Hud/TavernsRevision.ui',
        'prepared_foods.json',
        'comfort_registry.json',
        'rpg/catalog/skills.json',
        'rpg/catalog/passives.json',
        'rpg/presentation/icon-index.json',
        'Server/Particles/RPG/LightningSpire/Hywind_Lightning_Spire_Emergence.particlesystem',
        'Common/UI/Custom/Assets/SkillTree/skilltree_joint.png',
        'Common/UI/Custom/Assets/SkillTree/skilltree_passive_occupied.png',
        'Common/UI/Custom/Assets/SkillTree/skilltree_passive_unoccupied.png',
        'Common/UI/Custom/Assets/SkillTree/Slot@2x.png',
        'Common/UI/Custom/Assets/SkillTree/SpecialSlotTemporary@2x.png',
        'Common/UI/Custom/Assets/SkillTree/StructuralCraftingArrowUp@2x.png',
        'hywind-build.properties'
    )
    foreach ($requiredEntry in $required) {
        if ($null -eq $zip.GetEntry($requiredEntry)) { throw "Missing required merged entry: $requiredEntry" }
    }

    $repository = Split-Path $PSScriptRoot -Parent
    foreach($asset in @('skilltree_joint.png','skilltree_passive_occupied.png','skilltree_passive_unoccupied.png',
            'Slot@2x.png','SpecialSlotTemporary@2x.png','StructuralCraftingArrowUp@2x.png')){
        $relative='Common/UI/Custom/Assets/SkillTree/'+$asset
        $source=Join-Path $repository ('canvas-ui\src\main\resources\'+($relative -replace '/','\'))
        $entry=$zip.GetEntry($relative);$memory=[IO.MemoryStream]::new();$stream=$entry.Open()
        try{$stream.CopyTo($memory)}finally{$stream.Dispose()}
        if((&$bytesHash $memory.ToArray()) -ne (&$sha256 $source)){throw "Packaged Skill Tree asset hash mismatch: $asset"}
        $memory.Dispose()
    }

    # Owner-authored icon bytes must survive clean/build/package unchanged. art is the drop workflow;
    # src/main/resources is the canonical Gradle source and the JAR must match it exactly.
    $indexEntry = $zip.GetEntry('rpg/presentation/icon-index.json')
    $indexReader = [IO.StreamReader]::new($indexEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $iconIndex = $indexReader.ReadToEnd() | ConvertFrom-Json } finally { $indexReader.Dispose() }
    $iconRows = @{}; foreach($row in $iconIndex.entries){$iconRows[$row.fileName]=$row}
    $authoredIcons=0
    foreach($kind in @('Skill','Passive')){
        $artFolder=Join-Path $repository ('art\'+$kind+'s')
        if(-not(Test-Path -LiteralPath $artFolder -PathType Container)){continue}
        foreach($art in Get-ChildItem -LiteralPath $artFolder -File -Filter '*.png'){
            $row=$iconRows[$art.Name];if($null -eq $row -or $row.kind -ne $kind){throw "Unknown authored icon: $($art.Name)"}
            $uiRelative='Common/UI/Custom/Icons/RPG/'+$row.fileName
            $uiSource=Join-Path $repository ('src\main\resources\'+($uiRelative -replace '/','\'))
            if(-not(Test-Path -LiteralPath $uiSource -PathType Leaf)){throw "Missing canonical icon source: $uiRelative"}
            $sourceHash=&$sha256 $uiSource;$entry=$zip.GetEntry($uiRelative)
            if($null -eq $entry){throw "Missing packaged authored icon: $uiRelative"}
            $memory=[IO.MemoryStream]::new();$entryStream=$entry.Open();try{$entryStream.CopyTo($memory)}finally{$entryStream.Dispose()}
            if((&$bytesHash $memory.ToArray()) -ne $sourceHash){throw "Packaged icon hash mismatch: $uiRelative"};$memory.Dispose()
            if($kind -eq 'Skill'){
                $nativeRelative='Common/Icons/Items/RPG/'+$row.fileName
                $nativeSource=Join-Path $repository ('src\main\resources\'+($nativeRelative -replace '/','\'))
                if((-not (Test-Path -LiteralPath $nativeSource -PathType Leaf)) -or ((&$sha256 $nativeSource) -ne $sourceHash)){throw "Native canonical icon mismatch: $nativeRelative"}
                $nativeEntry=$zip.GetEntry($nativeRelative);if($null -eq $nativeEntry){throw "Missing packaged native icon: $nativeRelative"}
                $nativeMemory=[IO.MemoryStream]::new();$nativeStream=$nativeEntry.Open();try{$nativeStream.CopyTo($nativeMemory)}finally{$nativeStream.Dispose()}
                if((&$bytesHash $nativeMemory.ToArray()) -ne $sourceHash){throw "Packaged native icon hash mismatch: $nativeRelative"};$nativeMemory.Dispose()
            }
            $authoredIcons++
        }
    }
    $forbidden = @(
        'com/inigmasgames/hytalerpg/phase00/Phase00Plugin.class',
        'com/inigmasgames/canvasui/CanvasUIPlugin.class'
    )
    foreach ($forbiddenEntry in $forbidden) {
        if ($null -ne $zip.GetEntry($forbiddenEntry)) { throw "Legacy lifecycle owner was packaged: $forbiddenEntry" }
    }

    foreach ($page in @('NativeInventoryProbe','ProfileInventory')) {
        $prefix = "Common/UI/Custom/Pages/$page/NpcSection"
        $sections = @($names | Where-Object { $_ -match ('^' + [regex]::Escape($prefix) + '(\d+)\.ui$') })
        if ($sections.Count -ne 1024) { throw "$page must contain checked-in sections 1..8 and generated sections 9..1024; found $($sections.Count)." }
        foreach ($id in 1..1024) {
            if ($null -eq $zip.GetEntry("$prefix$id.ui")) { throw "$page is missing NpcSection$id.ui." }
        }
    }

    $embeddedBinaries = @($names | Where-Object {
        $_ -match '(?i)(^|/)(HytaleServer\.jar|Assets\.zip)$' -or
        $_ -match '(?i)\.(onnx|safetensors|ckpt|pt|pth)$'
    })
    if ($embeddedBinaries.Count) { throw "Forbidden embedded runtime/model payload: $($embeddedBinaries -join ', ')" }

    $badUi = [Collections.Generic.List[string]]::new()
    foreach ($entry in $zip.Entries | Where-Object { $_.FullName -like '*.ui' }) {
        $uiReader = [IO.StreamReader]::new($entry.Open(), [Text.UTF8Encoding]::new($false), $true)
        try { $body = $uiReader.ReadToEnd() } finally { $uiReader.Dispose() }
        if ($body -match 'CursedIcon(Patch|Anchor)') { $badUi.Add($entry.FullName) }
    }
    if ($badUi.Count) { throw "Pre-3 incompatible ItemGrid fields remain: $($badUi -join ', ')" }

    $languageEntry = $zip.GetEntry('Server/Languages/en-US/server.lang')
    $languageReader = [IO.StreamReader]::new($languageEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $language = $languageReader.ReadToEnd() } finally { $languageReader.Dispose() }
    foreach ($needle in @('items.RPG_Ability_', 'server.npcRoles.', 'items.Core_Tavern.name', 'items.Core_Kitchen.name', 'items.Core_Bedroom.name')) {
        if ($language -notmatch [regex]::Escape($needle)) { throw "Merged language file lacks $needle keys." }
    }

    $result = [ordered]@{
        result = 'PASS'
        jar = $resolvedJar
        sha256 = (& $sha256 $resolvedJar)
        bytes = (Get-Item -LiteralPath $resolvedJar).Length
        entries = $zip.Entries.Count
        classes = @($names | Where-Object { $_ -like '*.class' }).Count
        uiDocuments = @($names | Where-Object { $_ -like '*.ui' }).Count
        generatedNpcSections = 2032
        totalNpcSections = 2048
        manifest = "$($manifest.Group):$($manifest.Name)@$($manifest.Version)"
        revision = $manifest.Metadata.RpgRevision
        authoredIconHashesVerified = $authoredIcons
    }
    $result | ConvertTo-Json -Depth 4
} finally {
    $zip.Dispose()
}
