@echo off
chcp 65001 >nul 2>&1
title Teacher Agent - Stop All

echo [Stop] Stopping backend (port 8089) ...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8089 ^| findstr LISTENING') do (
    taskkill /PID %%a /F >nul 2>&1
    echo [Stop] Killed PID %%a
)

echo [Stop] Stopping frontend (port 5173) ...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5173 ^| findstr LISTENING') do (
    taskkill /PID %%a /F >nul 2>&1
    echo [Stop] Killed PID %%a
)

echo.
echo [Stop] All services stopped.
pause
