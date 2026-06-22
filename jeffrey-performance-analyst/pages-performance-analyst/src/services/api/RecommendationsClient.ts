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

import BasePlatformClient from '@shared/services/api/BasePlatformClient';
import type RecommendationProgress from '@/services/api/model/RecommendationProgress';
import type RecommendationArtifacts from '@/services/api/model/RecommendationArtifacts';
import type Severity from '@/services/api/model/Severity';

export interface RecommendationCallbacks {
  onProgress: (progress: RecommendationProgress) => void;
  onComplete: (result: {
    severity: Severity | null;
    recommendations: string;
    patch: string | null;
  }) => void;
  onError: (error: string) => void;
}

/**
 * Starts a repository-aware AI recommendation run for a recording and follows it over SSE (polling
 * fallback). Mirrors the download-task client: the backend clones the project's repository and runs the
 * AI analysis asynchronously; the terminal COMPLETED progress event carries the recommendations markdown.
 */
export default class RecommendationsClient extends BasePlatformClient {
  private eventSource: EventSource | null = null;
  private pollingInterval: ReturnType<typeof setInterval> | null = null;

  constructor(hubId: string, workspaceId: string, projectId: string, recordingId: string) {
    super(
      `/hubs/${hubId}/workspaces/${workspaceId}/projects/${projectId}/recordings/${recordingId}/ai-recommendations`
    );
  }

  async start(eventType: string, projectName?: string | null): Promise<{ taskId: string }> {
    return super.post<{ taskId: string }>('/start', { eventType, projectName: projectName ?? null });
  }

  /**
   * Previously generated (stored) recommendation artifacts for this recording, one entry per event
   * type. Empty if none have been generated yet.
   */
  async peek(): Promise<RecommendationArtifacts[]> {
    return super.get<RecommendationArtifacts[]>('');
  }

  subscribeToProgress(taskId: string, callbacks: RecommendationCallbacks): void {
    this.unsubscribe();

    const sseUrl = this.baseUrl + '/' + taskId + '/progress';
    try {
      this.eventSource = new EventSource(sseUrl);
      this.eventSource.addEventListener('progress', (event: MessageEvent) => {
        const progress: RecommendationProgress = JSON.parse(event.data);
        this.handleProgress(progress, callbacks);
      });
      this.eventSource.onerror = () => {
        this.eventSource?.close();
        this.eventSource = null;
        this.startPolling(taskId, callbacks);
      };
    } catch {
      this.startPolling(taskId, callbacks);
    }
  }

  private startPolling(taskId: string, callbacks: RecommendationCallbacks): void {
    const poll = async () => {
      try {
        const progress = await this.getStatus(taskId);
        this.handleProgress(progress, callbacks);
      } catch (e: any) {
        this.unsubscribe();
        callbacks.onError(e?.message ?? 'Failed to get recommendation status');
      }
    };
    this.pollingInterval = setInterval(poll, 1000);
    poll();
  }

  private handleProgress(progress: RecommendationProgress, callbacks: RecommendationCallbacks): void {
    callbacks.onProgress(progress);
    if (progress.status === 'COMPLETED') {
      this.unsubscribe();
      callbacks.onComplete({
        severity: progress.severity,
        recommendations: progress.recommendations ?? '',
        patch: progress.patch
      });
    } else if (progress.status === 'FAILED') {
      this.unsubscribe();
      callbacks.onError(progress.errorMessage ?? 'Recommendation generation failed');
    }
  }

  async getStatus(taskId: string): Promise<RecommendationProgress> {
    return super.get<RecommendationProgress>(`/${taskId}/status`);
  }

  async cancel(taskId: string): Promise<void> {
    return super.del<void>(`/${taskId}`);
  }

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
}
