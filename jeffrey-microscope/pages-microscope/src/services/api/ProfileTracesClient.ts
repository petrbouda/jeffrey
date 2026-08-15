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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import FlamegraphPanel from '@/services/api/model/FlamegraphPanel';
import type {
  TraceDetail,
  TraceListQuery,
  TraceOperationId,
  TraceOperationListQuery,
  TraceOperationsPage,
  TraceOperationSummary,
  TraceOverview,
  TraceRow,
  TraceSpanEvents,
  TraceTimelineBucket,
  TracesPage
} from '@/services/api/model/trace/TraceModels';

/**
 * A trace type as query parameters. All three travel together — narrowing on the name alone would
 * merge an inbound call with an outbound one of the same name.
 *
 * They are query parameters rather than path segments because operation names contain slashes and
 * braces (`GET /api/internal/profiles/{profileId}/heap/instances`).
 */
function operationParams(operation: TraceOperationId): Record<string, string> {
  return { name: operation.name, kind: operation.kind, eventType: operation.eventType };
}

/**
 * A list query as query parameters, leaving out everything the caller did not set.
 *
 * Omitting rather than sending defaults keeps the server's own defaults authoritative, so the two
 * cannot drift apart — and keeps an unfiltered request looking exactly like the request this list
 * made before it could be filtered at all.
 */
function listParams(query: TraceListQuery | TraceOperationListQuery): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params[key] = value;
    }
  }
  return params;
}

export default class ProfileTracesClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'traces');
  }

  /**
   * A page of traces. Narrowing happens on the server because the list is capped: filtering the
   * fetched page would only ever search the slowest few hundred traces, so a search for anything
   * outside them comes back empty while the trace sits in the profile.
   */
  public getTraces(query: TraceListQuery = {}): Promise<TracesPage> {
    return this.get<TracesPage>('', listParams(query));
  }

  /** How traces were spread over the recording, for the density strip above the list. */
  public getTimeline(buckets?: number): Promise<TraceTimelineBucket[]> {
    return this.get<TraceTimelineBucket[]>(
      '/timeline',
      buckets === undefined ? undefined : { buckets }
    );
  }

  /** Profile-wide totals, which the capped trace list cannot be summed into. */
  public getOverview(): Promise<TraceOverview> {
    return this.get<TraceOverview>('/overview');
  }

  public getTrace(traceId: string): Promise<TraceDetail> {
    return this.get<TraceDetail>(`/${traceId}`);
  }

  /** A page of operations, narrowed and ordered on the server for the same reason traces are. */
  public getOperations(query: TraceOperationListQuery = {}): Promise<TraceOperationsPage> {
    return this.get<TraceOperationsPage>('/operations', listParams(query));
  }

  /**
   * The traces of one type, chronologically. One call feeds both the timeline and the slowest list
   * of the operation drill-down.
   */
  public getOperationTraces(operation: TraceOperationId, limit?: number): Promise<TraceRow[]> {
    return this.get<TraceRow[]>('/operation/traces', {
      ...operationParams(operation),
      ...(limit === undefined ? {} : { limit })
    });
  }

  /**
   * The span breakdown and thread split of one operation — the parts of its summary that are not
   * arithmetic over the trace list the caller already has.
   */
  public getOperationSummary(operation: TraceOperationId): Promise<TraceOperationSummary> {
    return this.get<TraceOperationSummary>('/operation/summary', operationParams(operation));
  }

  /** Which event types recorded samples inside the traces of one type, with their real counts. */
  public getOperationPanels(operation: TraceOperationId): Promise<FlamegraphPanel[]> {
    return this.get<FlamegraphPanel[]>('/operation/panels', operationParams(operation));
  }

  /**
   * What the JVM was doing on the span's thread while it was open — a page, with a flag saying
   * whether the window held more events than the backend's row cap.
   */
  public getSpanEvents(traceId: string, spanId: string): Promise<TraceSpanEvents> {
    return this.get<TraceSpanEvents>(`/${traceId}/spans/${spanId}/events`);
  }

  /**
   * Which event types actually recorded samples inside the span, with their real counts, so the
   * drill-down offers only flamegraphs that exist.
   */
  public getSpanPanels(
    traceId: string,
    spanId: string,
    selfOnly: boolean
  ): Promise<FlamegraphPanel[]> {
    return this.get<FlamegraphPanel[]>(`/${traceId}/spans/${spanId}/panels`, { selfOnly });
  }
}
