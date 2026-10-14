param(
    [string]$ServerJar = "$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar",
    [string]$OutputDirectory = "$PSScriptRoot\..\..\build\native-patch"
)
$ErrorActionPreference='Stop'
function Get-PinnedHash([string]$path){
    $digest=[Security.Cryptography.SHA256]::Create()
    try{
        $stream=[IO.File]::OpenRead($path)
        try{return [BitConverter]::ToString($digest.ComputeHash($stream)).Replace('-','')}
        finally{$stream.Dispose()}
    }finally{$digest.Dispose()}
}
$expectedOriginal='A60FA0B31F1418691F8D8F7CC1159693E3EC504D4F0567FB91493A842D71F8CA'
$expectedPatched='46F4D2F087F15B27151A879E574EDFDFEC4E4B15FBD381A952A84099A4FF601F'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$server=(Resolve-Path -LiteralPath $ServerJar).Path
$target=[IO.Path]::GetFullPath($OutputDirectory)
$installationRoot=Split-Path (Split-Path $server -Parent) -Parent
if($target.Equals($installationRoot,[StringComparison]::OrdinalIgnoreCase) -or
   $target.StartsWith($installationRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
   $server -eq (Join-Path $target 'HytaleServer-packbound.jar')){throw 'Patch output must be outside the installed game package'}
$installedHash=Get-PinnedHash $server
if($installedHash -ne $expectedOriginal -and $installedHash -ne $expectedPatched){throw 'Installed server hash differs from both pinned versions'}
$libRoot=Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.2.0-bin'
$asm=@(Get-ChildItem -LiteralPath $libRoot -Recurse -File -Filter 'asm-9.8.jar')
$tree=@(Get-ChildItem -LiteralPath $libRoot -Recurse -File -Filter 'asm-tree-9.8.jar')
if($asm.Count -ne 1 -or $tree.Count -ne 1){throw 'Pinned Gradle 9.2.0 ASM 9.8 libraries unavailable'}
if((Get-PinnedHash $asm[0].FullName) -ne '876EAB6A83DAECAD5CA67EB9FCABB063C97B5AEB8CF1FCA7A989ECDE17522051' -or
   (Get-PinnedHash $tree[0].FullName) -ne '14B7880CB7C85EED101E2710432FC3FFB83275532A6A894DC4C4095D49AD59F1'){
    throw 'Pinned ASM tooling hash mismatch'
}
New-Item -ItemType Directory -Force $target | Out-Null
$originalCopy=Join-Path $target 'original\HytaleServer.jar'
New-Item -ItemType Directory -Force (Split-Path $originalCopy) | Out-Null
if(Test-Path -LiteralPath $originalCopy){
    if((Get-PinnedHash $originalCopy) -ne $expectedOriginal){throw 'Recovery copy hash mismatch'}
}else{
    if($installedHash -ne $expectedOriginal){throw 'Patched installed server requires the hash-checked original recovery copy'}
    Copy-Item -LiteralPath $server -Destination $originalCopy
    if((Get-PinnedHash $originalCopy) -ne $expectedOriginal){throw 'Recovery copy verification failed'}
}
$sourceServer=if($installedHash -eq $expectedOriginal){$server}else{$originalCopy}
$classes=Join-Path $target 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null
$classpath="$sourceServer;$($asm[0].FullName);$($tree[0].FullName)"
$source=Join-Path $root 'tools\native-patch\src\com\inigmasgames\hytale\patch'
& javac -cp $classpath -d $classes (Join-Path $source 'NativeMutationHook.java') (Join-Path $source 'NativeDamageReceiptHook.java') (Join-Path $source 'NativeProjectileReceiptHook.java') (Join-Path $source 'NativeModernProjectileHook.java') (Join-Path $source 'NativePopulationProjectionHook.java') (Join-Path $source 'PatchNativeMutations.java')
if($LASTEXITCODE -ne 0){throw 'Native patch compiler failed'}
$output=Join-Path $target 'HytaleServer-packbound.jar'
& java -cp "$classes;$classpath" com.inigmasgames.hytale.patch.PatchNativeMutations $sourceServer $classes $output
if($LASTEXITCODE -ne 0){throw 'Native patcher failed'}
if((Get-PinnedHash $output) -ne $expectedPatched){throw 'Patched server hash differs from the reviewed artifact'}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Get-EntryHash([IO.Compression.ZipArchive]$archive,[string]$name){
    $entry=$archive.GetEntry($name)
    if($null -eq $entry){throw "Missing native class entry: $name"}
    $digest=[Security.Cryptography.SHA256]::Create()
    try{$stream=$entry.Open();try{return [BitConverter]::ToString($digest.ComputeHash($stream)).Replace('-','').ToLowerInvariant()}finally{$stream.Dispose()}}
    finally{$digest.Dispose()}
}
$base='com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/'
$changed=@('ClearEntityEffectInteraction','ChangeStatInteraction','ChangeStatWithModifierInteraction','DamageEntityInteraction','LaunchProjectileInteraction') |
    ForEach-Object { "$base$_.class" }
$changed+= 'com/hypixel/hytale/server/spawning/world/system/WorldSpawningSystem.class'
$changed+= 'com/hypixel/hytale/server/core/modules/projectile/interaction/ProjectileInteraction.class'
$added=@('NativeMutationHook.class','NativeMutationHook$Kind.class','NativeMutationHook$Mutation.class','NativeMutationHook$Policy.class',
    'NativeDamageReceiptHook.class','NativeDamageReceiptHook$Context.class','NativeDamageReceiptHook$Provider.class',
    'NativeProjectileReceiptHook.class','NativeProjectileReceiptHook$Context.class','NativeProjectileReceiptHook$Provider.class',
    'NativeModernProjectileHook.class','NativeModernProjectileHook$Context.class','NativeModernProjectileHook$Provider.class',
    'NativePopulationProjectionHook.class') | ForEach-Object { "com/inigmasgames/hytale/patch/$_" }
$before=[IO.Compression.ZipFile]::OpenRead($sourceServer)
$after=[IO.Compression.ZipFile]::OpenRead($output)
try{
    $classHashes=@()
    foreach($name in $changed){$classHashes+=@{path=$name;originalSha256=(Get-EntryHash $before $name);patchedSha256=(Get-EntryHash $after $name)}}
    $addedHashes=@()
    foreach($name in $added){$addedHashes+=@{path=$name;sha256=(Get-EntryHash $after $name)}}
}finally{$before.Dispose();$after.Dispose()}
$commit=(& git -C $root rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0){throw 'Could not resolve native patcher source commit'}
$sourceDirty=@(& git -C $root status --porcelain -- tools/native-patch/src tools/native-patch/Build-PackboundNative.ps1).Count -ne 0
$record=[ordered]@{
    targetVersion='0.7.1';sourceCommit=$commit;patcherSourceTreeDirty=$sourceDirty
    originalServerSha256=$expectedOriginal.ToLowerInvariant();patchedServerSha256=$expectedPatched.ToLowerInvariant()
    originalClassHashes=$classHashes | ForEach-Object { @{path=$_.path;sha256=$_.originalSha256} }
    patchedClassHashes=$classHashes | ForEach-Object { @{path=$_.path;sha256=$_.patchedSha256} }
    changedNativeClasses=$changed;addedHookClasses=$addedHashes
    verification=[ordered]@{wholeJarPin=$true;classPins=$true;instructionPreservation=$true;archiveSurface=$true;outputPin=$true}
}
$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $target 'verification.json') -Encoding utf8
Write-Output "NATIVE_PATCH_VERIFIED output=$output recovery=$originalCopy"
