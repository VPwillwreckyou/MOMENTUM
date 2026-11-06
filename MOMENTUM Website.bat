@echo off
setlocal
set "ROOT=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\launch-app.ps1" -Browser
if errorlevel 1 (
  echo.
  echo MOMENTUM could not start. See the message above.
  pause
  exit /b 1
)
