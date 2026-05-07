# A2A Communication Test - PowerShell 启动脚本
# 使用 try/finally 确保 Ctrl+C 时自动停止所有 Agent 进程

$ErrorActionPreference = "Continue"

# Nacos 配置（可通过环境变量覆盖）
$nacosAddr = if ($env:NACOS_SERVER_ADDR) { $env:NACOS_SERVER_ADDR } else { "localhost:8848" }
$nacosNamespace = if ($env:NACOS_NAMESPACE) { $env:NACOS_NAMESPACE } else { "public" }
$nacosGroup = if ($env:NACOS_GROUP) { $env:NACOS_GROUP } else { "DEFAULT_GROUP" }

Write-Host "Nacos Server: $nacosAddr"
Write-Host "Nacos Namespace: $nacosNamespace"
Write-Host "Nacos Group: $nacosGroup"

# Agent 集群定义
$agents = @(
    @{ Port = 10001; Name = "node-1:Agent-AS-1" }
    @{ Port = 10002; Name = "node-1:Agent-AS-2" }
    @{ Port = 10003; Name = "node-2:Agent-AS-1" }
    @{ Port = 10004; Name = "node-2:Agent-AS-2" }
    @{ Port = 10005; Name = "node-3:Agent-AS-1" }
    @{ Port = 10006; Name = "node-3:Agent-AS-2" }
)

# 存储启动的进程
$processes = @()

try {
    # 1. 构建 Maven 项目（可选，已构建可跳过）
    # Write-Host ""
    # Write-Host "1. 构建 Maven 项目..."
    # mvn clean package -DskipTests -q

    # 2. 启动 6 个 Agent 实例并注册到 Nacos
    Write-Host ""
    Write-Host "2. 启动 6 个 Agent 实例并注册到 Nacos..."

    $jarPath = "agent-server\target\agent-server-1.0.0.jar"
    if (-not (Test-Path $jarPath)) {
        Write-Host "错误: 找不到 $jarPath，请先运行 mvn clean package -DskipTests"
        exit 1
    }

    foreach ($agent in $agents) {
        $args = @("-jar", $jarPath, "--port=$($agent.Port)", "--name=$($agent.Name)", "--nacos-addr=$nacosAddr")
        $proc = Start-Process -FilePath "java" -ArgumentList $args -PassThru -NoNewWindow
        $processes += $proc
        Write-Host "  [PID:$($proc.Id)] $($agent.Name) (端口 $($agent.Port))"
    }

    # 等待服务启动并注册到 Nacos
    Write-Host ""
    Write-Host "等待 Agent 服务启动并注册到 Nacos (25秒)..."
    Start-Sleep -Seconds 25
    Write-Host "所有 Agent 已启动。"

    # 3. 运行 A2A 通信测试
    Write-Host ""
    Write-Host "3. 运行 A2A 通信测试 (通过 Nacos 服务发现)..."
    Write-Host "========================================================"
    Write-Host ""

    $env:EMBEDDED_MODE = "false"
    $env:AGENT_HOST = "localhost"
    $env:NACOS_SERVER_ADDR = $nacosAddr
    $env:NACOS_NAMESPACE = $nacosNamespace
    $env:NACOS_GROUP = $nacosGroup

    Push-Location test-runner
    cmd /c 'mvn exec:java -Dexec.mainClass=io.agentscope.examples.test.A2aCommunicationTest'
    Pop-Location

    Write-Host ""
    Write-Host "测试完成。"

} finally {
    # 无论正常结束还是 Ctrl+C，都会执行此块，停止所有 Agent 进程
    Write-Host ""
    Write-Host "正在停止 Agent 进程..."

    foreach ($proc in $processes) {
        try {
            if (!$proc.HasExited) {
                # 先尝试正常终止，再强制杀掉整个进程树
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
                # 同时杀掉所有子进程（Java 可能 fork 了子进程）
                Get-CimInstance Win32_Process -Filter "ParentProcessId=$($proc.Id)" |
                    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
            }
        } catch {
            # 忽略已退出的进程
        }
    }

    Write-Host "所有 Agent 进程已停止。"
}
