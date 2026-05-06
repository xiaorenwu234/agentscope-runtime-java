#!/bin/bash

# A2A Cross-Framework Example - Quick Start Script
# This script builds and starts all components

set -e

echo "========================================"
echo "A2A Cross-Framework Example"
echo "========================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Build the project
echo -e "${YELLOW}Step 1: Building the project...${NC}"
mvn clean install -DskipTests
echo -e "${GREEN}✓ Build successful${NC}"
echo ""

# Step 2: Start SAA A2A Server
echo -e "${YELLOW}Step 2: Starting Spring AI Alibaba A2A Server...${NC}"
cd saa-a2a-server
mvn spring-boot:run &
SAA_PID=$!
cd ..
echo -e "${GREEN}✓ SAA Server starting (PID: $SAA_PID)${NC}"
echo ""

# Wait for SAA server to start
echo "Waiting for SAA Server to be ready..."
sleep 10

# Step 3: Start AgentScope A2A Server
echo -e "${YELLOW}Step 3: Starting AgentScope A2A Server...${NC}"
cd agentscope-a2a-server
mvn spring-boot:run &
AS_PID=$!
cd ..
echo -e "${GREEN}✓ AgentScope Server starting (PID: $AS_PID)${NC}"
echo ""

# Wait for AgentScope server to start
echo "Waiting for AgentScope Server to be ready..."
sleep 10

# Step 4: Start Client
echo -e "${YELLOW}Step 4: Starting A2A Client Test...${NC}"
echo ""
cd a2a-client-test
mvn exec:java
CLIENT_EXIT=$?
cd ..

# Cleanup
echo ""
echo "Shutting down servers..."
kill $SAA_PID 2>/dev/null || true
kill $AS_PID 2>/dev/null || true
echo "All servers stopped."

exit $CLIENT_EXIT
