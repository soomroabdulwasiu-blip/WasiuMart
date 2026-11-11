@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-WasiuMart.ps1"
if errorlevel 1 (
  echo.
  echo WasiuMart could not start. Please read the message above.
  pause
)
