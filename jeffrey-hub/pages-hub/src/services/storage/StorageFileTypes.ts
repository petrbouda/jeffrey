import type { FileTypeUsage } from '@/services/api/model/StorageOverview';

/**
 * Visual grouping of the kinds the hub tells apart for the storage breakdown
 * (category bars, legend, drawer sections). The hub reads none of the files it
 * stores, so it knows a flight recording — plain or compressed — from everything
 * else, and nothing finer: a heap dump, a log and a pprof profile are all "other".
 */
export type StorageGroupKey = 'recordings' | 'other';

export interface StorageGroup {
    key: StorageGroupKey;
    label: string;
    /**
     * CSS `background` value usable directly in style bindings — a design-token
     * colour for the recordings, a hatch for the group the hub does not classify.
     */
    fill: string;
}

export interface StorageFileTypeMeta {
    label: string;
    /** Human-readable extension / filename pattern shown as a tag */
    extension: string;
    group: StorageGroupKey;
}

/**
 * Files the hub does not classify are not a kind of file, so their group is
 * drawn as a hatch over the bar's own background rather than as a second colour.
 */
const OTHER_FILL =
    'repeating-linear-gradient(135deg, var(--color-slate-muted) 0 2px, transparent 2px 5px), var(--color-grey-bg)';

export const STORAGE_GROUPS: StorageGroup[] = [
    { key: 'recordings', label: 'Flight recordings', fill: 'var(--color-primary)' },
    { key: 'other', label: 'Other files', fill: OTHER_FILL }
];

/** Keyed by the kind name sent by the backend. */
export const STORAGE_FILE_TYPES: Record<string, StorageFileTypeMeta> = {
    JFR: { label: 'JDK Flight Recording', extension: '.jfr', group: 'recordings' },
    JFR_LZ4: { label: 'LZ4 Compressed JFR', extension: '.jfr.lz4', group: 'recordings' }
};

/**
 * The backend's OTHER bucket — heap dumps, logs, perf counters, pprof and OTLP
 * profiles, the profiler's scratch files — and any kind name this catalogue has
 * not caught up with yet.
 */
const OTHER_FILE_TYPE: StorageFileTypeMeta = {
    label: 'Other files',
    extension: 'logs · heap dumps · diagnostics',
    group: 'other'
};

export function fileTypeMeta(type: string): StorageFileTypeMeta {
    return STORAGE_FILE_TYPES[type] ?? OTHER_FILE_TYPE;
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
