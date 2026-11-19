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

import { reactive, ref, type Ref } from 'vue';
import type {
  TraceSpanRow,
  TraceSpanRunMembers,
  TraceSpanRunRow
} from '@/services/api/model/trace/TraceModels';
import type { RunMembersState } from '@/services/trace/traceRuns';

/** How many members one "Load more" fetches — and the first expansion. */
export const RUN_MEMBERS_PAGE_SIZE = 50;

const LOAD_FAILED_MESSAGE = 'The members of this run could not be loaded.';
const REVEAL_FAILED_MESSAGE = 'The span this entry points at could not be loaded.';

/** The two calls this needs, so a test can stand in for the HTTP client. */
export interface RunMembersSource {
  getRunMembers(
    traceId: string,
    runId: string,
    offset: number,
    limit: number
  ): Promise<TraceSpanRunMembers>;
  getSpan(traceId: string, spanId: string): Promise<TraceSpanRow>;
}

export interface RunMembers {
  /** Per run id, whatever has been paged in so far. Kept across collapse and re-expand. */
  states: ReadonlyMap<string, RunMembersState>;
  /** The run ids whose members are drawn. */
  expanded: Ref<ReadonlySet<string>>;
  /** Draws the run's members, fetching the slowest page the first time only. */
  expand(run: TraceSpanRunRow): Promise<void>;
  collapse(runId: string): void;
  toggle(run: TraceSpanRunRow): Promise<void>;
  /** The next page, slowest first after the ones already loaded; also the retry after a failure. */
  loadMore(run: TraceSpanRunRow): Promise<void>;
  /**
   * Expands the run and makes sure one member is among the loaded ones, fetching it by id when the
   * pages have not reached it. Resolves to that member, or null when it could not be loaded.
   */
  reveal(run: TraceSpanRunRow, spanId: string): Promise<TraceSpanRow | null>;
  /** Forgets every run, for a different trace. Answers still in flight are dropped when they land. */
  reset(): void;
}

/**
 * The members of a trace's folded runs, paged in on demand.
 *
 * Deliberately outside the spans the waterfall was handed: the waterfall resets its fold and
 * filter state whenever those change identity, which is how it tells a new trace from the old one,
 * and a page of members arriving must never look like that.
 */
export function useRunMembers(source: () => RunMembersSource, traceId: () => string): RunMembers {
  const states = reactive(new Map<string, RunMembersState>());
  const expanded = ref<ReadonlySet<string>>(new Set());
  /*
   * Bumped by reset. A request remembers the generation it was made in and drops its answer if the
   * reader has since moved to another trace: a slow page from the last trace landing in this one
   * would graft another trace's spans under a run that happens to share an id.
   */
  let generation = 0;

  function stateOf(runId: string): RunMembersState {
    const existing = states.get(runId);
    if (existing !== undefined) {
      return existing;
    }
    states.set(runId, {
      members: [],
      fetched: 0,
      pinnedIds: [],
      total: 0,
      hasMore: true,
      loading: false,
      error: null
    });
    // Read back through the map, so the caller mutates the reactive proxy rather than the raw object.
    return states.get(runId)!;
  }

  async function loadMore(run: TraceSpanRunRow): Promise<void> {
    const state = stateOf(run.runId);
    if (state.loading || (!state.hasMore && state.error === null)) {
      return;
    }
    const requestedIn = generation;
    state.loading = true;
    state.error = null;
    try {
      const page = await source().getRunMembers(
        traceId(),
        run.runId,
        state.fetched,
        RUN_MEMBERS_PAGE_SIZE
      );
      if (requestedIn !== generation) {
        return;
      }
      // A pinned member reappears in whichever page reaches its rank; it stays where it was pinned.
      const loaded = new Set(state.members.map(member => member.spanId));
      state.members = [
        ...state.members,
        ...page.members.filter(member => !loaded.has(member.spanId))
      ];
      state.fetched += page.members.length;
      state.total = page.total;
      state.hasMore = page.hasMore;
    } catch {
      if (requestedIn === generation) {
        state.error = LOAD_FAILED_MESSAGE;
      }
    } finally {
      if (requestedIn === generation) {
        state.loading = false;
      }
    }
  }

  async function expand(run: TraceSpanRunRow): Promise<void> {
    expanded.value = new Set(expanded.value).add(run.runId);
    if (states.has(run.runId)) {
      return;
    }
    await loadMore(run);
  }

  function collapse(runId: string): void {
    const next = new Set(expanded.value);
    next.delete(runId);
    expanded.value = next;
  }

  async function toggle(run: TraceSpanRunRow): Promise<void> {
    if (expanded.value.has(run.runId)) {
      collapse(run.runId);
      return;
    }
    await expand(run);
  }

  async function reveal(run: TraceSpanRunRow, spanId: string): Promise<TraceSpanRow | null> {
    const requestedIn = generation;
    await expand(run);
    if (requestedIn !== generation) {
      return null;
    }
    const state = stateOf(run.runId);
    const loaded = state.members.find(member => member.spanId === spanId);
    if (loaded !== undefined) {
      return loaded;
    }
    try {
      const span = await source().getSpan(traceId(), spanId);
      if (requestedIn !== generation) {
        return null;
      }
      // Checked again: the first page may have landed while this request was out.
      const raced = state.members.find(member => member.spanId === spanId);
      if (raced !== undefined) {
        return raced;
      }
      state.members = [span, ...state.members];
      state.pinnedIds = [...state.pinnedIds, span.spanId];
      return span;
    } catch {
      if (requestedIn === generation) {
        state.error = REVEAL_FAILED_MESSAGE;
      }
      return null;
    }
  }

  function reset(): void {
    generation++;
    states.clear();
    expanded.value = new Set();
  }

  return { states, expanded, expand, collapse, toggle, loadMore, reveal, reset };
}
