export interface StackFrameLocal {
    objectId: number;
    className: string;
    fieldName: string;
    shallowSize: number;
}

export default interface ThreadStackFrame {
    className: string;
    methodName: string;
    sourceFile: string | null;
    lineNumber: number;
    locals: StackFrameLocal[];
}
