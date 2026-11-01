@echo off
rem Double-click: builds Itachi Voice and installs it on the TV. Log: work\itachi-voice\build.log
cd /d "%~dp0"
set "TV="
if exist "%~dp0..\tv-ip.txt" set /p TV=<"%~dp0..\tv-ip.txt"
if not defined TV set /p TV=TV IP address (TV Settings, Network): 
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-install.ps1" -TvHost %TV%
echo.
pause
