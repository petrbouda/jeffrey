import { PathStep } from '@/services/api/model/GCRootPath';

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
}

export default interface LeakSuspectsReport {
    totalHeapSize: number;
    analyzedBytes: number;
    suspects: LeakSuspect[];
}
