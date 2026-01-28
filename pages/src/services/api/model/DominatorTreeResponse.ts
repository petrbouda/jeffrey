export interface DominatorNode {
    objectId: number;
    className: string;
    displayValue: string;
    shallowSize: number;
    retainedSize: number;
    retainedPercent: number;
    hasChildren: boolean;
}

export default interface DominatorTreeResponse {
    nodes: DominatorNode[];
    totalHeapSize: number;
    hasMore: boolean;
}
