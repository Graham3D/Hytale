@echo off
setlocal
echo Close Hytale before installing your artwork.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Update-RpgIcons.ps1"
if errorlevel 1 echo UPDATE FAILED. Read the error above; do not start the game until you understand it.
pause
