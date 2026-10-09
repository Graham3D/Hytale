param(
    [string]$ServerJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar",
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
$expectedOriginal='35A34A32175CD92CE5E2A51310953DB3A4A64CAC89D995CBD4830C52A9B1B904'
$expectedPatched='6F4233203E804D6B0071A6416D26DD867F5CFB0C60D3E78B69A6A91415C1A59E'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$server=(Resolve-Path -LiteralPath $ServerJar).Path
$target=[IO.Path]::GetFullPath($OutputDirectory)
if($server -eq (Join-Path $target 'HytaleServer-packbound.jar')){throw 'Patch output must differ from the installed server'}
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
& javac -cp $classpath -d $classes (Join-Path $source 'NativeMutationHook.java') (Join-Path $source 'NativeDamageReceiptHook.java') (Join-Path $source 'NativeProjectileReceiptHook.java') (Join-Path $source 'PatchNativeMutations.java')
if($LASTEXITCODE -ne 0){throw 'Native patch compiler failed'}
$output=Join-Path $target 'HytaleServer-packbound.jar'
& java -cp "$classes;$classpath" com.inigmasgames.hytale.patch.PatchNativeMutations $sourceServer $classes $output
if($LASTEXITCODE -ne 0){throw 'Native patcher failed'}
if((Get-PinnedHash $output) -ne $expectedPatched){throw 'Patched server hash differs from the reviewed artifact'}
Write-Output "NATIVE_PATCH_VERIFIED output=$output recovery=$originalCopy"
