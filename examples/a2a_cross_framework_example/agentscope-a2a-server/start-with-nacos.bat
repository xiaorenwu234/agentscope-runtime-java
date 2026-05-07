@echo off
REM ========================================
REM AgentScope A2A Server 启动脚本（带Nacos注册）
REM ========================================

REM 设置环境变量（可选，修改为你的Nacos配置）
if not defined NACOS_SERVER_ADDR set NACOS_SERVER_ADDR=localhost:8848
if not defined NACOS_NAMESPACE set NACOS_NAMESPACE=public
if not defined NACOS_GROUP set NACOS_GROUP=DEFAULT_GROUP
if not defined NACOS_SERVICE_NAME set NACOS_SERVICE_NAME=agentscope-a2a-server

REM 检查 DashScope API Key
if not defined DASHSCOPE_API_KEY (
    echo 错误: 请设置环境变量 DASHSCOPE_API_KEY
    echo 例如: set DASHSCOPE_API_KEY=your-api-key
    exit /b 1
)

echo ==========================================
echo AgentScope A2A Server 启动
echo ==========================================
echo Nacos Server: %NACOS_SERVER_ADDR%
echo Nacos Namespace: %NACOS_NAMESPACE%
echo Nacos Group: %NACOS_GROUP%
echo Service Name: %NACOS_SERVICE_NAME%
echo ==========================================
echo.

REM 编译项目
echo 正在编译项目...
call mvn clean package -DskipTests

if errorlevel 1 (
    echo 编译失败！
    exit /b 1
)

echo.
echo 启动 AgentScope A2A Server...
echo.

REM 运行项目
java -jar target\agentscope-a2a-server-1.0.0.jar
