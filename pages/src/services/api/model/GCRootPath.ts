export interface PathStep {
    objectId: number;
    className: string;
    fieldName: string | null;
    shallowSize: number;
    retainedSize: number;
    objectParams: Record<string, string>;
    isTarget: boolean;
}

export interface GCRootPath {
    rootObjectId: number;
    rootClassName: string;
    rootType: string;
    threadName: string | null;
    stackFrame: string | null;
    steps: PathStep[];
}
