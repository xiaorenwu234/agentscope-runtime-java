// Agent 状态
export type AgentStatus = 'online' | 'offline' | 'busy';

// Agent 信息
export interface AgentInfo {
  id: string;
  name: string;
  type: 'master' | 'translator' | 'analyzer' | 'summarizer';
  status: AgentStatus;
  host: string;
  port: number;
  currentTask?: string;
}

// 编排模式
export type OrchestrationMode = 'fanout' | 'pipeline' | 'master-slave' | 'scatter-gather';

// 任务事件
export interface TaskEvent {
  id: string;
  timestamp: number;
  type: 'request_received' | 'task_dispatched' | 'worker_start' | 'worker_complete' | 'result_aggregated' | 'response_sent';
  agentId?: string;
  agentName?: string;
  message: string;
  details?: string;
}

// 任务记录
export interface TaskRecord {
  id: string;
  mode: OrchestrationMode;
  input: string;
  output?: string;
  startTime: number;
  endTime?: number;
  status: 'running' | 'completed' | 'failed';
  events: TaskEvent[];
}

// 消息流动
export interface MessageFlow {
  id: string;
  from: string;
  to: string;
  timestamp: number;
  content: string;
  direction: 'request' | 'response';
}

// 编排模式信息
export interface ModeInfo {
  id: OrchestrationMode;
  name: string;
  nameCn: string;
  description: string;
  characteristics: string[];
}

export const ORCHESTRATION_MODES: ModeInfo[] = [
  {
    id: 'fanout',
    name: 'Fan-Out',
    nameCn: '扇出模式',
    description: 'Master 同时向多个 Worker 发送请求，并行处理后汇总结果',
    characteristics: ['并行执行', '最快响应', '适合独立任务']
  },
  {
    id: 'pipeline',
    name: 'Pipeline',
    nameCn: '流水线模式',
    description: '任务按顺序流经多个 Worker，每个 Worker 处理后传递给下一个',
    characteristics: ['串行执行', '数据流转', '适合依赖处理']
  },
  {
    id: 'master-slave',
    name: 'Master-Slave',
    nameCn: '主从模式',
    description: 'Master 负责协调调度，Slave 执行具体任务并返回结果',
    characteristics: ['集中调度', '任务分配', '适合大规模并行']
  },
  {
    id: 'scatter-gather',
    name: 'Scatter-Gather',
    nameCn: '散布收集模式',
    description: '将请求散布到多个 Worker，收集所有响应后合并返回',
    characteristics: ['广播请求', '结果聚合', '适合搜索查询']
  }
];
