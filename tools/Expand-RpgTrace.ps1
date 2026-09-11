param(
    [Parameter(Mandatory=$true)][string]$TraceDirectory,
    [Parameter(Mandatory=$true)][ValidateSet('SKILL','UI')][string]$TraceKind,
    [Parameter(Mandatory=$true)][string]$Output,
    [string]$Jar
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
if([string]::IsNullOrWhiteSpace($Jar)){
    $candidate=Get-ChildItem -LiteralPath (Join-Path $repo 'build\libs') -Filter '*.jar' -File |
        Where-Object {$_.Name -notmatch 'sources|javadoc'} | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if($null -eq $candidate){throw 'Build the RPG JAR first or pass -Jar.'}
    $Jar=$candidate.FullName
}
& java -cp $Jar com.inigmasgames.hytalerpg.diagnostics.TraceArchiveExport $TraceDirectory $TraceKind $Output
if($LASTEXITCODE -ne 0){throw "Trace export failed with exit code $LASTEXITCODE"}
Get-Item -LiteralPath $Output | Select-Object FullName,Length,LastWriteTime
