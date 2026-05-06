import { useCallback, useEffect, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  useNodesState,
  useEdgesState,
  MarkerType,
  Handle,
  Position,
} from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { motion, AnimatePresence } from 'framer-motion';
import type { AgentInfo, MessageFlow } from '../types';

interface AgentTopologyProps {
  agents: AgentInfo[];
  messageFlows: MessageFlow[];
  activeFlowId?: string;
}

// 自定义 Agent 节点组件
const AgentNode = ({ data }: { data: AgentInfo & { isActive?: boolean } }) => {
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'online': return '#52c41a';
      case 'busy': return '#faad14';
      case 'offline': return '#ff4d4f';
      default: return '#d9d9d9';
    }
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'master': return '👑';
      case 'translator': return '🌐';
      case 'analyzer': return '🔍';
      case 'summarizer': return '📝';
      default: return '🤖';
    }
  };

  const getTypeColor = (type: string) => {
    switch (type) {
      case 'master': return 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)';
      case 'translator': return 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)';
      case 'analyzer': return 'linear-gradient(135deg, #ee0979 0%, #ff6a00 100%)';
      case 'summarizer': return 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)';
      default: return 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)';
    }
  };

  return (
    <motion.div
      initial={{ scale: 0.8, opacity: 0 }}
      animate={{ 
        scale: data.isActive ? 1.1 : 1, 
        opacity: 1,
        boxShadow: data.isActive 
          ? '0 0 20px rgba(102, 126, 234, 0.6)' 
          : '0 4px 12px rgba(0, 0, 0, 0.15)'
      }}
      transition={{ duration: 0.3 }}
      style={{
        padding: '16px 20px',
        borderRadius: '12px',
        background: getTypeColor(data.type),
        color: 'white',
        minWidth: '140px',
        textAlign: 'center',
        position: 'relative',
      }}
    >
      <Handle type="target" position={Position.Top} style={{ background: '#555' }} />
      <Handle type="source" position={Position.Bottom} style={{ background: '#555' }} />
      <Handle type="target" position={Position.Left} style={{ background: '#555' }} />
      <Handle type="source" position={Position.Right} style={{ background: '#555' }} />
      
      {/* 状态指示灯 */}
      <div
        style={{
          position: 'absolute',
          top: '8px',
          right: '8px',
          width: '10px',
          height: '10px',
          borderRadius: '50%',
          background: getStatusColor(data.status),
          border: '2px solid white',
        }}
      />

      {/* Agent 图标 */}
      <div style={{ fontSize: '28px', marginBottom: '8px' }}>
        {getTypeIcon(data.type)}
      </div>

      {/* Agent 名称 */}
      <div style={{ fontWeight: 'bold', fontSize: '14px', marginBottom: '4px' }}>
        {data.name}
      </div>

      {/* Agent 类型 */}
      <div style={{ fontSize: '11px', opacity: 0.9, textTransform: 'uppercase' }}>
        {data.type}
      </div>

      {/* 当前任务 */}
      {data.currentTask && (
        <div
          style={{
            marginTop: '8px',
            padding: '4px 8px',
            background: 'rgba(255,255,255,0.2)',
            borderRadius: '4px',
            fontSize: '10px',
          }}
        >
          {data.currentTask}
        </div>
      )}
    </motion.div>
  );
};

const nodeTypes = {
  agent: AgentNode,
};

const AgentTopology: React.FC<AgentTopologyProps> = ({ agents, messageFlows, activeFlowId }) => {
  // 计算节点位置
  const calculateNodePositions = useCallback((agents: AgentInfo[]): Node[] => {
    const master = agents.find(a => a.type === 'master');
    const workers = agents.filter(a => a.type !== 'master');
    const centerX = 300;
    const centerY = 200;
    const radius = 180;

    const nodes: Node[] = [];

    // Master 在中心
    if (master) {
      nodes.push({
        id: master.id,
        type: 'agent',
        position: { x: centerX - 70, y: centerY - 50 },
        data: { ...master, isActive: messageFlows.some(f => f.from === master.id || f.to === master.id) },
      });
    }

    // Workers 围绕 Master 分布
    workers.forEach((worker, index) => {
      const angle = (2 * Math.PI * index) / workers.length - Math.PI / 2;
      const x = centerX + radius * Math.cos(angle) - 70;
      const y = centerY + radius * Math.sin(angle) - 50;

      nodes.push({
        id: worker.id,
        type: 'agent',
        position: { x, y },
        data: { ...worker, isActive: messageFlows.some(f => f.from === worker.id || f.to === worker.id) },
      });
    });

    return nodes;
  }, [messageFlows]);

  // 计算边
  const calculateEdges = useCallback((agents: AgentInfo[], flows: MessageFlow[]): Edge[] => {
    const master = agents.find(a => a.type === 'master');
    const workers = agents.filter(a => a.type !== 'master');
    
    if (!master) return [];

    // 基础连接：Master 到所有 Worker
    const baseEdges: Edge[] = workers.map(worker => ({
      id: `${master.id}-${worker.id}`,
      source: master.id,
      target: worker.id,
      type: 'smoothstep',
      animated: flows.some(f => 
        (f.from === master.id && f.to === worker.id) || 
        (f.from === worker.id && f.to === master.id)
      ),
      style: { 
        stroke: flows.some(f => f.from === master.id && f.to === worker.id) ? '#667eea' : '#b1b1b7',
        strokeWidth: 2,
      },
      markerEnd: {
        type: MarkerType.ArrowClosed,
        color: flows.some(f => f.from === master.id && f.to === worker.id) ? '#667eea' : '#b1b1b7',
      },
    }));

    return baseEdges;
  }, []);

  const initialNodes = useMemo(() => calculateNodePositions(agents), [agents, calculateNodePositions]);
  const initialEdges = useMemo(() => calculateEdges(agents, messageFlows), [agents, messageFlows, calculateEdges]);

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  useEffect(() => {
    setNodes(calculateNodePositions(agents));
    setEdges(calculateEdges(agents, messageFlows));
  }, [agents, messageFlows, calculateNodePositions, calculateEdges, setNodes, setEdges]);

  return (
    <div style={{ width: '100%', height: '100%', background: '#1a1a2e', borderRadius: '12px', overflow: 'hidden' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        fitView
        attributionPosition="bottom-left"
      >
        <Background color="#333" gap={20} />
        <Controls />
      </ReactFlow>

      {/* 消息流动动画层 */}
      <AnimatePresence>
        {activeFlowId && messageFlows.filter(f => f.id === activeFlowId).map(flow => (
          <motion.div
            key={flow.id}
            initial={{ opacity: 0, scale: 0.5 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.5 }}
            style={{
              position: 'absolute',
              top: '10px',
              right: '10px',
              background: 'rgba(102, 126, 234, 0.9)',
              color: 'white',
              padding: '8px 12px',
              borderRadius: '8px',
              fontSize: '12px',
              maxWidth: '200px',
            }}
          >
            <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>
              {flow.direction === 'request' ? '📤' : '📥'} {flow.from} → {flow.to}
            </div>
            <div style={{ opacity: 0.9 }}>{flow.content}</div>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
};

export default AgentTopology;
