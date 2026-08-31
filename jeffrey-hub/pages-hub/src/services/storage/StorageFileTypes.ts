import type { FileTypeUsage } from '@/services/api/model/StorageOverview';

/**
 * Visual grouping of the backend's SupportedRecordingFile types for the
 * storage breakdown (category bars, legend, drawer sections).
 */
export type StorageGroupKey =
    | 'recordings'
    | 'profiles'
    | 'heapDumps'
    | 'logs'
    | 'diagnostics'
    | 'temporary';

export interface StorageGroup {
    key: StorageGroupKey;
    label: string;
    /** CSS custom property expression usable directly in style bindings */
    color: string;
}

export interface StorageFileTypeMeta {
    label: string;
    /** Human-readable extension / filename pattern shown as a tag */
    extension: string;
    group: StorageGroupKey;
}

export const STORAGE_GROUPS: StorageGroup[] = [
    { key: 'recordings', label: 'Flight recordings', color: 'var(--color-primary)' },
    { key: 'profiles', label: 'Profiles', color: 'var(--color-purple)' },
    { key: 'heapDumps', label: 'Heap dumps', color: 'var(--color-orange)' },
    { key: 'logs', label: 'Logs', color: 'var(--color-teal)' },
    { key: 'diagnostics', label: 'Diagnostics', color: 'var(--color-amber)' },
    { key: 'temporary', label: 'Temporary', color: 'var(--color-slate-light)' }
];

/** Keyed by the SupportedRecordingFile enum name sent by the backend. */
export const STORAGE_FILE_TYPES: Record<string, StorageFileTypeMeta> = {
    JFR: { label: 'JDK Flight Recording', extension: '.jfr', group: 'recordings' },
    JFR_LZ4: { label: 'LZ4 Compressed JFR', extension: '.jfr.lz4', group: 'recordings' },
    PPROF: { label: 'pprof Profile', extension: '.pprof · .pb.gz', group: 'profiles' },
    OTLP_PROFILE: { label: 'OpenTelemetry Profiles', extension: '.otlp', group: 'profiles' },
    HEAP_DUMP: { label: 'Heap Dump', extension: '.hprof', group: 'heapDumps' },
    HEAP_DUMP_GZ: { label: 'GZ Compressed Heap Dump', extension: '.hprof.gz', group: 'heapDumps' },
    JVM_LOG: { label: 'JVM Log', extension: '*-jvm.log', group: 'logs' },
    APP_LOG: { label: 'Application Log', extension: '*-app.log', group: 'logs' },
    HS_JVM_ERROR_LOG: { label: 'HotSpot JVM Error Log', extension: 'hs-jvm-err.log', group: 'logs' },
    PERF_COUNTERS: { label: 'HotSpot Performance Counters', extension: '.hsperfdata', group: 'diagnostics' },
    ASPROF_TEMP: { label: 'Async Profiler Cache', extension: '.jfr.*~', group: 'temporary' }
};

const UNKNOWN_FILE_TYPE: StorageFileTypeMeta = {
    label: 'Unsupported Files',
    extension: '',
    group: 'diagnostics'
};

export function fileTypeMeta(type: string): StorageFileTypeMeta {
    return STORAGE_FILE_TYPES[type] ?? UNKNOWN_FILE_TYPE;
}

export interface GroupUsage {
    group: StorageGroup;
    sizeBytes: number;
    fileCount: number;
    fileTypes: FileTypeUsage[];
}

/**
 * Aggregates per-file-type usages into the visual groups, keeping the
 * STORAGE_GROUPS order and dropping empty groups.
 */
export function groupUsages(fileTypes: FileTypeUsage[]): GroupUsage[] {
    return STORAGE_GROUPS
        .map(group => {
            const types = fileTypes.filter(usage => fileTypeMeta(usage.type).group === group.key);
            return {
                group,
                sizeBytes: types.reduce((sum, usage) => sum + usage.sizeBytes, 0),
                fileCount: types.reduce((sum, usage) => sum + usage.fileCount, 0),
                fileTypes: types
            };
        })
        .filter(usage => usage.fileTypes.length > 0);
}

export const STORAGE_FILE_TYPE_COUNT = Object.keys(STORAGE_FILE_TYPES).length;
