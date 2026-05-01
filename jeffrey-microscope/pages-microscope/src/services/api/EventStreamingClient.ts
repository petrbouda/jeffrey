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

/**
 * A typed value from a JFR event field. Exactly one of the value properties will be set.
 * Annotations are resolved: @Timestamp -> epoch millis, @Timespan -> nanoseconds, @Percentage -> float.
 */
export interface TypedValue {
  stringValue?: string
  longValue?: number
  doubleValue?: number
  boolValue?: boolean
  floatValue?: number
}

/**
 * A single JFR event received from the streaming subscription.
 */
export interface StreamingEvent {
  eventType: string
  sessionId: string
  timestamp: number
  fields: Record<string, TypedValue>
}

/**
 * Client for subscribing to live JFR events from a remote session via SSE.
 * Each SSE message contains a batch of events delivered on JFR's flush cycle (~1s).
 */
export default class EventStreamingClient {
  private readonly baseUrl: string
  private eventSource: EventSource | null = null

  constructor(workspaceId: string, projectId: string) {
    this.baseUrl = `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects/${projectId}/live-stream`
  }

  /**
   * Subscribes to live JFR events for one or more sessions.
   * Always continuous — the stream stays open waiting for new events.
   *
   * @param sessionIds - The sessions to stream events from (must be non-empty)
   * @param eventTypes - JFR event types to receive (empty array = all events)
   * @param onEvents - Callback for each batch of events (~1/sec). Each event carries its sessionId.
   * @param _onComplete - Called when every session's stream has ended
   * @param onError - Called when the SSE connection itself is lost
   * @param onSessionError - Called with the sessionId of a session whose server-side stream errored;
   *                         other sessions keep streaming
   */
  subscribe(
    sessionIds: string[],
    eventTypes: string[],
    onEvents: (events: StreamingEvent[]) => void,
    _onComplete: () => void,
    onError: (error: string) => void,
    onSessionError: (sessionId: string) => void
  ): void {
    this.unsubscribe()

    const params = new URLSearchParams()
    params.set('sessionIds', sessionIds.join(','))
    if (eventTypes.length > 0) {
      params.set('eventTypes', eventTypes.join(','))
    }

    const url = `${this.baseUrl}/subscribe?${params.toString()}`

    this.eventSource = new EventSource(url)

    this.eventSource.addEventListener('events', (event: MessageEvent) => {
      const events: StreamingEvent[] = JSON.parse(event.data)
      onEvents(events)
    })

    this.eventSource.addEventListener('sessionError', (event: MessageEvent) => {
      try {
        const payload: { sessionId: string } = JSON.parse(event.data)
        onSessionError(payload.sessionId)
      } catch {
        // malformed payload — ignore
      }
    })

    this.eventSource.onerror = () => {
      this.unsubscribe()
      onError('Event streaming connection lost')
    }
  }

  /**
   * Closes the SSE connection and stops receiving events.
   */
  unsubscribe(): void {
    if (this.eventSource) {
      this.eventSource.close()
      this.eventSource = null
    }
  }
}
