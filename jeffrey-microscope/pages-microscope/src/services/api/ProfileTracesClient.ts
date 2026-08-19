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
import {
  encodeCondition,
  type TraceAttributeConditionModel,
  type TraceAttributeKeyId,
  type TraceAttributeKeyRow,
  type TraceAttributeLatency,
  type TraceAttributeScope,
  type TraceAttributeSearchResult,
  type TraceAttributeTimelineBucket,
  type TraceAttributeValues,
  type TraceAttributeValueSortField,
  type TraceSpanTypeRow
} from '@/services/api/model/trace/TraceAttributeModels';
import type {
  TraceContext,
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
function listParams(
  query: TraceListQuery | TraceOperationListQuery
): Record<string, string | number | boolean> {
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

  /**
   * What the JVM was doing to the trace — the pauses that crossed it, what each span waited on, and
   * where its time went.
   *
   * A second request rather than part of {@link getTrace} because it is a slower question: the
   * pauses come from a scan of the events table rather than the derived span tables. The waterfall
   * draws immediately and the context arrives over it.
   */
  public getTraceContext(traceId: string): Promise<TraceContext> {
    return this.get<TraceContext>(`/${traceId}/context`);
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

  /** Every attribute key the profile recorded — the catalog the key rail renders. */
  public getAttributeKeys(eventType?: string): Promise<TraceAttributeKeyRow[]> {
    // Scoped to an event type this is the picker's second step, where the counts are that type's
    // own; unscoped it is the whole catalog, which is what the search builder offers.
    return this.get<TraceAttributeKeyRow[]>(
      '/attributes/keys',
      eventType === undefined ? {} : { eventType }
    );
  }

  /**
   * The traces whose spans satisfy every condition, with the spans that satisfied them.
   *
   * The conditions repeat as a bare `where` parameter rather than travelling as one encoded blob, so
   * a filter stays legible in the address bar and can be edited there.
   */
  public searchByAttributes(query: AttributeSearchQuery): Promise<TraceAttributeSearchResult> {
    return this.get<TraceAttributeSearchResult>(
      '/attributes/search',
      searchParams(query),
      REPEATED_PARAMS
    );
  }

  /** Where in the recording the matches fell, against every trace as a backdrop. */
  public getAttributeTimeline(
    conditions: TraceAttributeConditionModel[],
    scope: TraceAttributeScope,
    buckets?: number
  ): Promise<TraceAttributeTimelineBucket[]> {
    return this.get<TraceAttributeTimelineBucket[]>(
      '/attributes/timeline',
      {
        where: conditions.map(encodeCondition),
        scope,
        ...(buckets === undefined ? {} : { buckets })
      },
      REPEATED_PARAMS
    );
  }

  /** One key's values, ranked by what they cost. */
  public getAttributeValues(
    key: TraceAttributeKeyId,
    eventType: string,
    sort?: TraceAttributeValueSortField,
    limit?: number
  ): Promise<TraceAttributeValues> {
    // Scoped to the event type the key was reached through, so the breakdown answers the question
    // the picker asked rather than the wider one about every span carrying the key.
    return this.get<TraceAttributeValues>('/attributes/values', {
      ...keyParams(key),
      eventType,
      ...(sort === undefined ? {} : { sort }),
      ...(limit === undefined ? {} : { limit })
    });
  }

  /** The event types that produced spans — the attribute picker's first step. */
  public getSpanEventTypes(): Promise<TraceSpanTypeRow[]> {
    return this.get<TraceSpanTypeRow[]>('/attributes/event-types');
  }

  /** How each of the key's values is distributed over trace duration. */
  public getAttributeLatency(
    key: TraceAttributeKeyId,
    eventType: string,
    maxValues?: number
  ): Promise<TraceAttributeLatency> {
    return this.get<TraceAttributeLatency>('/attributes/latency', {
      ...keyParams(key),
      eventType,
      ...(maxValues === undefined ? {} : { maxValues })
    });
  }
}

/**
 * Axios repeats an array parameter as `where[]=a&where[]=b` by default, which Spring does not bind
 * to a `List<String>` — it looks for the bare name.
 */
const REPEATED_PARAMS = { repeatArrayParams: true };

/** A key as query parameters. The owner is omitted rather than sent empty when there is none. */
function keyParams(key: TraceAttributeKeyId): Record<string, string> {
  return {
    source: key.source,
    key: key.key,
    ...(key.owner === null ? {} : { owner: key.owner })
  };
}

/** How the attribute search is asked for. */
export interface AttributeSearchQuery {
  conditions: TraceAttributeConditionModel[];
  scope: TraceAttributeScope;
  sort?: string;
  desc?: boolean;
  limit?: number;
  offset?: number;
}

function searchParams(query: AttributeSearchQuery): Record<string, unknown> {
  return {
    where: query.conditions.map(encodeCondition),
    scope: query.scope,
    ...(query.sort === undefined ? {} : { sort: query.sort }),
    ...(query.desc === undefined ? {} : { desc: query.desc }),
    ...(query.limit === undefined ? {} : { limit: query.limit }),
    ...(query.offset === undefined ? {} : { offset: query.offset })
  };
}
