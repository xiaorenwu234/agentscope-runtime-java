@echo off
REM A2A Communication Test - Windows 启动脚本
REM
REM 测试环境: 3节点6Agent集群
REM   Node-1: Agent-AS-1 (10001), Agent-AS-2 (10002)
REM   Node-2: Agent-AS-1 (10003), Agent-AS-2 (10004)
REM   Node-3: Agent-AS-1 (10005), Agent-AS-2 (10006)

echo ========================================================
echo   A2A 协议通信功能测试 - 启动脚本
echo ========================================================

if "%DASHSCOPE_API_KEY%"=="" (
    echo 错误: 请设置环境变量 DASHSCOPE_API_KEY
    echo   set DASHSCOPE_API_KEY=your-api-key
    exit /b 1
)

REM Nacos 配置（可通过环境变量覆盖）
if "%NACOS_SERVER_ADDR%"=="" set NACOS_SERVER_ADDR=localhost:8848
if "%NACOS_NAMESPACE%"=="" set NACOS_NAMESPACE=public
if "%NACOS_GROUP%"=="" set NACOS_GROUP=DEFAULT_GROUP

echo Nacos Server: %NACOS_SERVER_ADDR%
echo Nacos Namespace: %NACOS_NAMESPACE%
echo Nacos Group: %NACOS_GROUP%

echo.
echo 使用 PowerShell 启动 Agent 并自动清理（Ctrl+C 可停止所有进程）...
echo.

powershell -ExecutionPolicy Bypass -File "%~dp0run-test.ps1"

echo.
echo 所有进程已停止。
