import { PathStep } from '@/services/api/model/GCRootPath';

export interface DominatedClassEntry {
  className: string;
  instanceCount: number;
  retainedSize: number;
  percentOfCluster: number;
}

export interface ClassLoaderLeakSummary {
  classLoaderId: number;
  classLoaderClassName: string;
  totalRetainedSize: number;
  suspectCount: number;
}

export interface LeakSuspect {
  rank: number;
  className: string;
  objectId: number | null;
  retainedSize: number;
  heapPercentage: number;
  instanceCount: number;
  reason: string;
  accumulationPoint: string;
  pathSteps: PathStep[];
  accumulationPointId: number | null;
  accumulationPointClass: string | null;
  dominatedHistogram: DominatedClassEntry[];
  leakScore: number;
  classLoaderId: number;
  classLoaderClassName: string;
}

export default interface LeakSuspectsReport {
  totalHeapSize: number;
  analyzedBytes: number;
  suspects: LeakSuspect[];
  topLeakingClassLoaders: ClassLoaderLeakSummary[];
}
