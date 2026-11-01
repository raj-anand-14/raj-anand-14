@echo off
rem Double-click: sends your Claude API key to Itachi Voice on the TV. The key is not written to any file.
cd /d "%~dp0..\.."
set "TV="
if exist "%~dp0..\tv-ip.txt" set /p TV=<"%~dp0..\tv-ip.txt"
if not defined TV set /p TV=TV IP address (TV Settings, Network): 
set TV_HOST=%TV%
echo Paste your Claude API key (starts with sk-ant-) and press Enter.
echo Right-click pastes in this window.
set /p KEY=Key: 
if "%KEY%"=="" (echo No key entered. & pause & exit /b 1)
tools\venv\Scripts\python.exe -I tools\tv.py "am broadcast -n com.itachi.voice/.SetupReceiver --es api_key %KEY%"
set KEY=
echo.
echo If you see "saved: key" above, it worked. Open Itachi Voice on the TV and press OK.
pause
