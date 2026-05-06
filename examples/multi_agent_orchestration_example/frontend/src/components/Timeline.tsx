import React, { useState } from 'react';
import { Timeline as AntTimeline, Card, Typography, Tag, Modal } from 'antd';
import { motion, AnimatePresence } from 'framer-motion';
import {
  SendOutlined,
  ThunderboltOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  MergeCellsOutlined,
  CheckOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import type { TaskEvent, TaskRecord } from '../types';

const { Text, Paragraph } = Typography;

interface TimelineProps {
  taskRecord?: TaskRecord;
  isAnimating?: boolean;
}

const getEventIcon = (type: TaskEvent['type']) => {
  switch (type) {
    case 'request_received': return <SendOutlined />;
    case 'task_dispatched': return <ThunderboltOutlined />;
    case 'worker_start': return <PlayCircleOutlined />;
    case 'worker_complete': return <CheckCircleOutlined />;
    case 'result_aggregated': return <MergeCellsOutlined />;
    case 'response_sent': return <CheckOutlined />;
    default: return <PlayCircleOutlined />;
  }
};

const getEventColor = (type: TaskEvent['type']) => {
  switch (type) {
    case 'request_received': return '#667eea';
    case 'task_dispatched': return '#faad14';
    case 'worker_start': return '#11998e';
    case 'worker_complete': return '#52c41a';
    case 'result_aggregated': return '#ee0979';
    case 'response_sent': return '#4facfe';
    default: return '#666';
  }
};

const getEventLabel = (type: TaskEvent['type']) => {
  switch (type) {
    case 'request_received': return '接收请求';
    case 'task_dispatched': return '任务分发';
    case 'worker_start': return '开始处理';
    case 'worker_complete': return '处理完成';
    case 'result_aggregated': return '结果聚合';
    case 'response_sent': return '响应返回';
    default: return '未知事件';
  }
};

const getModeLabel = (mode: string) => {
  switch (mode) {
    case 'fanout': return { name: 'Fan-Out', color: '#667eea', desc: '并行分发' };
    case 'pipeline': return { name: 'Pipeline', color: '#11998e', desc: '串行流水线' };
    case 'master-slave': return { name: 'Master-Slave', color: '#ee0979', desc: '主从协调' };
    case 'scatter-gather': return { name: 'Scatter-Gather', color: '#4facfe', desc: '散布收集' };
    default: return { name: mode, color: '#666', desc: '' };
  }
};

const formatTime = (timestamp: number, baseTime?: number) => {
  if (baseTime) {
    const diff = timestamp - baseTime;
    return `+${diff}ms`;
  }
  const date = new Date(timestamp);
  return date.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit',
    fractionalSecondDigits: 3 
  });
};

const TaskTimeline: React.FC<TimelineProps> = ({ taskRecord, isAnimating }) => {
  const [selectedEvent, setSelectedEvent] = useState<TaskEvent | null>(null);
  const [visibleEvents, setVisibleEvents] = useState<number>(0);
  const prevTaskIdRef = React.useRef<string | null>(null);

  // 动画显示事件
  React.useEffect(() => {
    if (!taskRecord || !taskRecord.events || taskRecord.events.length === 0) {
      setVisibleEvents(0);
      return;
    }

    const isNewTask = taskRecord.id !== prevTaskIdRef.current;
    prevTaskIdRef.current = taskRecord.id;

    // 新任务开始动画
    if (isNewTask && isAnimating) {
      setVisibleEvents(0);
      let index = 0;
      const timer = setInterval(() => {
        index++;
        if (index <= taskRecord.events.length) {
          setVisibleEvents(index);
        } else {
          clearInterval(timer);
        }
      }, 300);
      return () => clearInterval(timer);
    } else {
      // 直接显示所有事件
      setVisibleEvents(taskRecord.events.length);
    }
  }, [taskRecord?.id, taskRecord?.events?.length, isAnimating]);

  if (!taskRecord) {
    return (
      <div style={{ 
        height: '100%', 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'center',
        color: '#666',
        flexDirection: 'column',
        gap: '12px'
      }}>
        <ThunderboltOutlined style={{ fontSize: '48px', opacity: 0.3 }} />
        <Text style={{ color: '#666' }}>点击上方按钮执行演示</Text>
      </div>
    );
  }

  const baseTime = taskRecord.events[0]?.timestamp;
  const displayedEvents = taskRecord.events.slice(0, visibleEvents);

  const timelineItems = displayedEvents.map((event, index) => ({
    key: event.id,
    dot: (
      <motion.div
        initial={{ scale: 0, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.3, delay: index * 0.1 }}
        style={{
          width: '32px',
          height: '32px',
          borderRadius: '50%',
          background: getEventColor(event.type),
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'white',
          fontSize: '14px',
          boxShadow: `0 0 10px ${getEventColor(event.type)}60`,
        }}
      >
        {getEventIcon(event.type)}
      </motion.div>
    ),
    children: (
      <motion.div
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.3, delay: index * 0.1 }}
        onClick={() => setSelectedEvent(event)}
        style={{ cursor: 'pointer' }}
      >
        <Card
          size="small"
          style={{
            background: '#1a1a2e',
            border: `1px solid ${getEventColor(event.type)}40`,
            borderRadius: '8px',
            marginBottom: '8px',
          }}
          hoverable
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <Tag color={getEventColor(event.type)} style={{ marginBottom: '6px' }}>
                {getEventLabel(event.type)}
              </Tag>
              {event.agentName && (
                <Tag style={{ marginBottom: '6px', marginLeft: '4px' }}>
                  {event.agentName}
                </Tag>
              )}
              <div>
                <Text style={{ color: '#ccc', fontSize: '13px' }}>
                  {event.message}
                </Text>
              </div>
            </div>
            <Text style={{ color: '#888', fontSize: '12px', whiteSpace: 'nowrap', marginLeft: '12px' }}>
              {formatTime(event.timestamp, baseTime)}
            </Text>
          </div>
        </Card>
      </motion.div>
    ),
  }));

  return (
    <div style={{ height: '100%', overflowY: 'auto', padding: '8px 0' }}>
      {/* 任务摘要 */}
      <Card
        size="small"
        style={{
          background: '#0d0d1a',
          border: `1px solid ${getModeLabel(taskRecord.mode).color}40`,
          borderRadius: '8px',
          marginBottom: '16px',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Tag color={getModeLabel(taskRecord.mode).color} style={{ margin: 0 }}>
              {getModeLabel(taskRecord.mode).name}
            </Tag>
            <Text style={{ color: '#888', fontSize: '12px' }}>
              {getModeLabel(taskRecord.mode).desc}
            </Text>
          </div>
          <Tag color={taskRecord.status === 'completed' ? 'success' : taskRecord.status === 'running' ? 'processing' : 'error'}>
            {taskRecord.status === 'completed' ? '已完成' : taskRecord.status === 'running' ? '执行中' : '失败'}
          </Tag>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Text style={{ color: '#888', fontSize: '12px' }}>任务ID: </Text>
            <Text style={{ color: '#ccc', fontSize: '12px' }}>{taskRecord.id.substring(0, 8)}...</Text>
          </div>
          {taskRecord.endTime && (
            <div>
              <Text style={{ color: '#888', fontSize: '12px' }}>总耗时: </Text>
              <Text strong style={{ color: '#52c41a', fontSize: '14px' }}>
                {taskRecord.endTime - taskRecord.startTime}ms
              </Text>
            </div>
          )}
        </div>
      </Card>

      {/* 执行输出 */}
      {taskRecord.output && taskRecord.status === 'completed' && (
        <Card
          size="small"
          title={
            <span style={{ color: '#52c41a', fontSize: '13px' }}>
              <CodeOutlined style={{ marginRight: '8px' }} />
              执行输出
            </span>
          }
          style={{
            background: '#0d0d1a',
            border: '1px solid #52c41a40',
            borderRadius: '8px',
            marginBottom: '16px',
          }}
          styles={{ header: { borderBottom: '1px solid #333', minHeight: '36px', padding: '0 12px' }, body: { padding: '12px' } }}
        >
          <pre style={{
            color: '#ccc',
            fontSize: '11px',
            margin: 0,
            padding: '8px',
            background: '#1a1a2e',
            borderRadius: '4px',
            maxHeight: '120px',
            overflow: 'auto',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
          }}>
            {taskRecord.output}
          </pre>
        </Card>
      )}

      {/* 时间线 */}
      <AntTimeline items={timelineItems} />

      {/* 进度指示 */}
      <AnimatePresence>
        {visibleEvents < taskRecord.events.length && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            style={{ textAlign: 'center', padding: '12px' }}
          >
            <motion.div
              animate={{ scale: [1, 1.2, 1] }}
              transition={{ duration: 1, repeat: Infinity }}
              style={{
                width: '12px',
                height: '12px',
                borderRadius: '50%',
                background: '#667eea',
                margin: '0 auto',
              }}
            />
          </motion.div>
        )}
      </AnimatePresence>

      {/* 事件详情弹窗 */}
      <Modal
        title={
          <span>
            {selectedEvent && getEventIcon(selectedEvent.type)} 事件详情
          </span>
        }
        open={!!selectedEvent}
        onCancel={() => setSelectedEvent(null)}
        footer={null}
        styles={{ body: { background: '#1a1a2e' } }}
      >
        {selectedEvent && (
          <div>
            <div style={{ marginBottom: '12px' }}>
              <Text style={{ color: '#888' }}>事件类型: </Text>
              <Tag color={getEventColor(selectedEvent.type)}>
                {getEventLabel(selectedEvent.type)}
              </Tag>
            </div>
            {selectedEvent.agentName && (
              <div style={{ marginBottom: '12px' }}>
                <Text style={{ color: '#888' }}>Agent: </Text>
                <Text style={{ color: '#ccc' }}>{selectedEvent.agentName}</Text>
              </div>
            )}
            <div style={{ marginBottom: '12px' }}>
              <Text style={{ color: '#888' }}>时间: </Text>
              <Text style={{ color: '#ccc' }}>
                {new Date(selectedEvent.timestamp).toLocaleString('zh-CN')}
              </Text>
            </div>
            <div style={{ marginBottom: '12px' }}>
              <Text style={{ color: '#888' }}>消息: </Text>
              <Paragraph style={{ color: '#ccc', margin: 0 }}>
                {selectedEvent.message}
              </Paragraph>
            </div>
            {selectedEvent.details && (
              <div>
                <Text style={{ color: '#888' }}>详情: </Text>
                <Card size="small" style={{ background: '#0d0d1a', marginTop: '8px' }}>
                  <pre style={{ color: '#ccc', margin: 0, fontSize: '12px', overflow: 'auto' }}>
                    {selectedEvent.details}
                  </pre>
                </Card>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default TaskTimeline;
