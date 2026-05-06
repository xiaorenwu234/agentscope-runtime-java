#!/bin/bash

# Multi-Agent Orchestration Demo 一键启动脚本
# 用法: ./run-demo.sh [--skip-build] [--with-master]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 参数解析
SKIP_BUILD=false
WITH_MASTER=false

for arg in "$@"; do
    case $arg in
        --skip-build)
            SKIP_BUILD=true
            ;;
        --with-master)
            WITH_MASTER=true
            ;;
        --help|-h)
            echo "用法: ./run-demo.sh [选项]"
            echo ""
            echo "选项:"
            echo "  --skip-build    跳过 Maven 编译"
            echo "  --with-master   同时启动 Master Agent"
            echo "  --help, -h      显示帮助信息"
            exit 0
            ;;
    esac
done

# 存储后台进程 PID
PIDS=()

# 清理函数
cleanup() {
    echo ""
    echo -e "${YELLOW}正在停止所有 Agent 进程...${NC}"
    for pid in "${PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null || true
        fi
    done
    # 等待进程结束
    sleep 2
    echo -e "${GREEN}所有进程已停止${NC}"
}

# 注册清理函数
trap cleanup EXIT INT TERM

echo -e "${BLUE}==========================================${NC}"
echo -e "${BLUE}  Multi-Agent Orchestration Demo 启动器${NC}"
echo -e "${BLUE}==========================================${NC}"
echo ""

# 检查 Nacos
echo -e "${YELLOW}[1/5] 检查 Nacos 服务...${NC}"
if curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=test" > /dev/null 2>&1; then
    echo -e "${GREEN}  ✓ Nacos 服务正常运行${NC}"
else
    echo -e "${RED}  ✗ Nacos 服务未运行！${NC}"
    echo -e "${YELLOW}  请先启动 Nacos: docker run -d -p 8848:8848 -p 9848:9848 nacos/nacos-server:latest${NC}"
    exit 1
fi

# 编译项目
if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo -e "${YELLOW}[2/5] 编译项目...${NC}"
    cd "$PROJECT_ROOT"
    mvn clean install -DskipTests -q
    echo -e "${GREEN}  ✓ 编译完成${NC}"
else
    echo ""
    echo -e "${YELLOW}[2/5] 跳过编译${NC}"
fi

cd "$SCRIPT_DIR"

# 启动 Worker Agents
echo ""
echo -e "${YELLOW}[3/5] 启动 Worker Agents...${NC}"

# Translator Agent (端口 8091)
echo -e "  启动 Translator Agent (端口 8091)..."
mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
    -Dserver.port=8091 \
    -Dexec.args="--port=8091 --agent-name=translator --agent-type=translator" \
    -q > /tmp/translator.log 2>&1 &
PIDS+=($!)
sleep 2

# Analyzer Agent (端口 8092)
echo -e "  启动 Analyzer Agent (端口 8092)..."
mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
    -Dserver.port=8092 \
    -Dexec.args="--port=8092 --agent-name=analyzer --agent-type=analyzer" \
    -q > /tmp/analyzer.log 2>&1 &
PIDS+=($!)
sleep 2

# Summarizer Agent (端口 8093)
echo -e "  启动 Summarizer Agent (端口 8093)..."
mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
    -Dserver.port=8093 \
    -Dexec.args="--port=8093 --agent-name=summarizer --agent-type=summarizer" \
    -q > /tmp/summarizer.log 2>&1 &
PIDS+=($!)
sleep 2

echo -e "${GREEN}  ✓ 3 个 Worker Agents 已启动${NC}"

# 启动 Master Agent (可选)
if [ "$WITH_MASTER" = true ]; then
    echo ""
    echo -e "${YELLOW}[4/5] 启动 Master Agent (端口 8090)...${NC}"
    mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.MasterAgentApplication" \
        -Dserver.port=8090 \
        -Dexec.args="--port=8090" \
        -q > /tmp/master.log 2>&1 &
    PIDS+=($!)
    sleep 3
    echo -e "${GREEN}  ✓ Master Agent 已启动${NC}"
else
    echo ""
    echo -e "${YELLOW}[4/5] 跳过 Master Agent (使用 --with-master 启用)${NC}"
fi

# 等待服务注册到 Nacos
echo ""
echo -e "${YELLOW}等待 Agents 注册到 Nacos (5秒)...${NC}"
sleep 5

# 运行 Demo
echo ""
echo -e "${YELLOW}[5/5] 运行 Orchestration Demo...${NC}"
echo -e "${BLUE}==========================================${NC}"
echo ""

mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.OrchestrationDemo" -q

echo ""
echo -e "${BLUE}==========================================${NC}"
echo -e "${GREEN}Demo 执行完成！${NC}"
echo ""
echo -e "${YELLOW}日志文件位置:${NC}"
echo "  - Translator: /tmp/translator.log"
echo "  - Analyzer:   /tmp/analyzer.log"
echo "  - Summarizer: /tmp/summarizer.log"
if [ "$WITH_MASTER" = true ]; then
    echo "  - Master:     /tmp/master.log"
fi
echo ""
echo -e "${YELLOW}按 Ctrl+C 停止所有 Agent 进程${NC}"

# 保持脚本运行，等待用户中断
wait
