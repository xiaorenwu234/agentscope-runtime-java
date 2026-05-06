import React from 'react';
import { Button, Space, Input, Tooltip, message } from 'antd';
import { motion } from 'framer-motion';
import {
  PlayCircleOutlined,
  ReloadOutlined,
  PauseCircleOutlined,
  CameraOutlined,
} from '@ant-design/icons';
import type { OrchestrationMode } from '../types';
import { ORCHESTRATION_MODES } from '../types';

const { TextArea } = Input;

interface ControlPanelProps {
  selectedMode: OrchestrationMode;
  inputText: string;
  onInputChange: (text: string) => void;
  onExecute: () => void;
  onReset: () => void;
  isRunning: boolean;
  isPaused: boolean;
  onPauseToggle: () => void;
}

const ControlPanel: React.FC<ControlPanelProps> = ({
  selectedMode,
  inputText,
  onInputChange,
  onExecute,
  onReset,
  isRunning,
  isPaused,
  onPauseToggle,
}) => {
  const currentMode = ORCHESTRATION_MODES.find(m => m.id === selectedMode);

  const handleScreenshot = () => {
    message.info('截图功能：可使用系统截图工具 (Cmd+Shift+4)');
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      style={{
        background: '#1a1a2e',
        borderRadius: '12px',
        padding: '16px',
        border: '1px solid #333',
      }}
    >
      <div style={{ display: 'flex', gap: '16px', alignItems: 'flex-start' }}>
        {/* 输入区域 */}
        <div style={{ flex: 1 }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ color: '#888', fontSize: '13px' }}>演示输入：</span>
            <span style={{ color: '#667eea', fontSize: '13px' }}>
              当前模式: {currentMode?.nameCn}
            </span>
          </div>
          <TextArea
            value={inputText}
            onChange={(e) => onInputChange(e.target.value)}
            placeholder="请输入要处理的文本，例如：Hello, this is a test message for multi-agent orchestration."
            rows={2}
            style={{
              background: '#0d0d1a',
              border: '1px solid #333',
              color: '#ccc',
              resize: 'none',
            }}
            disabled={isRunning}
          />
        </div>

        {/* 控制按钮 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <Space>
            <Tooltip title={isRunning ? '执行中...' : '执行演示'}>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={onExecute}
                loading={isRunning}
                style={{
                  background: isRunning ? '#333' : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  border: 'none',
                  height: '40px',
                  paddingLeft: '20px',
                  paddingRight: '20px',
                }}
              >
                {isRunning ? '执行中' : '执行'}
              </Button>
            </Tooltip>

            {isRunning && (
              <Tooltip title={isPaused ? '继续动画' : '暂停动画'}>
                <Button
                  icon={isPaused ? <PlayCircleOutlined /> : <PauseCircleOutlined />}
                  onClick={onPauseToggle}
                  style={{
                    background: '#0d0d1a',
                    border: '1px solid #333',
                    color: '#ccc',
                    height: '40px',
                  }}
                />
              </Tooltip>
            )}

            <Tooltip title="重置">
              <Button
                icon={<ReloadOutlined />}
                onClick={onReset}
                style={{
                  background: '#0d0d1a',
                  border: '1px solid #333',
                  color: '#ccc',
                  height: '40px',
                }}
              />
            </Tooltip>

            <Tooltip title="截图提示">
              <Button
                icon={<CameraOutlined />}
                onClick={handleScreenshot}
                style={{
                  background: '#0d0d1a',
                  border: '1px solid #333',
                  color: '#ccc',
                  height: '40px',
                }}
              />
            </Tooltip>
          </Space>
        </div>
      </div>

      {/* 快捷示例 */}
      <div style={{ marginTop: '12px' }}>
        <span style={{ color: '#666', fontSize: '12px', marginRight: '8px' }}>快捷示例：</span>
        <Space size={[8, 8]} wrap>
          {[
            'Hello, this is a test message.',
            '请分析这段中文文本的情感。',
            'Summarize the key points of AI development.',
          ].map((text, idx) => (
            <Button
              key={idx}
              size="small"
              onClick={() => onInputChange(text)}
              disabled={isRunning}
              style={{
                background: '#0d0d1a',
                border: '1px solid #444',
                color: '#888',
                fontSize: '11px',
              }}
            >
              示例 {idx + 1}
            </Button>
          ))}
        </Space>
      </div>
    </motion.div>
  );
};

export default ControlPanel;
