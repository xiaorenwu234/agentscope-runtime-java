import React from 'react';
import { Card, Tag, Tabs, Typography } from 'antd';
import { motion } from 'framer-motion';
import {
  BranchesOutlined,
  SwapOutlined,
  ClusterOutlined,
  RadarChartOutlined,
} from '@ant-design/icons';
import type { OrchestrationMode, ModeInfo } from '../types';
import { ORCHESTRATION_MODES } from '../types';

const { Text, Paragraph } = Typography;

interface ModeComparisonProps {
  selectedMode: OrchestrationMode;
  onModeSelect: (mode: OrchestrationMode) => void;
  executionTimes?: Record<OrchestrationMode, number>;
}

const getModeIcon = (mode: OrchestrationMode) => {
  switch (mode) {
    case 'fanout': return <BranchesOutlined />;
    case 'pipeline': return <SwapOutlined />;
    case 'master-slave': return <ClusterOutlined />;
    case 'scatter-gather': return <RadarChartOutlined />;
    default: return <BranchesOutlined />;
  }
};

const getModeColor = (mode: OrchestrationMode) => {
  switch (mode) {
    case 'fanout': return '#667eea';
    case 'pipeline': return '#11998e';
    case 'master-slave': return '#ee0979';
    case 'scatter-gather': return '#4facfe';
    default: return '#667eea';
  }
};

// 编排模式动画示意图
const ModeAnimation: React.FC<{ mode: OrchestrationMode; isActive: boolean }> = ({ mode, isActive }) => {
  const renderFanOut = () => (
    <svg viewBox="0 0 200 120" style={{ width: '100%', height: '100%' }}>
      {/* Master */}
      <motion.circle
        cx="100" cy="25" r="18"
        fill="#667eea"
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ duration: 0.3 }}
      />
      <text x="100" y="30" textAnchor="middle" fill="white" fontSize="12">M</text>
      
      {/* Workers */}
      {[30, 100, 170].map((x, i) => (
        <React.Fragment key={i}>
          <motion.line
            x1="100" y1="43" x2={x} y2="77"
            stroke={isActive ? '#667eea' : '#666'}
            strokeWidth="2"
            initial={{ pathLength: 0 }}
            animate={{ pathLength: 1 }}
            transition={{ duration: 0.5, delay: i * 0.1 }}
          />
          <motion.circle
            cx={x} cy="95" r="15"
            fill="#38ef7d"
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ duration: 0.3, delay: 0.3 + i * 0.1 }}
          />
          <text x={x} y="100" textAnchor="middle" fill="white" fontSize="11">W{i+1}</text>
        </React.Fragment>
      ))}
      
      {/* 并行箭头动画 */}
      {isActive && [30, 100, 170].map((x, i) => (
        <motion.circle
          key={`arrow-${i}`}
          r="4"
          fill="#fff"
          initial={{ cx: 100, cy: 43 }}
          animate={{ cx: x, cy: 77 }}
          transition={{ duration: 0.8, repeat: Infinity, repeatDelay: 1, delay: i * 0.1 }}
        />
      ))}
    </svg>
  );

  const renderPipeline = () => (
    <svg viewBox="0 0 200 80" style={{ width: '100%', height: '100%' }}>
      {[30, 85, 140].map((x, i) => (
        <React.Fragment key={i}>
          <motion.circle
            cx={x} cy="40" r="18"
            fill={i === 0 ? '#667eea' : '#38ef7d'}
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ duration: 0.3, delay: i * 0.2 }}
          />
          <text x={x} y="45" textAnchor="middle" fill="white" fontSize="12">
            {i === 0 ? 'M' : `W${i}`}
          </text>
          {i < 2 && (
            <motion.line
              x1={x + 18} y1="40" x2={x + 37} y2="40"
              stroke={isActive ? '#667eea' : '#666'}
              strokeWidth="2"
              markerEnd="url(#arrow)"
              initial={{ pathLength: 0 }}
              animate={{ pathLength: 1 }}
              transition={{ duration: 0.3, delay: 0.3 + i * 0.2 }}
            />
          )}
        </React.Fragment>
      ))}
      
      {/* 流动动画 */}
      {isActive && (
        <motion.circle
          r="5"
          fill="#fff"
          initial={{ cx: 48, cy: 40 }}
          animate={{ cx: [48, 103, 158] }}
          transition={{ duration: 2, repeat: Infinity, repeatDelay: 0.5 }}
        />
      )}
      
      <defs>
        <marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
          <path d="M0,0 L0,6 L9,3 z" fill={isActive ? '#667eea' : '#666'} />
        </marker>
      </defs>
    </svg>
  );

  const renderMasterSlave = () => (
    <svg viewBox="0 0 200 120" style={{ width: '100%', height: '100%' }}>
      {/* Master */}
      <motion.circle
        cx="100" cy="25" r="20"
        fill="#ee0979"
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ duration: 0.3 }}
      />
      <text x="100" y="30" textAnchor="middle" fill="white" fontSize="12" fontWeight="bold">M</text>
      
      {/* Slaves */}
      {[40, 100, 160].map((x, i) => (
        <React.Fragment key={i}>
          <motion.line
            x1="100" y1="45" x2={x} y2="75"
            stroke={isActive ? '#ee0979' : '#666'}
            strokeWidth="2"
            strokeDasharray={isActive ? "5,5" : "none"}
            initial={{ pathLength: 0 }}
            animate={{ pathLength: 1 }}
            transition={{ duration: 0.4, delay: i * 0.1 }}
          />
          <motion.rect
            x={x - 15} y="80" width="30" height="25" rx="4"
            fill="#ff6a00"
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ duration: 0.3, delay: 0.4 + i * 0.1 }}
          />
          <text x={x} y="97" textAnchor="middle" fill="white" fontSize="10">S{i+1}</text>
        </React.Fragment>
      ))}
    </svg>
  );

  const renderScatterGather = () => (
    <svg viewBox="0 0 200 120" style={{ width: '100%', height: '100%' }}>
      {/* Center node */}
      <motion.circle
        cx="100" cy="60" r="22"
        fill="#4facfe"
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ duration: 0.3 }}
      />
      <text x="100" y="65" textAnchor="middle" fill="white" fontSize="12">Hub</text>
      
      {/* Surrounding nodes */}
      {[0, 72, 144, 216, 288].map((angle, i) => {
        const rad = (angle * Math.PI) / 180;
        const x = 100 + 45 * Math.cos(rad);
        const y = 60 + 40 * Math.sin(rad);
        return (
          <React.Fragment key={i}>
            <motion.line
              x1="100" y1="60" x2={x} y2={y}
              stroke={isActive ? '#00f2fe' : '#666'}
              strokeWidth="2"
              initial={{ pathLength: 0 }}
              animate={{ pathLength: 1 }}
              transition={{ duration: 0.4, delay: i * 0.1 }}
            />
            <motion.circle
              cx={x} cy={y} r="12"
              fill="#00f2fe"
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ duration: 0.3, delay: 0.4 + i * 0.1 }}
            />
          </React.Fragment>
        );
      })}
      
      {/* 散布收集动画 */}
      {isActive && [0, 72, 144, 216, 288].map((angle, i) => {
        const rad = (angle * Math.PI) / 180;
        const endX = 100 + 45 * Math.cos(rad);
        const endY = 60 + 40 * Math.sin(rad);
        return (
          <motion.circle
            key={`scatter-${i}`}
            r="4"
            fill="#fff"
            initial={{ cx: 100, cy: 60 }}
            animate={{ cx: [100, endX, 100], cy: [60, endY, 60] }}
            transition={{ duration: 1.5, repeat: Infinity, delay: i * 0.2 }}
          />
        );
      })}
    </svg>
  );

  switch (mode) {
    case 'fanout': return renderFanOut();
    case 'pipeline': return renderPipeline();
    case 'master-slave': return renderMasterSlave();
    case 'scatter-gather': return renderScatterGather();
    default: return renderFanOut();
  }
};

const ModeComparison: React.FC<ModeComparisonProps> = ({ 
  selectedMode, 
  onModeSelect,
  executionTimes 
}) => {
  const tabItems = ORCHESTRATION_MODES.map((mode: ModeInfo) => ({
    key: mode.id,
    label: (
      <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
        {getModeIcon(mode.id)}
        {mode.nameCn}
      </span>
    ),
    children: (
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <Card 
          size="small" 
          style={{ 
            background: '#1a1a2e', 
            border: `1px solid ${getModeColor(mode.id)}40`,
            borderRadius: '12px'
          }}
        >
          {/* 模式动画 */}
          <div style={{ 
            height: '140px', 
            marginBottom: '16px',
            background: '#0d0d1a',
            borderRadius: '8px',
            padding: '10px'
          }}>
            <ModeAnimation mode={mode.id} isActive={selectedMode === mode.id} />
          </div>

          {/* 模式名称 */}
          <div style={{ marginBottom: '12px' }}>
            <Text strong style={{ color: getModeColor(mode.id), fontSize: '16px' }}>
              {mode.name}
            </Text>
            <Text style={{ color: '#888', marginLeft: '8px' }}>
              {mode.nameCn}
            </Text>
          </div>

          {/* 模式描述 */}
          <Paragraph style={{ color: '#ccc', marginBottom: '12px', fontSize: '13px' }}>
            {mode.description}
          </Paragraph>

          {/* 特性标签 */}
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '12px' }}>
            {mode.characteristics.map((char, idx) => (
              <Tag key={idx} color={getModeColor(mode.id)} style={{ margin: 0 }}>
                {char}
              </Tag>
            ))}
          </div>

          {/* 执行时间 */}
          {executionTimes?.[mode.id] && (
            <div style={{ 
              padding: '8px 12px', 
              background: '#0d0d1a', 
              borderRadius: '6px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <Text style={{ color: '#888' }}>执行耗时</Text>
              <Text strong style={{ color: getModeColor(mode.id) }}>
                {executionTimes[mode.id]}ms
              </Text>
            </div>
          )}
        </Card>
      </motion.div>
    ),
  }));

  return (
    <div style={{ height: '100%' }}>
      <Tabs
        activeKey={selectedMode}
        onChange={(key) => onModeSelect(key as OrchestrationMode)}
        items={tabItems}
        size="small"
        style={{ height: '100%' }}
        tabBarStyle={{ 
          marginBottom: '8px',
          borderBottom: '1px solid #333'
        }}
      />
    </div>
  );
};

export default ModeComparison;
