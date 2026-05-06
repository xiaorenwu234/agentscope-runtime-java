@echo off
REM A2A Cross-Framework Example - Quick Start Script for Windows
REM This script builds and starts all components

echo ========================================
echo A2A Cross-Framework Example
echo ========================================
echo.

REM Step 1: Build the project
echo Step 1: Building the project...
call mvn clean install -DskipTests
if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)
echo ✓ Build successful
echo.

REM Step 2: Start SAA A2A Server
echo Step 2: Starting Spring AI Alibaba A2A Server...
start "SAA A2A Server" cmd /k "cd saa-a2a-server && mvn spring-boot:run"
echo ✓ SAA Server starting in new window
echo.

REM Wait for SAA server to start
echo Waiting for SAA Server to be ready...
timeout /t 10 /nobreak >nul

REM Step 3: Start AgentScope A2A Server
echo Step 3: Starting AgentScope A2A Server...
start "AgentScope A2A Server" cmd /k "cd agentscope-a2a-server && mvn spring-boot:run"
echo ✓ AgentScope Server starting in new window
echo.

REM Wait for AgentScope server to start
echo Waiting for AgentScope Server to be ready...
timeout /t 10 /nobreak >nul

REM Step 4: Start Client
echo Step 4: Starting A2A Client Test...
echo.
cd a2a-client-test
call mvn exec:java
cd ..

echo.
echo All done! Close the server windows to stop the servers.
pause
