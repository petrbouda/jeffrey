export interface BiggestObjectEntry {
    objectId: number;
    className: string;
    shallowSize: number;
    retainedSize: number;
    objectParams: Record<string, string>;
}

export default interface BiggestObjectsReport {
    totalRetainedSize: number;
    totalHeapSize: number;
    entries: BiggestObjectEntry[];
}
