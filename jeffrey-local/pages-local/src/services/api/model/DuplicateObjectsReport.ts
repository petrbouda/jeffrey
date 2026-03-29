export interface DuplicateObjectEntry {
    className: string;
    contentPreview: string;
    duplicateCount: number;
    individualSize: number;
    totalWastedBytes: number;
}

export default interface DuplicateObjectsReport {
    totalInstancesAnalyzed: number;
    totalWastedBytes: number;
    duplicates: DuplicateObjectEntry[];
}
