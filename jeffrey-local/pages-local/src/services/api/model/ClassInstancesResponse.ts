export interface ClassInstanceEntry {
    objectId: number;
    shallowSize: number;
    retainedSize: number | null;
    objectParams: Record<string, string>;
}

export default interface ClassInstancesResponse {
    className: string;
    totalInstances: number;
    instances: ClassInstanceEntry[];
    hasMore: boolean;
}
