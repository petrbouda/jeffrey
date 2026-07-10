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

import type { Variant } from '@shared/types/ui';
import type { ExecutionLevel } from '@/services/api/model/JobView';

/**
 * Display metadata for scheduler job types, shared by the Scheduler and
 * Job Executions views.
 */

const displayNames: Record<string, string> = {
    WORKSPACE_EVENTS_REPLICATOR: 'Workspace Events Replicator',
    WORKSPACE_EVENTS_CLEANER: 'Workspace Events Cleaner',
    TEMP_DIRECTORY_CLEANER: 'Temp Directory Cleaner',
    DELETED_PROJECTS_CLEANER: 'Deleted Projects Cleaner',
    PROJECTS_SYNCHRONIZER: 'Projects Synchronizer',
    PROFILER_SETTINGS_SYNCHRONIZER: 'Profiler Settings Synchronizer',
    PROJECT_INSTANCE_SESSION_CLEANER: 'Instance Session Cleaner',
    PROJECT_INSTANCE_RECORDING_CLEANER: 'Instance Recording Cleaner',
    EXPIRED_INSTANCE_CLEANER: 'Expired Instance Cleaner',
    REPOSITORY_JFR_COMPRESSION: 'JFR Compression',
    SESSION_FINISHED_DETECTOR: 'Session Finished Detector'
};

export const displayNameFor = (jobType: string): string => displayNames[jobType] || jobType;

const descriptions: Record<string, string> = {
    WORKSPACE_EVENTS_REPLICATOR:
        'Polls the shared workspace event folder for files written by the CLI and replicates them into the persistent queue. Events are not processed here — they are picked up by Projects Synchronizer.',
    WORKSPACE_EVENTS_CLEANER:
        'Trims the workspace events persistent queue, deleting entries older than the configured retention window so storage stays bounded.',
    TEMP_DIRECTORY_CLEANER:
        'Sweeps the hub temp directory and removes leaked scratch entries (JFR merges, compression staging) older than the retention window.',
    DELETED_PROJECTS_CLEANER:
        'Purges soft-deleted project rows once their retention window has passed, together with any child rows that still reference them.',
    PROJECTS_SYNCHRONIZER:
        'Drains the workspace event queue per workspace and applies project create / delete and session lifecycle events, keeping the server’s project list in sync with what each workspace reports.',
    PROFILER_SETTINGS_SYNCHRONIZER:
        'Resolves the effective profiler settings (global → workspace → project) for every workspace and uploads them to the remote workspace, pruning legacy versions to the configured max-versions cap.',
    PROJECT_INSTANCE_SESSION_CLEANER:
        'Removes Project Instance Sessions older than the configured duration. Once a session is removed, all associated Recordings and Additional Files (HeapDump, PerfCounters, ...) are removed as well.',
    PROJECT_INSTANCE_RECORDING_CLEANER:
        'Removes only Recordings in the active (latest) Project Instance Session. It does not remove recordings in older sessions — it just ensures that rolling recordings in the latest session are bounded by age.',
    EXPIRED_INSTANCE_CLEANER:
        'Removes expired instance metadata after the configured retention period. Instances transition to EXPIRED when all their sessions are cleaned up, and this job permanently deletes those rows.',
    REPOSITORY_JFR_COMPRESSION:
        'Compresses finished JFR recording files using LZ4 compression to save storage space. Processes the active and latest finished sessions on each tick.',
    SESSION_FINISHED_DETECTOR:
        'Detects when repository sessions become finished using a heartbeat-based strategy and emits SESSION_FINISHED workspace events so downstream consumers can react.'
};

export const descriptionFor = (jobType: string): string => descriptions[jobType] || '';

const icons: Record<string, [string, string]> = {
    WORKSPACE_EVENTS_REPLICATOR: ['bi-broadcast', 'job-icon-bell'],
    WORKSPACE_EVENTS_CLEANER: ['bi-eraser', 'job-icon-broom'],
    TEMP_DIRECTORY_CLEANER: ['bi-eraser', 'job-icon-broom'],
    DELETED_PROJECTS_CLEANER: ['bi-trash', 'job-icon-trash'],
    PROJECTS_SYNCHRONIZER: ['bi-arrow-repeat', 'job-icon-sync'],
    PROFILER_SETTINGS_SYNCHRONIZER: ['bi-cpu', 'job-icon-cpu'],
    PROJECT_INSTANCE_SESSION_CLEANER: ['bi-trash', 'job-icon-trash'],
    PROJECT_INSTANCE_RECORDING_CLEANER: ['bi-trash', 'job-icon-trash'],
    EXPIRED_INSTANCE_CLEANER: ['bi-trash', 'job-icon-trash'],
    REPOSITORY_JFR_COMPRESSION: ['bi-file-zip', 'job-icon-zip'],
    SESSION_FINISHED_DETECTOR: ['bi-check-circle', 'job-icon-check']
};

export const iconFor = (jobType: string): string => icons[jobType]?.[0] || 'bi-gear';
export const iconClassFor = (jobType: string): string => icons[jobType]?.[1] || 'job-icon-default';

const levelLabels: Record<ExecutionLevel, string> = {
    GLOBAL: 'Global',
    WORKSPACE: 'Per workspace',
    PROJECT: 'Per project'
};

export const levelLabelFor = (level: ExecutionLevel): string => levelLabels[level];

const levelVariants: Record<ExecutionLevel, Variant> = {
    GLOBAL: 'violet',
    WORKSPACE: 'blue',
    PROJECT: 'primary'
};

export const levelVariantFor = (level: ExecutionLevel): Variant => levelVariants[level];
