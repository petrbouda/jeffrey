export interface PathStep {
    objectId: number;
    className: string;
    fieldName: string | null;
    shallowSize: number;
    displayValue: string;
    isTarget: boolean;
}

export interface GCRootPath {
    rootObjectId: number;
    rootClassName: string;
    rootType: string;
    steps: PathStep[];
}
