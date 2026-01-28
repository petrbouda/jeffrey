export interface BiggestObjectEntry {
    objectId: number;
    className: string;
    shallowSize: number;
    retainedSize: number;
    displayValue: string;
}

export default interface BiggestObjectsReport {
    totalRetainedSize: number;
    totalHeapSize: number;
    entries: BiggestObjectEntry[];
}
