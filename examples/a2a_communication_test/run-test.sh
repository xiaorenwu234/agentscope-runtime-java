#!/bin/bash
# A2A Communication Test - 启动脚本
# 使用方法: ./run-test.sh
#
# 测试环境: 3节点6Agent集群
#   Node-1: Agent-AS-1 (10001), Agent-AS-2 (10002)
#   Node-2: Agent-AS-1 (10003), Agent-AS-2 (10004)
#   Node-3: Agent-AS-1 (10005), Agent-AS-2 (10006)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 所有 Agent 进程 PID
AGENT_PIDS=()

# 清理函数：停止所有 Agent 进程
cleanup() {
    echo ""
    echo "正在停止 Agent 服务..."
    for PID in "${AGENT_PIDS[@]}"; do
        kill "$PID" 2>/dev/null || true
        # 等待进程退出，超时后强制杀掉
        ( wait "$PID" 2>/dev/null ) &
        local timeout=5
        while [ $timeout -gt 0 ]; do
            if ! kill -0 "$PID" 2>/dev/null; then
                break
            fi
            sleep 1
            timeout=$((timeout - 1))
        done
        # 超时后强制杀掉
        if kill -0 "$PID" 2>/dev/null; then
            kill -9 "$PID" 2>/dev/null || true
        fi
    done
    echo "所有 Agent 服务已停止。"
}

# 捕获 Ctrl+C (SIGINT) 和正常退出，确保清理
trap cleanup EXIT INT TERM

echo "========================================================"
echo "  A2A 协议通信功能测试 - 启动脚本"
echo "========================================================"

# 检查 DASHSCOPE_API_KEY
if [ -z "$DASHSCOPE_API_KEY" ]; then
    echo "错误: 请设置环境变量 DASHSCOPE_API_KEY"
    echo "  export DASHSCOPE_API_KEY=your-api-key"
    exit 1
fi

# Nacos 配置（可通过环境变量覆盖）
export NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-localhost:8848}
export NACOS_NAMESPACE=${NACOS_NAMESPACE:-public}
export NACOS_GROUP=${NACOS_GROUP:-DEFAULT_GROUP}

echo "Nacos Server: $NACOS_SERVER_ADDR"
echo "Nacos Namespace: $NACOS_NAMESPACE"
echo "Nacos Group: $NACOS_GROUP"

echo ""
echo "1. 构建 Maven 项目..."
mvn clean package -DskipTests -q

echo ""
echo "2. 启动 6 个 Agent 实例并注册到 Nacos..."

# Node-1
java -jar agent-server/target/agent-server-1.0.0.jar --port=10001 --name=node-1:Agent-AS-1 --nacos-addr=$NACOS_SERVER_ADDR &
AGENT_PIDS+=($!)

java -jar agent-server/target/agent-server-1.0.0.jar --port=10002 --name=node-1:Agent-AS-2 --nacos-addr=$NACOS_SERVER_ADDR &
AGENT_PIDS+=($!)

# Node-2
java -jar agent-server/target/agent-server-1.0.0.jar --port=10003 --name=node-2:Agent-AS-1 --nacos-addr=$NACOS_SERVER_ADDR &
AGENT_PIDS+=($!)

java -jar agent-server/target/agent-server-1.0.0.jar --port=10004 --name=node-2:Agent-AS-2 --nacos-addr=$NACOS_SERVER_ADDR &
AGENT_PIDS+=($!)

# Node-3
java -jar agent-server/target/agent-server-1.0.0.jar --port=10005 --name=node-3:Agent-AS-1 --nacos-addr=$NACOS_SERVER_ADDR &
AGENT_PIDS+=($!)

java -jar agent-server/target/agent-server-1.0.0.jar --port=10006 --name=node-3:Agent-AS-2 --nacos-addr=$NACOS_SERVER_ADDR &
AGENT_PIDS+=($!)

for i in "${!AGENT_PIDS[@]}"; do
    echo "  PID: ${AGENT_PIDS[$i]}"
done

echo ""
echo "等待 Agent 服务启动并注册到 Nacos (25秒)..."
sleep 25

echo ""
echo "3. 运行 A2A 通信测试 (通过 Nacos 服务发现)..."
echo "========================================================"
echo ""

# 使用外部模式运行测试
export EMBEDDED_MODE=false
export AGENT_HOST=localhost

cd test-runner
mvn exec:java -Dexec.mainClass="io.agentscope.examples.test.A2aCommunicationTest"
cd ..

echo ""
echo "测试完成。"
# cleanup 会在 EXIT trap 中自动调用
