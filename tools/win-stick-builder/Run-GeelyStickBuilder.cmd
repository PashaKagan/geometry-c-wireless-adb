@echo off
rem Launches the Geely Stick Builder GUI as Administrator (formatting needs admin),
rem bypasses execution policy for this one script, and keeps the window open so any
rem error stays visible instead of flashing closed. Handles paths with spaces.
setlocal
set "PS1=%~dp0GeelyStickBuilder.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell.exe -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -NoExit -File \"%PS1%\"'"
endlocal
