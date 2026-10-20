$ErrorActionPreference='Stop'
$save='C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG'
$destination=Join-Path $save 'mods\Hywind.jar'
$source='C:\Users\Zemio\.codex\worktrees\hyarpg-separation\build\libs\HyARPG.jar'
$native='C:\Users\Zemio\AppData\Roaming\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar'
$config=Join-Path $save 'world-config.json'
$expectedOld='819CC21B80E49EF10FF079361C6B24F413ABA3A6DBB8DFC0D5EEDFC4E8667599'
$expectedNative='A032A64E03390CA0AEB6C03C3B1600EAA892C8BC833D1BF3AC39307CCDF3CB20'
$expectedConfig='3D7E86AB1B97F36E41F124FD3E03C682923F77F59B898FCE8CA0221542DF7986'
if((Get-Process -Name 'Hytale','HytaleServer' -ErrorAction SilentlyContinue)){throw 'Hytale is running; deployment refused'}
$javaServer=Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -match 'HytaleServer\.jar' -and $_.CommandLine -notmatch 'GradleWorkerMain' }
if($javaServer){throw 'Hytale server is running; deployment refused'}
if(!(Test-Path -LiteralPath $source -PathType Leaf)){throw 'Candidate JAR missing'}
if((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $expectedOld){throw 'Deployed JAR differs from pinned R247 baseline'}
if((Get-FileHash -LiteralPath $native -Algorithm SHA256).Hash -ne $expectedNative){throw 'Installed native patch hash mismatch'}
if((Get-FileHash -LiteralPath $config -Algorithm SHA256).Hash -ne $expectedConfig){throw 'World configuration changed during R248 work'}
$candidate=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
$backupDir='C:\Users\Zemio\.codex\recovery\Hywind'
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$backup=Join-Path $backupDir ('Hywind-R247-before-R248-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'.jar')
Copy-Item -LiteralPath $destination -Destination $backup
if((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $expectedOld){throw 'Backup checksum mismatch'}
$staged=Join-Path (Split-Path -Parent $destination) ('Hywind-R248-'+[Guid]::NewGuid().ToString('N')+'.deploying')
Copy-Item -LiteralPath $source -Destination $staged
if((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash -ne $candidate){throw 'Staged checksum mismatch'}
$replacementBackup=Join-Path $backupDir ('Hywind-R247-replace-R248-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'.jar')
[System.IO.File]::Replace($staged,$destination,$replacementBackup)
if((Get-FileHash -LiteralPath $replacementBackup -Algorithm SHA256).Hash -ne $expectedOld){throw 'Replacement backup checksum mismatch'}
$installed=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
if($installed -ne $candidate){throw 'Installed candidate checksum mismatch'}
if((Get-FileHash -LiteralPath $config -Algorithm SHA256).Hash -ne $expectedConfig){throw 'World configuration changed during deployment'}
Write-Output "R248_DEPLOYED_SHA256=$installed"
Write-Output "R248_BACKUP=$backup"
Write-Output "R248_NATIVE_PATCH_SHA256=$expectedNative"
Write-Output "R248_WORLD_CONFIG_SHA256=$expectedConfig"
