/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import type { TraceSpanRow, TraceSpanRunRow } from '@/services/api/model/trace/TraceModels';

/*
 * ---------------------------------------------------------------------------------------------
 * The waterfall's rows, before anything is drawn.
 *
 * A trace arrives as two lists: the spans the server sent one by one, and the runs it folded
 * because there were too many siblings to send. The waterfall needs one list, in tree order, so
 * that folding, filtering and the client's own rollup can each walk it once. That list is made of
 * entries -- a span, a server run, or the "load more" row under an expanded run -- and every entry
 * carries the few things the tree helpers read, so none of them has to ask which kind it holds.
 *
 * Members of an expanded run are entries too, but they never join the trace's own spans: they are
 * paged in on demand and live in their own store, so loading a page cannot look like a new trace.
 */

/** The state of one server run's members, as far as they have been paged in. */
export interface RunMembersState {
  /** Pinned members first, then the pages in the order they arrived: slowest first. */
  members: TraceSpanRow[];
  /** How many members paging has returned — the next page's offset. Pinned ones are not counted. */
  fetched: number;
  /**
   * Members revealed one by one because a rail entry pointed at them. Their rank among the run is
   * unknown — they were fetched by id, not by order — so they are drawn first and unranked.
   */
  pinnedIds: string[];
  total: number;
  hasMore: boolean;
  loading: boolean;
  error: string | null;
}

/** What every entry carries for the tree helpers, so none of them has to know what it holds. */
interface EntryBase {
  /** Unique across the list. A span's own id; runs and their "more" rows get prefixed keys. */
  key: string;
  /** The depth the row is drawn at. */
  depth: number;
  /** The key of the entry this one hangs under, or null for a root. */
  parentKey: string | null;
  /**
   * Whether hiding the parent hides this entry too. True for anything the derivation hung where it
   * is — a synthesized span, a member under its run — and false for a recorded span, which a hidden
   * traced method had only adopted and which must resurface when the method is switched off.
   */
  followsParent: boolean;
  /** How many spans the entry stands for: one for a span, every member for a run. */
  weight: number;
}

export interface SpanEntry extends EntryBase {
  kind: 'span';
  span: TraceSpanRow;
  /** The run this span was paged in from, or null for a span the trace itself carried. */
  memberOf: string | null;
  /** 1-based place among the run's members, slowest first; null for a pinned or unfolded span. */
  rank: number | null;
}

export interface RunEntry extends EntryBase {
  kind: 'run';
  run: TraceSpanRunRow;
}

/** The row closing an expanded run: how much of it is showing, and the way to the next page. */
export interface MoreEntry extends EntryBase {
  kind: 'more';
  run: TraceSpanRunRow;
  /** Null only before the first request is made, which reads the same as one in flight. */
  state: RunMembersState | null;
}

export type WaterfallEntry = SpanEntry | RunEntry | MoreEntry;

/** Prefixed, because a run's id is its lowest member's span id, which a loaded member also carries. */
export function runKey(runId: string): string {
  return `run:${runId}`;
}

function moreKey(runId: string): string {
  return `more:${runId}`;
}

export function spanEntry(span: TraceSpanRow): SpanEntry {
  return {
    kind: 'span',
    key: span.spanId,
    depth: span.depth,
    parentKey: span.parentSpanId,
    followsParent: span.synthesized,
    weight: 1,
    span,
    memberOf: null,
    rank: null
  };
}

function runEntry(run: TraceSpanRunRow): RunEntry {
  return {
    kind: 'run',
    key: runKey(run.runId),
    depth: run.depth,
    parentKey: run.parentSpanId,
    followsParent: run.synthesized,
    weight: run.durations.count,
    run
  };
}

/*
 * A member is drawn one level under its run rather than at its own depth: at its own depth it would
 * read as a sibling of the run row that holds it.
 */
function memberEntry(run: TraceSpanRunRow, span: TraceSpanRow, rank: number | null): SpanEntry {
  return {
    kind: 'span',
    key: span.spanId,
    depth: run.depth + 1,
    parentKey: runKey(run.runId),
    followsParent: true,
    weight: 1,
    span,
    memberOf: run.runId,
    rank
  };
}

function moreEntry(run: TraceSpanRunRow, state: RunMembersState | null): MoreEntry {
  return {
    kind: 'more',
    key: moreKey(run.runId),
    depth: run.depth + 1,
    parentKey: runKey(run.runId),
    followsParent: true,
    weight: 0,
    run,
    state
  };
}

/** Whether an expanded run still owes the reader a row: more to load, a page loading, or a failure. */
function needsMoreRow(state: RunMembersState | null): boolean {
  return state === null || state.hasMore || state.loading || state.error !== null;
}

/**
 * The spans and runs as one list in tree order, with each expanded run's loaded members after it.
 *
 * The server already decided where each run goes (`position`: draw it before that span), so this
 * only has to honour it — a run cannot be placed by start time here, because its members' starts
 * are exactly what the browser does not have.
 */
export function mergeEntries(
  spans: readonly TraceSpanRow[],
  runs: readonly TraceSpanRunRow[],
  expandedRunIds: ReadonlySet<string> = new Set(),
  membersByRun: ReadonlyMap<string, RunMembersState> = new Map()
): WaterfallEntry[] {
  // Stable, so runs sharing a position keep the order the server sent them in.
  const ordered = [...runs].sort((left, right) => left.position - right.position);
  const entries: WaterfallEntry[] = [];
  let next = 0;

  const emitRunsBefore = (position: number) => {
    while (next < ordered.length && ordered[next].position <= position) {
      emitRun(ordered[next], expandedRunIds, membersByRun, entries);
      next++;
    }
  };

  for (let index = 0; index < spans.length; index++) {
    emitRunsBefore(index);
    entries.push(spanEntry(spans[index]));
  }
  // Whatever is left goes after the last span — `position === spans.length`, or a stale one past it.
  emitRunsBefore(Number.POSITIVE_INFINITY);
  return entries;
}

function emitRun(
  run: TraceSpanRunRow,
  expandedRunIds: ReadonlySet<string>,
  membersByRun: ReadonlyMap<string, RunMembersState>,
  entries: WaterfallEntry[]
): void {
  entries.push(runEntry(run));
  if (!expandedRunIds.has(run.runId)) {
    return;
  }
  const state = membersByRun.get(run.runId) ?? null;
  const pinned = new Set(state?.pinnedIds ?? []);
  let rank = 0;
  for (const member of state?.members ?? []) {
    const isPinned = pinned.has(member.spanId);
    if (!isPinned) {
      rank++;
    }
    entries.push(memberEntry(run, member, isPinned ? null : rank));
  }
  if (needsMoreRow(state)) {
    entries.push(moreEntry(run, state));
  }
}

/** Whether an entry is on the critical path. A "more" row goes wherever its run goes. */
export function isCriticalEntry(entry: WaterfallEntry): boolean {
  if (entry.kind === 'span') {
    return entry.span.criticalPathNanos > 0;
  }
  return entry.run.criticalPathNanos > 0;
}

/*
 * ---------------------------------------------------------------------------------------------
 * Run facts: one shape for the statistics strip, whichever side summed the run.
 */

/** Buckets in a client run's histogram — enough to show a shape, few enough to stay a glyph. */
export const CLIENT_RUN_HISTOGRAM_BUCKETS = 12;

export interface RunHistogramBucket {
  /** Drawn height, 0..1 against the busiest bucket. */
  height: number;
  fromNanos: number;
  toNanos: number;
  count: number;
}

/** What the statistics strip says about a run, from the spans in hand or from the server. */
export interface RunFacts {
  count: number;
  totalNanos: number;
  minNanos: number;
  medianNanos: number;
  p95Nanos: number;
  p99Nanos: number;
  maxNanos: number;
  /** The run's summed share of the critical path; null where the strip does not report it. */
  criticalPathNanos: number | null;
  histogram: RunHistogramBucket[];
  /** Whether the bars' heights are log-scaled, which the strip has to say under them. */
  logScale: boolean;
}

/**
 * The facts of a run the browser rolled up itself. Nearest-rank percentiles, the same formulas the
 * server uses for its own runs, so a client run and a server run of the same spans read the same.
 */
export function clientRunFacts(spans: readonly TraceSpanRow[]): RunFacts {
  const durations = spans.map(span => span.durationNanos).sort((left, right) => left - right);
  const at = (share: number) =>
    durations[Math.min(durations.length - 1, Math.floor(durations.length * share))];
  return {
    count: durations.length,
    totalNanos: durations.reduce((sum, nanos) => sum + nanos, 0),
    minNanos: durations[0],
    medianNanos: durations[Math.floor(durations.length / 2)],
    p95Nanos: at(0.95),
    p99Nanos: at(0.99),
    maxNanos: durations[durations.length - 1],
    criticalPathNanos: null,
    histogram: linearHistogram(durations),
    logScale: false
  };
}

/**
 * The run's durations bucketed min-to-max, each height normalized to the busiest bucket. The shape
 * answers what median and max cannot: were the slow writes a tail, a cluster, or a second mode?
 */
function linearHistogram(sortedDurations: readonly number[]): RunHistogramBucket[] {
  const counts = new Array(CLIENT_RUN_HISTOGRAM_BUCKETS).fill(0) as number[];
  const min = sortedDurations[0];
  const max = sortedDurations[sortedDurations.length - 1];
  const range = Math.max(1, max - min);
  for (const nanos of sortedDurations) {
    const bucket = Math.min(
      CLIENT_RUN_HISTOGRAM_BUCKETS - 1,
      Math.floor(((nanos - min) / range) * CLIENT_RUN_HISTOGRAM_BUCKETS)
    );
    counts[bucket]++;
  }
  const peak = Math.max(...counts, 1);
  const bucketWidth = range / CLIENT_RUN_HISTOGRAM_BUCKETS;
  return counts.map((count, index) => ({
    height: count / peak,
    fromNanos: min + index * bucketWidth,
    toNanos: min + (index + 1) * bucketWidth,
    count
  }));
}

/*
 * The least a non-empty log-scaled bucket is drawn at. log(1 + 1) against log(1 + 900,000) is under
 * a twentieth, and a bar that short reads as an empty bucket — the one thing a single slow outlier
 * must never look like.
 */
const MIN_LOG_HEIGHT = 0.06;

/** A bucket's drawn height on a log scale, 0..1; exactly 1 for the busiest, so it reads as the mode. */
function logHeight(count: number, peak: number): number {
  if (count <= 0) {
    return 0;
  }
  if (count >= peak) {
    return 1;
  }
  return Math.max(MIN_LOG_HEIGHT, Math.log10(1 + count) / Math.log10(1 + peak));
}

/**
 * The facts of a run the server folded, read straight off what it summed. The histogram's heights
 * are log-scaled: on a linear scale all but a handful of a million fast writes land in one bar, and
 * the tail that made the run worth opening disappears.
 */
export function serverRunFacts(run: TraceSpanRunRow): RunFacts {
  const durations = run.durations;
  const peak = Math.max(1, ...durations.buckets.map(bucket => bucket.count));
  return {
    count: durations.count,
    totalNanos: durations.totalNanos,
    minNanos: durations.minNanos,
    medianNanos: durations.p50Nanos,
    p95Nanos: durations.p95Nanos,
    p99Nanos: durations.p99Nanos,
    maxNanos: durations.maxNanos,
    criticalPathNanos: run.criticalPathNanos,
    histogram: durations.buckets.map(bucket => ({
      height: logHeight(bucket.count, peak),
      fromNanos: bucket.fromNanos,
      toNanos: bucket.toNanos,
      count: bucket.count
    })),
    logScale: true
  };
}

/*
 * ---------------------------------------------------------------------------------------------
 * The client's own rollup.
 *
 * Consecutive same-named leaf siblings the server sent one by one still fold into a counted row in
 * the browser — a 2 GB upload written through an 8 MB buffer is a few hundred writes, under the
 * server's threshold and still not worth a row each. It applies only to spans the trace carried:
 * a server run's members are already a run, and rolling them up again would hide the very rows the
 * reader just paged in.
 */

/** Consecutive same-named leaf siblings, drawn as one rollup row until expanded. */
export interface ClientRun {
  key: string;
  entries: SpanEntry[];
  facts: RunFacts;
}

/** One drawn row. */
export type DisplayRow =
  | { kind: 'span'; key: string; entry: SpanEntry }
  | { kind: 'clientRun'; key: string; run: ClientRun }
  | { kind: 'serverRun'; key: string; entry: RunEntry }
  | { kind: 'more'; key: string; entry: MoreEntry };

/** Whether two rows belong to one client run. Errors never join one — a rollup must not eat one. */
function sameClientRun(left: TraceSpanRow, right: TraceSpanRow): boolean {
  return (
    left.parentSpanId === right.parentSpanId &&
    left.name === right.name &&
    left.eventType === right.eventType &&
    left.status !== 'ERROR' &&
    right.status !== 'ERROR'
  );
}

/** A span the client may roll up: one the trace carried, with nothing hanging off it. */
function isRollupCandidate(
  entry: WaterfallEntry,
  parents: ReadonlySet<string>
): entry is SpanEntry {
  return entry.kind === 'span' && entry.memberOf === null && !parents.has(entry.key);
}

function clientRun(entries: SpanEntry[]): ClientRun {
  const first = entries[0].span;
  return {
    // The first span's id keeps the key stable however often the surrounding filters recompute.
    key: `${first.parentSpanId ?? ''}|${first.name}|${first.eventType}|${first.spanId}`,
    entries,
    facts: clientRunFacts(entries.map(entry => entry.span))
  };
}

function toDisplayRow(entry: WaterfallEntry): DisplayRow {
  if (entry.kind === 'span') {
    return { kind: 'span', key: entry.key, entry };
  }
  if (entry.kind === 'run') {
    return { kind: 'serverRun', key: entry.key, entry };
  }
  return { kind: 'more', key: entry.key, entry };
}

/**
 * The rows as drawn: runs of `minRunLength`+ identical leaves the trace carried fold into one
 * rollup row, everything else passes through one entry per row. Only leaves are grouped — merging a
 * parent would hide the structure beneath it, which is the opposite of what the rollup is for.
 */
export function rollupRows(
  entries: readonly WaterfallEntry[],
  parents: ReadonlySet<string>,
  expandedClientRuns: ReadonlySet<string>,
  minRunLength: number
): DisplayRow[] {
  const rows: DisplayRow[] = [];
  let index = 0;
  while (index < entries.length) {
    const start = entries[index];
    if (!isRollupCandidate(start, parents)) {
      rows.push(toDisplayRow(start));
      index++;
      continue;
    }

    const group: SpanEntry[] = [start];
    while (index + group.length < entries.length) {
      const candidate = entries[index + group.length];
      if (!isRollupCandidate(candidate, parents) || !sameClientRun(start.span, candidate.span)) {
        break;
      }
      group.push(candidate);
    }

    if (group.length >= minRunLength) {
      const run = clientRun(group);
      rows.push({ kind: 'clientRun', key: run.key, run });
      if (expandedClientRuns.has(run.key)) {
        for (const entry of group) {
          rows.push(toDisplayRow(entry));
        }
      }
    } else {
      for (const entry of group) {
        rows.push(toDisplayRow(entry));
      }
    }
    index += group.length;
  }
  return rows;
}
