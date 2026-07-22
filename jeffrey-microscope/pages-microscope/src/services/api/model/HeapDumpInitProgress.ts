import type { SubPhaseTiming } from '@/services/api/model/InitPipelineResult';

export interface HeapDumpInitStageProgress {
  id: string;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  durationMs: number | null;
  /** Backend-measured milliseconds spent so far while in_progress, else null. */
  elapsedMs: number | null;
  subPhases: SubPhaseTiming[] | null;
}

export default interface HeapDumpInitProgress {
  state: 'idle' | 'running' | 'completed' | 'failed';
  errorCode: string | null;
  errorMessage: string | null;
  stages: HeapDumpInitStageProgress[];
}
