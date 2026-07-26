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

import GlobalVars from '@/services/GlobalVars';
import WorkspaceEvent from '@/services/api/model/WorkspaceEvent';

/**
 * One delivery from the workspace event stream.
 */
export interface WorkspaceEventsBatch {
  events: WorkspaceEvent[];
  /**
   * The highest event id the server has read, *before* type filtering. Resume from this rather
   * than from the largest id actually received, or a filtered subscription re-scans the events
   * it skipped on every reconnect.
   */
  lastOffset: number;
  /** True once the backlog is drained and the stream is tailing. */
  caughtUp: boolean;
}

/** Connection state, surfaced so the view can show a live indicator. */
export type StreamState = 'connecting' | 'live' | 'reconnecting' | 'closed';

const INITIAL_RETRY_MS = 1000;
const MAX_RETRY_MS = 30000;

/**
 * Subscribes to a workspace's event stream over SSE.
 *
 * Reconnect is implemented here rather than left to `EventSource`: the browser's own retry is
 * disabled the moment we close the source in `onerror`, and more importantly a blind retry would
 * replay from the beginning. Tracking `lastOffset` and passing it back as `fromOffset` is what
 * makes a reconnect resume instead of duplicate.
 */
export default class WorkspaceEventsStreamClient {
  private readonly baseUrl: string;
  private eventSource: EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private retryDelayMs = INITIAL_RETRY_MS;
  private fromOffset = 0;
  private stopped = false;

  constructor(hubId: string, workspaceId: string) {
    this.baseUrl = `${GlobalVars.internalUrl}/hubs/${hubId}/workspaces/${workspaceId}/events/subscribe`;
  }

  /**
   * Opens the stream.
   *
   * @param fromOffset exclusive resume point — the highest event id already displayed
   * @param eventTypes type filter; an empty array means all types
   * @param onBatch called for each batch, including empty heartbeats
   * @param onState called whenever the connection state changes
   */
  subscribe(
    fromOffset: number,
    eventTypes: string[],
    onBatch: (batch: WorkspaceEventsBatch) => void,
    onState: (state: StreamState) => void
  ): void {
    this.unsubscribe();
    this.stopped = false;
    this.fromOffset = fromOffset;
    this.retryDelayMs = INITIAL_RETRY_MS;
    this.open(eventTypes, onBatch, onState);
  }

  private open(
    eventTypes: string[],
    onBatch: (batch: WorkspaceEventsBatch) => void,
    onState: (state: StreamState) => void
  ): void {
    if (this.stopped) {
      return;
    }

    onState(this.retryDelayMs === INITIAL_RETRY_MS ? 'connecting' : 'reconnecting');

    const params = new URLSearchParams();
    params.set('fromOffset', String(this.fromOffset));
    if (eventTypes.length > 0) {
      params.set('eventTypes', eventTypes.join(','));
    }

    const source = new EventSource(`${this.baseUrl}?${params.toString()}`);
    this.eventSource = source;

    source.onopen = () => {
      this.retryDelayMs = INITIAL_RETRY_MS;
      onState('live');
    };

    source.addEventListener('events', (message: MessageEvent) => {
      let batch: WorkspaceEventsBatch;
      try {
        batch = JSON.parse(message.data) as WorkspaceEventsBatch;
      } catch {
        return;
      }

      if (batch.lastOffset > this.fromOffset) {
        this.fromOffset = batch.lastOffset;
      }
      onBatch(batch);
    });

    source.onerror = () => {
      source.close();
      if (this.eventSource === source) {
        this.eventSource = null;
      }
      if (this.stopped) {
        return;
      }
      this.scheduleReconnect(eventTypes, onBatch, onState);
    };
  }

  private scheduleReconnect(
    eventTypes: string[],
    onBatch: (batch: WorkspaceEventsBatch) => void,
    onState: (state: StreamState) => void
  ): void {
    onState('reconnecting');
    const delay = this.retryDelayMs;
    this.retryDelayMs = Math.min(this.retryDelayMs * 2, MAX_RETRY_MS);
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.open(eventTypes, onBatch, onState);
    }, delay);
  }

  /**
   * Closes the stream and cancels any pending reconnect. Safe to call more than once.
   */
  unsubscribe(): void {
    this.stopped = true;
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    if (this.eventSource !== null) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }
}
