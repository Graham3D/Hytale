param(
    [string]$ServerJar = "$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar",
    [string]$OutputDirectory = "$PSScriptRoot\..\..\build\native-patch"
)
$ErrorActionPreference='Stop'
function Get-Sha256([string]$path){
    $digest=[Security.Cryptography.SHA256]::Create()
    try{$stream=[IO.File]::OpenRead($path)
        try{return [BitConverter]::ToString($digest.ComputeHash($stream)).Replace('-','').ToLowerInvariant()}
        finally{$stream.Dispose()}}
    finally{$digest.Dispose()}
}
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$output=[IO.Path]::GetFullPath($OutputDirectory)
& (Join-Path $PSScriptRoot 'Build-PackboundNative.ps1') -ServerJar $ServerJar -OutputDirectory $output
if($LASTEXITCODE -ne 0){throw 'Native patch build failed'}
$record=Get-Content -LiteralPath (Join-Path $output 'verification.json') -Raw | ConvertFrom-Json
$patched=Join-Path $output 'HytaleServer-packbound.jar'
$original=Join-Path $output 'original\HytaleServer.jar'
if((Get-Sha256 $original) -ne $record.originalServerSha256){throw 'Original server record mismatch'}
if((Get-Sha256 $patched) -ne $record.patchedServerSha256){throw 'Patched server record mismatch'}
if(@($record.originalClassHashes).Count -ne 6 -or @($record.patchedClassHashes).Count -ne 6 -or
   @($record.addedHookClasses).Count -ne 11){throw 'Incomplete native verification record'}
$libRoot=Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.2.0-bin'
$asm=@(Get-ChildItem -LiteralPath $libRoot -Recurse -File -Filter 'asm-9.8.jar')
$tree=@(Get-ChildItem -LiteralPath $libRoot -Recurse -File -Filter 'asm-tree-9.8.jar')
if($asm.Count -ne 1 -or $tree.Count -ne 1){throw 'Pinned ASM libraries unavailable'}
$testClasses=Join-Path $output 'test-classes'
New-Item -ItemType Directory -Force $testClasses | Out-Null
$compilerClasspath="$(Join-Path $output 'classes');$original;$($asm[0].FullName);$($tree[0].FullName)"
& javac -cp $compilerClasspath -d $testClasses (Join-Path $PSScriptRoot 'test\NativePatchRegression.java')
if($LASTEXITCODE -ne 0){throw 'Native patch regression compiler failed'}
$runtimeClasspath="$testClasses;$(Join-Path $output 'classes');$patched;$($asm[0].FullName);$($tree[0].FullName)"
& java -Xverify:all -cp $runtimeClasspath com.inigmasgames.hytale.patch.NativePatchRegression $original $patched
if($LASTEXITCODE -ne 0){throw 'Native patch regression failed'}
Write-Output "NATIVE_PATCH_TEST_VERIFIED sha256=$($record.patchedServerSha256)"
