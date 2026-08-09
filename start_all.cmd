@echo off
REM Launches backend (Spring Boot) and frontend (Vite) each in their own window.
echo Starting backend and frontend...

start "backend" cmd /k "cd /d %~dp0back_base_java && start.bat"
start "frontend" cmd /k "cd /d %~dp0front_base && npm run dev"

echo Both servers are starting in separate windows.
