import axios from 'axios';
import type { AgentInfo, OrchestrationMode, TaskRecord } from './types';

// API base URL - configurable via environment variable
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error);
    return Promise.reject(error);
  }
);

/**
 * Get all registered agents from the backend.
 */
export async function getAgents(): Promise<AgentInfo[]> {
  const response = await api.get<AgentInfo[]>('/agents');
  return response.data;
}

/**
 * Execute an orchestration demo with the specified mode.
 */
export async function executeDemo(
  mode: OrchestrationMode,
  input: string
): Promise<TaskRecord> {
  const response = await api.post<TaskRecord>(`/demo/${mode}`, { input });
  return response.data;
}

/**
 * Get task history.
 */
export async function getTasks(): Promise<TaskRecord[]> {
  const response = await api.get<TaskRecord[]>('/tasks');
  return response.data;
}

/**
 * Get a specific task by ID.
 */
export async function getTask(taskId: string): Promise<TaskRecord | null> {
  try {
    const response = await api.get<TaskRecord>(`/tasks/${taskId}`);
    return response.data;
  } catch {
    return null;
  }
}

/**
 * Health check.
 */
export async function healthCheck(): Promise<{
  status: string;
  nacosConnected: boolean;
  timestamp: number;
}> {
  const response = await api.get('/health');
  return response.data;
}

export default api;
