import React, { useState, useCallback, useEffect } from 'react';
import { ConfigProvider, theme, Typography, Card, Row, Col, message, Tag, Switch } from 'antd';
import { motion } from 'framer-motion';
import { ApiOutlined, CloudOutlined, CloudSyncOutlined } from '@ant-design/icons';
import AgentTopology from './components/AgentTopology';
import ModeComparison from './components/ModeComparison';
import TaskTimeline from './components/Timeline';
import ControlPanel from './components/ControlPanel';
import { getAgents, executeDemo, healthCheck } from './api';
import type { AgentInfo, OrchestrationMode, TaskRecord, TaskEvent, MessageFlow } from './types';
import './App.css';

const { Title, Text } = Typography;

// 模拟 Agent 数据（用于离线/演示模式）
const mockAgents: AgentInfo[] = [
  { id: 'master', name: 'Master', type: 'master', status: 'online', host: 'localhost', port: 8091 },
  { id: 'translator', name: 'Translator', type: 'translator', status: 'online', host: 'localhost', port: 8092 },
  { id: 'analyzer', name: 'Analyzer', type: 'analyzer', status: 'online', host: 'localhost', port: 8093 },
  { id: 'summarizer', name: 'Summarizer', type: 'summarizer', status: 'online', host: 'localhost', port: 8094 },
];

// 生成模拟任务事件
const generateMockEvents = (mode: OrchestrationMode, input: string): TaskEvent[] => {
  const now = Date.now();
  const workers = ['translator', 'analyzer', 'summarizer'];
  const events: TaskEvent[] = [];
  let offset = 0;

  // 请求接收
  events.push({
    id: `evt-${offset}`,
    timestamp: now + offset,
    type: 'request_received',
    message: `收到请求: "${input.substring(0, 30)}${input.length > 30 ? '...' : ''}"`,
  });
  offset += 50;

  if (mode === 'fanout' || mode === 'scatter-gather') {
    // 并行分发
    events.push({
      id: `evt-${offset}`,
      timestamp: now + offset,
      type: 'task_dispatched',
      message: `并行分发任务到 ${workers.length} 个 Worker`,
    });
    offset += 30;

    // 所有 Worker 同时开始
    workers.forEach((worker, idx) => {
      events.push({
        id: `evt-start-${idx}`,
        timestamp: now + offset + idx * 10,
        type: 'worker_start',
        agentId: worker,
        agentName: worker.charAt(0).toUpperCase() + worker.slice(1),
        message: `开始处理任务`,
      });
    });
    offset += 100;

    // Worker 完成（随机顺序）
    const completionDelays = [120, 180, 150];
    workers.forEach((worker, idx) => {
      events.push({
        id: `evt-complete-${idx}`,
        timestamp: now + offset + completionDelays[idx],
        type: 'worker_complete',
        agentId: worker,
        agentName: worker.charAt(0).toUpperCase() + worker.slice(1),
        message: `处理完成`,
        details: `处理耗时: ${completionDelays[idx]}ms`,
      });
    });
    offset += 200;

  } else if (mode === 'pipeline') {
    // 串行流水线
    workers.forEach((worker, idx) => {
      events.push({
        id: `evt-dispatch-${idx}`,
        timestamp: now + offset,
        type: 'task_dispatched',
        message: `传递任务到 ${worker.charAt(0).toUpperCase() + worker.slice(1)}`,
      });
      offset += 20;

      events.push({
        id: `evt-start-${idx}`,
        timestamp: now + offset,
        type: 'worker_start',
        agentId: worker,
        agentName: worker.charAt(0).toUpperCase() + worker.slice(1),
        message: `开始处理`,
      });
      offset += 100;

      events.push({
        id: `evt-complete-${idx}`,
        timestamp: now + offset,
        type: 'worker_complete',
        agentId: worker,
        agentName: worker.charAt(0).toUpperCase() + worker.slice(1),
        message: `处理完成，${idx < workers.length - 1 ? '传递给下一个' : '返回结果'}`,
      });
      offset += 20;
    });

  } else if (mode === 'master-slave') {
    // Master-Slave 模式
    events.push({
      id: `evt-${offset}`,
      timestamp: now + offset,
      type: 'task_dispatched',
      message: `Master 协调分配任务`,
    });
    offset += 50;

    workers.forEach((worker, idx) => {
      events.push({
        id: `evt-start-${idx}`,
        timestamp: now + offset,
        type: 'worker_start',
        agentId: worker,
        agentName: worker.charAt(0).toUpperCase() + worker.slice(1),
        message: `Slave 开始执行`,
      });
    });
    offset += 150;

    workers.forEach((worker, idx) => {
      events.push({
        id: `evt-complete-${idx}`,
        timestamp: now + offset + idx * 30,
        type: 'worker_complete',
        agentId: worker,
        agentName: worker.charAt(0).toUpperCase() + worker.slice(1),
        message: `向 Master 报告结果`,
      });
    });
    offset += 120;
  }

  // 结果聚合
  events.push({
    id: `evt-aggregate`,
    timestamp: now + offset,
    type: 'result_aggregated',
    message: `聚合 ${workers.length} 个 Worker 的结果`,
  });
  offset += 30;

  // 响应返回
  events.push({
    id: `evt-response`,
    timestamp: now + offset,
    type: 'response_sent',
    message: `返回最终结果`,
  });

  return events;
};

const App: React.FC = () => {
  const [selectedMode, setSelectedMode] = useState<OrchestrationMode>('fanout');
  const [inputText, setInputText] = useState('Hello, this is a test message for multi-agent orchestration.');
  const [isRunning, setIsRunning] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [taskRecord, setTaskRecord] = useState<TaskRecord | undefined>();
  const [messageFlows, setMessageFlows] = useState<MessageFlow[]>([]);
  const [agents, setAgents] = useState<AgentInfo[]>(mockAgents);
  const [executionTimes, setExecutionTimes] = useState<Record<OrchestrationMode, number>>({} as Record<OrchestrationMode, number>);
  const [useRealApi, setUseRealApi] = useState(false);
  const [apiConnected, setApiConnected] = useState(false);
  const [nacosConnected, setNacosConnected] = useState(false);

  // 检查后端连接状态
  useEffect(() => {
    const checkConnection = async () => {
      try {
        const health = await healthCheck();
        setApiConnected(health.status === 'UP');
        setNacosConnected(health.nacosConnected);
      } catch {
        setApiConnected(false);
        setNacosConnected(false);
      }
    };

    checkConnection();
    const interval = setInterval(checkConnection, 5000);
    return () => clearInterval(interval);
  }, []);

  // 获取真实 Agent 列表
  useEffect(() => {
    const fetchAgents = async () => {
      if (!useRealApi || !apiConnected) {
        setAgents(mockAgents);
        return;
      }
      try {
        const realAgents = await getAgents();
        // 将后端返回的数据转换为前端类型
        const typedAgents: AgentInfo[] = realAgents.map(a => ({
          ...a,
          type: a.type as AgentInfo['type'],
          status: a.status as AgentInfo['status'],
        }));
        setAgents(typedAgents);
      } catch (error) {
        console.error('Failed to fetch agents:', error);
        setAgents(mockAgents);
      }
    };

    fetchAgents();
    const interval = setInterval(fetchAgents, 3000);
    return () => clearInterval(interval);
  }, [useRealApi, apiConnected]);

  // 执行演示
  const handleExecute = useCallback(async () => {
    if (!inputText.trim()) {
      message.warning('请输入演示文本');
      return;
    }

    setIsRunning(true);
    setIsPaused(false);

    // 更新 Agent 状态为忙碌
    setAgents(prev => prev.map(a => ({ ...a, status: 'busy' as const, currentTask: '处理中...' })));

    // 生成消息流动
    const flows: MessageFlow[] = [];
    const workers = ['translator', 'analyzer', 'summarizer'];
    
    if (selectedMode === 'fanout' || selectedMode === 'scatter-gather') {
      workers.forEach(w => {
        flows.push({
          id: `flow-${w}`,
          from: 'master',
          to: w,
          timestamp: Date.now(),
          content: inputText.substring(0, 20) + '...',
          direction: 'request',
        });
      });
    }
    setMessageFlows(flows);

    try {
      if (useRealApi && apiConnected) {
        // 使用真实后端 API
        const result = await executeDemo(selectedMode, inputText);
        
        // 将后端返回的数据转换为前端类型
        const typedRecord: TaskRecord = {
          ...result,
          mode: result.mode as OrchestrationMode,
          status: result.status as TaskRecord['status'],
          events: result.events.map(e => ({
            ...e,
            type: e.type as TaskRecord['events'][0]['type'],
          })),
        };
        setTaskRecord(typedRecord);

        if (result.endTime && result.startTime) {
          setExecutionTimes(prev => ({
            ...prev,
            [selectedMode]: result.endTime! - result.startTime,
          }));
        }

        message.success('演示执行完成！');
      } else {
        // 使用模拟数据
        const startTime = Date.now();
        const events = generateMockEvents(selectedMode, inputText);
        
        const record: TaskRecord = {
          id: `task-${Date.now()}`,
          mode: selectedMode,
          input: inputText,
          startTime,
          status: 'running',
          events,
        };
        setTaskRecord(record);

        // 模拟执行过程
        const totalDuration = events[events.length - 1].timestamp - events[0].timestamp;
        
        await new Promise(resolve => setTimeout(resolve, totalDuration + 500));

        // 完成
        setTaskRecord(prev => prev ? {
          ...prev,
          status: 'completed',
          endTime: Date.now(),
          output: `处理完成: ${inputText}`,
        } : undefined);

        setExecutionTimes(prev => ({
          ...prev,
          [selectedMode]: totalDuration,
        }));

        message.success('演示执行完成！');
      }
    } catch (error) {
      console.error('Execution failed:', error);
      message.error('执行失败，请检查后端连接');
    }

    // 恢复 Agent 状态
    setAgents(prev => prev.map(a => ({ ...a, status: 'online' as const, currentTask: undefined })));
    setMessageFlows([]);
    setIsRunning(false);
  }, [inputText, selectedMode, useRealApi, apiConnected]);

  // 重置
  const handleReset = useCallback(() => {
    setIsRunning(false);
    setIsPaused(false);
    setTaskRecord(undefined);
    setMessageFlows([]);
    setAgents(mockAgents);
    setInputText('Hello, this is a test message for multi-agent orchestration.');
  }, []);

  // 暂停切换
  const handlePauseToggle = useCallback(() => {
    setIsPaused(prev => !prev);
  }, []);

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#667eea',
          borderRadius: 8,
        },
      }}
    >
      <div className="app-container">
        {/* 标题栏 */}
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          className="header"
        >
          <div className="header-content">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <Title level={3} style={{ margin: 0, color: '#fff' }}>
                <span className="logo-icon">🤖</span>
                Multi-Agent Orchestration Visualizer
              </Title>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Tag color={apiConnected ? 'success' : 'error'} icon={<ApiOutlined />}>
                  API {apiConnected ? '已连接' : '未连接'}
                </Tag>
                {apiConnected && (
                  <Tag color={nacosConnected ? 'success' : 'warning'} icon={nacosConnected ? <CloudOutlined /> : <CloudSyncOutlined />}>
                    Nacos {nacosConnected ? '已连接' : '连接中'}
                  </Tag>
                )}
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <Text style={{ color: '#888' }}>
                基于 A2A 协议的分布式多 Agent 运行时可视化监控
              </Text>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Text style={{ color: '#888', fontSize: '12px' }}>模拟模式</Text>
                <Switch
                  checked={useRealApi}
                  onChange={setUseRealApi}
                  disabled={!apiConnected}
                  size="small"
                />
                <Text style={{ color: useRealApi ? '#52c41a' : '#888', fontSize: '12px' }}>真实 API</Text>
              </div>
            </div>
          </div>
        </motion.div>

        {/* 主内容区 */}
        <div className="main-content">
          <Row gutter={[16, 16]} style={{ height: '100%' }}>
            {/* 左侧：Agent 拓扑图 */}
            <Col xs={24} lg={12} xl={10}>
              <Card
                title={
                  <span style={{ color: '#fff' }}>
                    <span style={{ marginRight: '8px' }}>🔗</span>
                    Agent 拓扑图
                  </span>
                }
                className="topology-card"
                styles={{ body: { height: 'calc(100% - 57px)', padding: '12px' } }}
              >
                <AgentTopology
                  agents={agents}
                  messageFlows={messageFlows}
                />
              </Card>
            </Col>

            {/* 右侧：编排模式 + 时间线 */}
            <Col xs={24} lg={12} xl={14}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', height: '100%' }}>
                {/* 编排模式对比 */}
                <Card
                  title={
                    <span style={{ color: '#fff' }}>
                      <span style={{ marginRight: '8px' }}>⚡</span>
                      编排模式对比
                    </span>
                  }
                  className="mode-card"
                  style={{ flex: '0 0 auto', maxHeight: '350px' }}
                  styles={{ body: { padding: '12px', overflow: 'hidden' } }}
                >
                  <ModeComparison
                    selectedMode={selectedMode}
                    onModeSelect={setSelectedMode}
                    executionTimes={executionTimes}
                  />
                </Card>

                {/* 任务时间线 */}
                <Card
                  title={
                    <span style={{ color: '#fff' }}>
                      <span style={{ marginRight: '8px' }}>📊</span>
                      任务执行时间线
                    </span>
                  }
                  className="timeline-card"
                  style={{ flex: 1, minHeight: '250px', overflow: 'hidden' }}
                  styles={{ body: { height: 'calc(100% - 57px)', padding: '12px', overflow: 'auto' } }}
                >
                  <TaskTimeline
                    taskRecord={taskRecord}
                    isAnimating={isRunning}
                  />
                </Card>
              </div>
            </Col>
          </Row>
        </div>

        {/* 底部控制面板 */}
        <div className="control-panel">
          <ControlPanel
            selectedMode={selectedMode}
            inputText={inputText}
            onInputChange={setInputText}
            onExecute={handleExecute}
            onReset={handleReset}
            isRunning={isRunning}
            isPaused={isPaused}
            onPauseToggle={handlePauseToggle}
          />
        </div>
      </div>
    </ConfigProvider>
  );
};

export default App;
