@echo off
chcp 65001 >nul 2>&1
title Teacher Agent - Backend
cd /d "%~dp0backend"
echo [Backend] Starting on port 8089 ...
echo [Backend] Press Ctrl+C to stop
echo.
call mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx512m -Dfile.encoding=UTF-8 -Dnative.encoding=UTF-8"
pause
