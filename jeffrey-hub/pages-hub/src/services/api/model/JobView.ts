/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

export type ExecutionLevel = 'GLOBAL' | 'WORKSPACE' | 'PROJECT';

/**
 * All job types served by the scheduler, mirroring the backend {@code JobType} enum
 * (same order). The scheduler UI types its display-metadata maps as
 * {@code Record<JobTypeName, ...>} so the compiler forces a name/description/icon
 * for every declared job type — extend this union when a new enum constant is added.
 */
export type JobTypeName =
    | 'WORKSPACE_EVENTS_REPLICATOR'
    | 'WORKSPACE_EVENTS_CLEANER'
    | 'TEMP_DIRECTORY_CLEANER'
    | 'DELETED_PROJECTS_CLEANER'
    | 'STORAGE_OVERVIEW_REFRESHER'
    | 'PROJECTS_SYNCHRONIZER'
    | 'PROFILER_SETTINGS_SYNCHRONIZER'
    | 'PROJECT_INSTANCE_SESSION_CLEANER'
    | 'PROJECT_INSTANCE_RECORDING_CLEANER'
    | 'PROJECT_STORAGE_QUOTA_CLEANER'
    | 'ORPHANED_SESSION_CLEANER'
    | 'EXPIRED_INSTANCE_CLEANER'
    | 'REPOSITORY_JFR_COMPRESSION'
    | 'SESSION_FINISHED_DETECTOR'
    | 'SESSION_FILE_DETECTOR';

export interface JobView {
    jobType: string;
    executionLevel: ExecutionLevel;
    period: string;
    params: Record<string, string>;
    enabled: boolean;
}

/**
 * Turn an ISO-8601 duration like {@code PT30S}, {@code PT1H}, {@code P1D}
 * into a compact human form ({@code 30s}, {@code 1h}, {@code 1d}).
 */
export function formatPeriod(iso: string): string {
    if (!iso) return '';
    const m = iso.match(/^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/);
    if (!m) return iso;
    const [, d, h, min, s] = m;
    if (d && !h && !min && !s) return `${d}d`;
    if (!d && h && !min && !s) return `${h}h`;
    if (!d && !h && min && !s) return `${min}m`;
    if (!d && !h && !min && s) return `${s}s`;
    const parts = [];
    if (d) parts.push(`${d}d`);
    if (h) parts.push(`${h}h`);
    if (min) parts.push(`${min}m`);
    if (s) parts.push(`${s}s`);
    return parts.join(' ') || iso;
}
