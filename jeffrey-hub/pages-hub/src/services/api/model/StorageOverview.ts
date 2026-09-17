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
}

export interface StorageOverview {
    // UTC epoch millis of when the snapshot was computed by the refresher job
    computedAtMillis: number;
    databaseSizeBytes: number;
    tempSizeBytes: number;
    projects: ProjectStorage[];
}
