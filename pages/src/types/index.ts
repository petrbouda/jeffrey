// Project Types
export interface Project {
  id: string;
  name: string;
  createdAt: string;
  profileCount: number;
  recordingCount?: number;
  alertCount?: number;
  sourceType?: string | null;
  latestRecordingAt?: string | null;
  latestProfileAt?: string | null;
}

// Profile Types
export interface Profile {
  id: string;
  name: string;
  createdAt: string;
  updatedAt?: string;
  enabled: boolean;
  description?: string;
  size?: number;
  durationInSeconds?: number;
  metadata?: {
    [key: string]: any;
  };
}

// Guardian Check Types
export interface GuardianCheck {
  id: number;
  name: string;
  status: 'success' | 'warning' | 'error' | 'info' | 'disabled';
  score: number;
  brief: string;
  summary: string;
  explanation: string;
  solution: string;
  details: string | null;
  flamegraphData?: FlamegraphData | null;
}

// Flamegraph Types
export interface FlamegraphData {
  id: string;
  checkId: number;
  profileId: string;
  methods: FlamegraphMethod[];
}

export interface FlamegraphMethod {
  id: string;
  name: string;
  className: string;
  cpuTime: number;
  wallTime: number;
  selfTime: number;
  samples: number;
  percentage: number;
  children?: FlamegraphMethod[];
}

// Recording Types
export interface Recording {
  id: string;
  name: string;
  size: number;
  duration: number;
  recordedAt: string;
  path?: string;
  hasProfile?: boolean;
  createdAt?: string;
  folder?: string;
  isFolder?: boolean;
}

// Job Types
export enum JobStatus {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED'
}

export interface Job {
  id: string;
  type: string;
  status: JobStatus;
  progress: number;
  startedAt: string;
  completedAt?: string;
  error?: string;
}

// Repository Types
export interface RepositoryStats {
  totalSize: number;
  profileCount: number;
  recordingCount: number;
  storageUsedPercent: number;
}

export interface ActivityLogItem {
  id: string;
  type: string;
  message: string;
  timestamp: string;
}
