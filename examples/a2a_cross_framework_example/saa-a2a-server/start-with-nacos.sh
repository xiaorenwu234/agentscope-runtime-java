#!/bin/bash

# ========================================
# SAA A2A Server 启动脚本（带Nacos注册）
# ========================================

# 设置环境变量（可选，修改为你的Nacos配置）
export NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-"localhost:8848"}
export NACOS_NAMESPACE=${NACOS_NAMESPACE:-"public"}
export NACOS_GROUP=${NACOS_GROUP:-"DEFAULT_GROUP"}
export NACOS_SERVICE_NAME=${NACOS_SERVICE_NAME:-"saa-a2a-server"}

# 检查 DashScope API Key
if [ -z "$DASHSCOPE_API_KEY" ]; then
    echo "错误: 请设置环境变量 DASHSCOPE_API_KEY"
    echo "例如: export DASHSCOPE_API_KEY=your-api-key"
    exit 1
fi

echo "=========================================="
echo "SAA A2A Server 启动"
echo "=========================================="
echo "Nacos Server: $NACOS_SERVER_ADDR"
echo "Nacos Namespace: $NACOS_NAMESPACE"
echo "Nacos Group: $NACOS_GROUP"
echo "Service Name: $NACOS_SERVICE_NAME"
echo "=========================================="
echo ""

# 编译项目
echo "正在编译项目..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "编译失败！"
    exit 1
fi

echo ""
echo "启动 SAA A2A Server..."
echo ""

# 运行项目
java -jar target/saa-a2a-server-1.0.0.jar
