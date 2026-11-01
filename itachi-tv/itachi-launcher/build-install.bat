@echo off
rem Double-click: builds the Itachi launcher, installs it on the TV and pushes the theme. Log: work\itachi-launcher\build.log
cd /d "%~dp0"
set "TV="
if exist "%~dp0..\tv-ip.txt" set /p TV=<"%~dp0..\tv-ip.txt"
if not defined TV set /p TV=TV IP address (TV Settings, Network): 
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-install.ps1" -TvHost %TV%
echo.
pause
