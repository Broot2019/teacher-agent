@echo off
chcp 65001 >nul 2>&1
title Teacher Agent - Frontend
cd /d "%~dp0frontend"
echo [Frontend] Starting dev server ...
echo [Frontend] http://localhost:5173
echo [Frontend] Press Ctrl+C to stop
echo.
call npm run dev
pause
