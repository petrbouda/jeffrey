export interface DuplicateArrayGroup {
  typeName: string;
  arrayLength: number;
  count: number;
  shallowSize: number;
  wastedBytes: number;
  contentPreview: string;
  sampleObjectIds: number[];
}

export default interface DuplicateDataReport {
  totalPrimitiveArrays: number;
  totalPrimitiveArrayBytes: number;
  duplicateGroups: number;
  duplicateArrayCount: number;
  potentialSavings: number;
  oversizedSkipped: number;
  topGroups: DuplicateArrayGroup[];
}
