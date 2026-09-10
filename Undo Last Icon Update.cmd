@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Update-RpgIcons.ps1" -RestoreLast
if errorlevel 1 echo RESTORE FAILED. Read the error above.
pause
