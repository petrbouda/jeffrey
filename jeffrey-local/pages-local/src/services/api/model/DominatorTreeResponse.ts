export interface DominatorNode {
    objectId: number;
    className: string;
    objectParams: Record<string, string>;
    fieldName: string | null;
    shallowSize: number;
    retainedSize: number;
    retainedPercent: number;
    hasChildren: boolean;
    gcRootKind: string | null;
}

export default interface DominatorTreeResponse {
    nodes: DominatorNode[];
    totalHeapSize: number;
    compressedOops: boolean;
    hasMore: boolean;
}
