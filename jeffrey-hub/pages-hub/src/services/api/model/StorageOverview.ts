export interface FileTypeUsage {
    /** SupportedRecordingFile enum name, e.g. JFR, HEAP_DUMP_GZ */
    type: string;
    sizeBytes: number;
    fileCount: number;
}

export interface StoredFile {
    fileName: string;
    sizeBytes: number;
}

export interface ProjectStorage {
    workspaceId: string;
    workspaceName: string;
    projectId: string;
    projectName: string;
    projectLabel: string | null;
    totalSizeBytes: number;
    totalFiles: number;
    // UTC epoch millis — format with FormattingService, never by parsing date strings
    lastActivityTimeMillis: number;
    fileTypes: FileTypeUsage[];
    largestFiles: StoredFile[];
}

export interface StorageOverview {
    // UTC epoch millis of when the snapshot was computed by the refresher job
    computedAtMillis: number;
    diskTotalBytes: number;
    diskUsableBytes: number;
    databaseSizeBytes: number;
    tempSizeBytes: number;
    projects: ProjectStorage[];
}
