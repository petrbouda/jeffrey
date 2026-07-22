import HeapSummary from '@/services/api/model/HeapSummary';

export interface ClassDiffEntry {
  className: string;
  primaryCount: number;
  baselineCount: number;
  countDelta: number;
  primaryBytes: number;
  baselineBytes: number;
  bytesDelta: number;
}

export default interface HeapDumpDiffReport {
  primarySummary: HeapSummary;
  baselineSummary: HeapSummary;
  instanceCountDelta: number;
  shallowBytesDelta: number;
  entries: ClassDiffEntry[];
}
