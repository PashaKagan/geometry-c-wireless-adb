@echo off
rem Launches the Geely Stick Builder GUI as Administrator (formatting needs admin)
rem and bypasses execution policy for this one script only.
setlocal
set "PS1=%~dp0GeelyStickBuilder.ps1"
powershell -NoProfile -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','%PS1%'"
endlocal
