/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import { ref, computed } from 'vue';
import DownloadTaskClient from '@/services/api/DownloadTaskClient';
import DownloadProgress from '@/services/api/model/DownloadProgress';
import DownloadTaskStatus from '@/services/api/model/DownloadTaskStatus';
import { ToastService } from '@/services/ToastService';

/**
 * Entry for a single download in the queue.
 */
interface DownloadEntry {
    progress: DownloadProgress;
    client: DownloadTaskClient;
    onComplete?: () => void;
}

/**
 * Global state for download progress queue.
 * Supports multiple concurrent downloads.
 */

// Global state
const isOpen = ref(false);
const isExpanded = ref(true);
const downloads = ref<Map<string, DownloadEntry>>(new Map());

/**
 * All downloads as an array for rendering.
 */
const allDownloads = computed(() =>
    Array.from(downloads.value.values()).map(entry => entry.progress)
);

/**
 * Check if there are any active (non-completed) downloads.
 */
const hasActiveDownloads = computed(() =>
    Array.from(downloads.value.values()).some(entry =>
        entry.progress.status === DownloadTaskStatus.PENDING ||
        entry.progress.status === DownloadTaskStatus.DOWNLOADING ||
        entry.progress.status === DownloadTaskStatus.PROCESSING
    )
);

/**
 * Aggregate progress across all downloads.
 */
const aggregateProgress = computed(() => {
    const entries = Array.from(downloads.value.values());
    if (entries.length === 0) return 0;

    const totalBytes = entries.reduce((sum, e) => sum + (e.progress.totalBytes || 0), 0);
    const downloadedBytes = entries.reduce((sum, e) => sum + (e.progress.downloadedBytes || 0), 0);

    return totalBytes > 0 ? Math.round((downloadedBytes / totalBytes) * 100) : 0;
});

/**
 * Worst status among all downloads (for minimized indicator color).
 */
const aggregateStatus = computed(() => {
    const entries = Array.from(downloads.value.values());
    if (entries.length === 0) return null;

    // Priority: FAILED > CANCELLED > DOWNLOADING/PENDING/PROCESSING > COMPLETED
    if (entries.some(e => e.progress.status === DownloadTaskStatus.FAILED)) {
        return DownloadTaskStatus.FAILED;
    }
    if (entries.some(e => e.progress.status === DownloadTaskStatus.CANCELLED)) {
        return DownloadTaskStatus.CANCELLED;
    }
    if (entries.some(e =>
        e.progress.status === DownloadTaskStatus.PENDING ||
        e.progress.status === DownloadTaskStatus.DOWNLOADING ||
        e.progress.status === DownloadTaskStatus.PROCESSING
    )) {
        return DownloadTaskStatus.DOWNLOADING;
    }
    return DownloadTaskStatus.COMPLETED;
});

/**
 * Starts a download and adds it to the queue.
 */
const startDownload = async (
    workspaceId: string,
    projectId: string,
    sessionId: string,
    fileIds: string[],
    onComplete?: () => void
) => {
    try {
        // Create client for this download
        const client = new DownloadTaskClient(workspaceId, projectId);

        // Start the download task
        const task = await client.startDownload(sessionId, fileIds);

        // Initialize progress state
        const progress: DownloadProgress = {
            taskId: task.taskId,
            sessionName: sessionId,
            status: DownloadTaskStatus.PENDING,
            totalFiles: fileIds.length,
            completedFiles: 0,
            activeDownloads: [],
            completedDownloads: [],
            totalBytes: 0,
            downloadedBytes: 0,
            percentComplete: 0,
            errorMessage: null,
            startedAt: Date.now(),
            completedAt: null
        };

        // Add to downloads map
        downloads.value.set(task.taskId, {
            progress,
            client,
            onComplete
        });

        // Show the panel expanded
        isOpen.value = true;
        isExpanded.value = true;

        // Subscribe to progress updates
        client.subscribeToProgress(
            task.taskId,
            (p) => {
                const entry = downloads.value.get(task.taskId);
                if (entry) {
                    entry.progress = { ...p, sessionName: sessionId };
                    downloads.value = new Map(downloads.value); // Trigger reactivity
                }
            },
            () => {
                const entry = downloads.value.get(task.taskId);
                if (entry?.onComplete) entry.onComplete();
            },
            (error) => {
                ToastService.error('Download Failed', error);
            }
        );
    } catch (e: any) {
        ToastService.error('Download Failed',
            e?.response?.data?.message || e.message || 'Failed to start download');
    }
};

/**
 * Cancels a specific download.
 */
const cancelDownload = async (taskId: string) => {
    const entry = downloads.value.get(taskId);
    if (!entry) return;

    // Cancel on server
    try {
        await entry.client.cancelDownload(taskId);
    } catch (e) {
        console.error('Failed to cancel download:', e);
    }

    // Update status
    entry.progress = {
        ...entry.progress,
        status: DownloadTaskStatus.CANCELLED
    };
    downloads.value = new Map(downloads.value);
};

/**
 * Closes/removes a specific download from the queue.
 */
const closeDownload = (taskId: string) => {
    const entry = downloads.value.get(taskId);
    if (!entry) return;

    // Cleanup resources
    entry.client.unsubscribe();

    // Remove from map
    downloads.value.delete(taskId);
    downloads.value = new Map(downloads.value);

    // Close panel if no more downloads
    if (downloads.value.size === 0) {
        isOpen.value = false;
        isExpanded.value = true;
    }
};

/**
 * Expands the panel from minimized state.
 */
const expand = () => {
    isExpanded.value = true;
};

/**
 * Minimizes the panel to a floating indicator.
 */
const minimize = () => {
    isExpanded.value = false;
};

/**
 * Closes the panel (hides it, downloads continue in background).
 */
const closePanel = () => {
    // Only close if no active downloads
    if (!hasActiveDownloads.value) {
        // Cleanup all entries
        for (const [, entry] of downloads.value) {
            entry.client.unsubscribe();
        }
        downloads.value.clear();
        downloads.value = new Map(downloads.value);
    }

    isOpen.value = false;
    isExpanded.value = true;
};

/**
 * Global download progress store.
 * Use this in components that need to access or control download state.
 */
export const downloadProgressStore = {
    // State (reactive refs)
    isOpen,
    isExpanded,
    downloads,

    // Computed
    allDownloads,
    hasActiveDownloads,
    aggregateProgress,
    aggregateStatus,

    // Actions
    startDownload,
    cancelDownload,
    closeDownload,
    expand,
    minimize,
    closePanel
};
