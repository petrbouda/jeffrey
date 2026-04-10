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

import GlobalVars from '@/services/GlobalVars'
import type { StreamingEvent } from '@/services/api/EventStreamingClient'

/**
 * Client for replaying historical JFR events from a single session's dumped recording files via SSE.
 */
export default class ReplayStreamClient {
  private readonly baseUrl: string
  private eventSource: EventSource | null = null

  constructor(workspaceId: string, projectId: string) {
    this.baseUrl = `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects/${projectId}/replay-stream`
  }

  /**
   * Starts replaying historical JFR events from a single session's dumped recording files.
   *
   * @param sessionId - The session to replay events from
   * @param eventTypes - JFR event types to receive (empty array = all events)
   * @param onEvents - Callback for each batch of events
   * @param onComplete - Called when replay finishes (all files processed)
   * @param onError - Called when the SSE connection itself is lost
   * @param options - Optional: startTime/endTime (epoch millis)
   */
  replay(
    sessionId: string,
    eventTypes: string[],
    onEvents: (events: StreamingEvent[]) => void,
    onComplete: () => void,
    onError: (error: string) => void,
    options?: { startTime?: number; endTime?: number }
  ): void {
    this.cancel()

    const params = new URLSearchParams()
    params.set('sessionId', sessionId)
    if (eventTypes.length > 0) {
      params.set('eventTypes', eventTypes.join(','))
    }
    if (options?.startTime != null) {
      params.set('startTime', String(options.startTime))
    }
    if (options?.endTime != null) {
      params.set('endTime', String(options.endTime))
    }

    const url = `${this.baseUrl}/subscribe?${params.toString()}`

    this.eventSource = new EventSource(url)

    this.eventSource.addEventListener('events', (event: MessageEvent) => {
      const events: StreamingEvent[] = JSON.parse(event.data)
      onEvents(events)
    })

    this.eventSource.onerror = () => {
      this.cancel()
      onError('Replay connection lost')
    }
  }

  /**
   * Cancels the replay and closes the SSE connection.
   */
  cancel(): void {
    if (this.eventSource) {
      this.eventSource.close()
      this.eventSource = null
    }
  }
}
