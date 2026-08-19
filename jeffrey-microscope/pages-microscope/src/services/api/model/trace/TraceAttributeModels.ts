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

// API models for Traces by Attributes. Mirrors the backend records; durations are nanoseconds and
// span ids are 16-char hex strings, for the same reasons TraceModels states.

import type { TraceRow } from '@/services/api/model/trace/TraceModels';

/**
 * Where a key came from. The three are kept apart everywhere they are shown: a value a developer
 * attached at a call site and a field an event type declares about itself do not mean the same
 * thing, and a flat list of both reads as one convention badly applied.
 */
export type TraceAttributeSource = 'ATTRIBUTE' | 'EVENT_FIELD' | 'SPAN_SHAPE';

export type TraceAttributeValueKind = 'STRING' | 'NUMBER' | 'BOOLEAN';

export type TraceAttributeOperator =
  'EQ' | 'NOT_EQ' | 'CONTAINS' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'EXISTS';

/**
 * Whether the conditions have to hold together on one span, or anywhere in the trace.
 *
 * Not a preference — the two are different questions with different answers, because attributes are
 * per-span and are never inherited down the tree.
 */
export type TraceAttributeScope = 'TRACE' | 'SPAN';

export type TraceAttributeValueSortField =
  'TOTAL_TIME' | 'TRACES' | 'P50' | 'P95' | 'MAX' | 'ERRORS' | 'VALUE';

/** What identifies one key. The owner is part of the identity, not decoration. */
export interface TraceAttributeKeyId {
  source: TraceAttributeSource;
  /** The event type declaring it, for an event field; null for the other sources. */
  owner: string | null;
  key: string;
}

export interface TraceAttributeKeyRow extends TraceAttributeKeyId {
  valueKind: TraceAttributeValueKind;
  distinctValues: number;
  spanCount: number;
  traceCount: number;
  /**
   * Whether the key has too many values to break down. A `user.id` on sixty thousand spans is
   * legitimate instrumentation, and every breakdown of it is noise wearing the shape of an answer.
   */
  searchOnly: boolean;
}

/** One condition of a search, as the UI holds it before it is encoded into the URL. */
export interface TraceAttributeConditionModel {
  source: TraceAttributeSource;
  owner: string | null;
  key: string;
  operator: TraceAttributeOperator;
  /** Absent for `EXISTS`, which asks only whether the key is present at all. */
  value: string | null;
}

/** One span that satisfied a condition, and what it held. */
export interface TraceAttributeHit {
  spanId: string;
  key: string;
  value: string;
}

export interface TraceAttributeMatch {
  trace: TraceRow;
  /** Capped server-side: a trace where four hundred spans matched is answered by the first few. */
  hits: TraceAttributeHit[];
}

export interface TraceAttributeStats {
  traces: number;
  tracesWithErrors: number;
  totalNanos: number;
  p50Nanos: number;
  p95Nanos: number;
  maxNanos: number;
}

export interface TraceAttributeSearchResult {
  matches: TraceAttributeMatch[];
  totalMatching: number;
  /** The whole match summarised — the capped page above cannot be summed into it. */
  stats: TraceAttributeStats;
}

export interface TraceAttributeTimelineBucket {
  fromMillisFromBeginning: number;
  matched: number;
  total: number;
}

export interface TraceAttributeValueRow {
  value: string;
  traceCount: number;
  totalNanos: number;
  p50Nanos: number;
  p95Nanos: number;
  maxNanos: number;
  errorTraces: number;
}

export interface TraceAttributeValues {
  values: TraceAttributeValueRow[];
  /**
   * Traces carrying the key on no span at all. Shown rather than dropped: a key missing from a fifth
   * of the profile is a fact about the instrumentation, and hiding it makes every share a lie.
   */
  tracesWithoutKey: number;
  distinctValues: number;
  truncated: boolean;
}

export interface TraceAttributeLatencyCell {
  value: string;
  /**
   * A half-decade of nanoseconds: the cell covers `[10^(bucket/2), 10^((bucket+1)/2))` ns. Rendered
   * as a duration range by {@link bucketLowerNanos}.
   */
  bucket: number;
  traceCount: number;
}

export interface TraceAttributeLatency {
  cells: TraceAttributeLatencyCell[];
  minBucket: number;
  maxBucket: number;
}

/**
 * One row of the picker's first step: an event type that produced spans.
 *
 * `breakableCount` is the subset of `attributeCount` narrow enough to break down — both travel,
 * because a row promising twelve attributes that opens onto three usable ones is a row that lied.
 */
export interface TraceSpanTypeRow {
  eventType: string;
  spanCount: number;
  traceCount: number;
  errorSpans: number;
  attributeCount: number;
  breakableCount: number;
}

/** The nanosecond lower bound of a latency bucket — see {@link TraceAttributeLatencyCell.bucket}. */
export function bucketLowerNanos(bucket: number): number {
  return Math.pow(10, bucket / 2);
}

/**
 * What separates the fields of one condition in the query string.
 *
 * A tilde rather than a colon: the values being matched are routinely URIs and SQL, both full of
 * colons. Kept in step with `TraceAttributesController.CONDITION_SEPARATOR`.
 */
const CONDITION_SEPARATOR = '~';

/** One condition as the server reads it: `source~owner~key~operator~value`. */
export function encodeCondition(condition: TraceAttributeConditionModel): string {
  const fields = [
    condition.source,
    condition.owner ?? '',
    condition.key,
    condition.operator,
    condition.value ?? ''
  ];
  // EXISTS carries no value, and a trailing separator with nothing after it would arrive as an
  // empty string rather than as an absent one.
  return condition.operator === 'EXISTS'
    ? fields.slice(0, 4).join(CONDITION_SEPARATOR)
    : fields.join(CONDITION_SEPARATOR);
}

/**
 * The reverse, for restoring a filter from a link.
 *
 * Returns null for anything malformed rather than a half-built condition: a filter quietly narrowed
 * to fewer conditions than the link asked for returns more traces than it should, and looks correct.
 */
export function decodeCondition(encoded: string): TraceAttributeConditionModel | null {
  const fields = encoded.split(CONDITION_SEPARATOR);
  if (fields.length < 4) {
    return null;
  }

  const [source, owner, key, operator] = fields;
  if (!key || !source || !operator) {
    return null;
  }

  return {
    source: source as TraceAttributeSource,
    owner: owner === '' ? null : owner,
    key,
    operator: operator as TraceAttributeOperator,
    // Everything after the fourth separator belongs to the value, which may contain more of them.
    value: fields.length > 4 ? fields.slice(4).join(CONDITION_SEPARATOR) : null
  };
}

/** How a key is shown when its name alone is ambiguous — an event field is owned by its event type. */
export function keyLabel(key: TraceAttributeKeyId): string {
  return key.owner ? `${key.owner} · ${key.key}` : key.key;
}

/** Whether two key identities are the same key. */
export function sameKey(a: TraceAttributeKeyId, b: TraceAttributeKeyId): boolean {
  return a.source === b.source && a.owner === b.owner && a.key === b.key;
}

/**
 * A key as one comparable string — a `v-for` key, and what a watcher compares so that an unrelated
 * change to the query does not read as a change of key.
 */
export function keyToken(key: TraceAttributeKeyId): string {
  return `${key.source}~${key.owner ?? ''}~${key.key}`;
}

/**
 * The shape of a route's query as far as these helpers care: a value the router may have parsed
 * into an array when a name repeats. Declared here rather than imported so the models stay free of
 * the router.
 */
type QueryLike = Record<string, string | null | undefined | Array<string | null>>;

function firstValue(raw: QueryLike[string]): string | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return value === null || value === undefined ? undefined : value;
}

/**
 * The key the URL is pointing at, or null where it points at none.
 *
 * All three parts travel because all three identify the key: `rows` on a JDBC query and `rows` on
 * anything else are two keys that happen to share a name. Every attribute page reads the same
 * triple, which is what lets a key survive a move between them.
 */
export function keyFromQuery(query: QueryLike): TraceAttributeKeyId | null {
  const key = firstValue(query.key);
  if (key === undefined) {
    return null;
  }
  return {
    source: (firstValue(query.source) as TraceAttributeSource | undefined) ?? 'ATTRIBUTE',
    owner: firstValue(query.owner) ?? null,
    key
  };
}

/**
 * The event type the URL is reading a key under, or null before one is chosen.
 *
 * Separate from the key's own identity: a key is identified by source, owner and name, and the
 * event type says which of the types carrying it is on screen. Two spans' worth of `tenant` are the
 * same key read under different types, not two keys.
 */
export function eventTypeFromQuery(query: QueryLike): string | null {
  return firstValue(query.eventType) ?? null;
}
