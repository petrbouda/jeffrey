export interface BiggestObjectEntry {
    className: string;
    shallowSize: number;
    retainedSize: number;
    objectId: number;
}

export default interface BiggestObjectsReport {
    totalHeapSize: number;
    totalRetainedSize: number;
    entries: BiggestObjectEntry[];
}
