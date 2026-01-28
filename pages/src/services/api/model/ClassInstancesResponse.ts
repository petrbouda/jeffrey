export interface ClassInstanceEntry {
    objectId: number;
    shallowSize: number;
    retainedSize: number | null;
    displayValue: string;
}

export default interface ClassInstancesResponse {
    className: string;
    totalInstances: number;
    instances: ClassInstanceEntry[];
    hasMore: boolean;
}
