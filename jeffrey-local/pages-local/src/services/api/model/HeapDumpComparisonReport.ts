import type ClassHistogramEntry from '@/services/api/model/ClassHistogramEntry';

export interface ClassComparisonEntry {
    className: string;
    baselineSize: number;
    currentSize: number;
    sizeDelta: number;
    baselineCount: number;
    currentCount: number;
    countDelta: number;
    status: 'GREW' | 'SHRANK' | 'NEW' | 'REMOVED' | 'UNCHANGED';
}

export interface HeapDumpComparisonReport {
    baselineTotalBytes: number;
    currentTotalBytes: number;
    totalBytesDelta: number;
    baselineClassCount: number;
    currentClassCount: number;
    entries: ClassComparisonEntry[];
}

export interface HeapDumpComparisonRequest {
    baseline: ClassHistogramEntry[];
    current: ClassHistogramEntry[];
    baselineTotalBytes: number;
    currentTotalBytes: number;
}
