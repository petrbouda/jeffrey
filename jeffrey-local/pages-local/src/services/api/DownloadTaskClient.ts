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

import BasePlatformClient from '@/services/api/BasePlatformClient';
import DownloadTask from '@/services/api/model/DownloadTask';
import DownloadProgress from '@/services/api/model/DownloadProgress';
import DownloadTaskStatus from '@/services/api/model/DownloadTaskStatus';

/**
 * Client for managing download tasks with progress tracking.
 * Supports SSE for real-time updates with polling fallback.
 */
export default class DownloadTaskClient extends BasePlatformClient {
    private eventSource: EventSource | null = null;
    private pollingInterval: ReturnType<typeof setInterval> | null = null;

    constructor(workspaceId: string, projectId: string) {
        super(`/workspaces/${workspaceId}/projects/${projectId}/download`);
    }

    /**
     * Starts a new download task and returns the task info.
     */
    async startDownload(sessionId: string, fileIds: string[]): Promise<DownloadTask> {
        return super.post<DownloadTask>('/start', { sessionId, recordingIds: fileIds });
    }

    /**
     * Subscribes to real-time progress updates via SSE.
     * Falls back to polling if SSE fails.
     */
    subscribeToProgress(
        taskId: string,
        onProgress: (progress: DownloadProgress) => void,
        onComplete: () => void,
        onError: (error: string) => void
    ): void {
        this.unsubscribe(); // Clean up any existing subscription

        const sseUrl = this.baseUrl + '/' + taskId + '/progress';

        try {
            this.eventSource = new EventSource(sseUrl);

            this.eventSource.addEventListener('progress', (event: MessageEvent) => {
                const progress: DownloadProgress = JSON.parse(event.data);
                onProgress(progress);

                if (this.isTerminalStatus(progress.status)) {
                    this.unsubscribe();
                    if (progress.status === DownloadTaskStatus.COMPLETED) {
                        onComplete();
                    } else if (progress.status === DownloadTaskStatus.FAILED) {
                        onError(progress.errorMessage || 'Download failed');
                    }
                }
            });

            this.eventSource.onerror = () => {
                console.warn('SSE connection failed, falling back to polling');
                this.eventSource?.close();
                this.eventSource = null;
                this.startPolling(taskId, onProgress, onComplete, onError);
            };
        } catch (e) {
            console.warn('SSE not supported, using polling');
            this.startPolling(taskId, onProgress, onComplete, onError);
        }
    }

    /**
     * Polling fallback for when SSE is unavailable.
     */
    private startPolling(
        taskId: string,
        onProgress: (progress: DownloadProgress) => void,
        onComplete: () => void,
        onError: (error: string) => void
    ): void {
        const pollStatus = async () => {
            try {
                const progress = await this.getStatus(taskId);
                onProgress(progress);

                if (this.isTerminalStatus(progress.status)) {
                    this.unsubscribe();
                    if (progress.status === DownloadTaskStatus.COMPLETED) {
                        onComplete();
                    } else if (progress.status === DownloadTaskStatus.FAILED) {
                        onError(progress.errorMessage || 'Download failed');
                    }
                }
            } catch (e: any) {
                onError(e.message || 'Failed to get status');
                this.unsubscribe();
            }
        };

        // Poll every 500ms
        this.pollingInterval = setInterval(pollStatus, 500);
        pollStatus(); // Immediate first call
    }

    /**
     * Gets current status (for polling fallback).
     */
    async getStatus(taskId: string): Promise<DownloadProgress> {
        return super.get<DownloadProgress>(`/${taskId}/status`);
    }

    /**
     * Cancels an ongoing download.
     */
    async cancelDownload(taskId: string): Promise<void> {
        return super.del<void>(`/${taskId}`);
    }

    /**
     * Cleans up subscriptions.
     */
    unsubscribe(): void {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
        }
    }

    private isTerminalStatus(status: DownloadTaskStatus): boolean {
        return status === DownloadTaskStatus.COMPLETED ||
            status === DownloadTaskStatus.FAILED ||
            status === DownloadTaskStatus.CANCELLED;
    }
}
