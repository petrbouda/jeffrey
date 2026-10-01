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

import { describe, expect, it, vi } from 'vitest';
import {
  RUN_MEMBERS_PAGE_SIZE,
  useRunMembers,
  type RunMembersSource
} from '@/composables/useRunMembers';
import type {
  TraceSpanRow,
  TraceSpanRunMembers,
  TraceSpanRunRow
} from '@/services/api/model/trace/TraceModels';

/** Two and a half pages, so three loads reach the end and the last one comes back short. */
const TOTAL = RUN_MEMBERS_PAGE_SIZE * 2 + RUN_MEMBERS_PAGE_SIZE / 2;
/** A member near the end of the run, past the first page. */
const DEEP_MEMBER = `m${TOTAL - 5}`;

function member(index: number): TraceSpanRow {
  return {
    spanId: `m${index}`,
    parentSpanId: 'root',
    name: 'File write',
    kind: 'INTERNAL',
    status: 'OK',
    errorType: null,
    startMillisFromBeginning: 0,
    startEpochMicros: 0,
    // Slowest first, the order the server pages them in.
    durationNanos: TOTAL - index,
    selfDurationNanos: TOTAL - index,
    criticalPathNanos: 0,
    depth: 1,
    threadHash: 't1',
    threadName: 'main',
    isVirtual: false,
    eventType: 'jdk.FileWrite',
    attributes: null,
    eventFields: null,
    synthesized: true,
    ioOrigin: null
  };
}

const RUN: TraceSpanRunRow = {
  runId: 'm0',
  parentSpanId: 'root',
  position: 1,
  depth: 1,
  name: 'File write',
  kind: 'INTERNAL',
  eventType: 'jdk.FileWrite',
  ioOrigin: null,
  synthesized: true,
  threadHash: 't1',
  threadName: 'main',
  threadCount: 1,
  durations: {
    count: TOTAL,
    totalNanos: 0,
    minNanos: 0,
    p50Nanos: 0,
    p95Nanos: 0,
    p99Nanos: 0,
    maxNanos: 0,
    buckets: []
  },
  criticalPathNanos: 0,
  firstStartEpochMicros: 0,
  lastEndEpochMicros: 0,
  coverage: [],
  entrySpanIds: [DEEP_MEMBER]
};

/** A server holding {@link TOTAL} members, paging them as the members endpoint does. */
function fakeSource(): RunMembersSource & {
  getRunMembers: ReturnType<typeof vi.fn>;
  getSpan: ReturnType<typeof vi.fn>;
} {
  const all = Array.from({ length: TOTAL }, (_, index) => member(index));
  return {
    getRunMembers: vi.fn(
      async (_traceId: string, _runId: string, offset: number, limit: number) =>
        ({
          members: all.slice(offset, offset + limit),
          total: TOTAL,
          hasMore: offset + limit < TOTAL
        }) satisfies TraceSpanRunMembers
    ),
    getSpan: vi.fn(async (_traceId: string, spanId: string) => {
      const found = all.find(candidate => candidate.spanId === spanId);
      if (!found) {
        throw new Error('404');
      }
      return found;
    })
  };
}

function setUp() {
  const source = fakeSource();
  const runMembers = useRunMembers(
    () => source,
    () => 'trace-1'
  );
  return { source, runMembers };
}

describe('useRunMembers', () => {
  it('fetches the slowest page on the first expansion', async () => {
    const { source, runMembers } = setUp();

    await runMembers.expand(RUN);

    expect(source.getRunMembers).toHaveBeenCalledWith('trace-1', 'm0', 0, RUN_MEMBERS_PAGE_SIZE);
    const state = runMembers.states.get('m0')!;
    expect(state.members).toHaveLength(RUN_MEMBERS_PAGE_SIZE);
    expect(state.members[0].spanId).toBe('m0');
    expect(state.total).toBe(TOTAL);
    expect(state.hasMore).toBe(true);
    expect(runMembers.expanded.value.has('m0')).toBe(true);
  });

  it('pages on from where the last page ended, until there is no more', async () => {
    const { source, runMembers } = setUp();

    await runMembers.expand(RUN);
    await runMembers.loadMore(RUN);
    await runMembers.loadMore(RUN);

    expect(source.getRunMembers.mock.calls.map(call => call[2])).toEqual([0, RUN_MEMBERS_PAGE_SIZE, 2 * RUN_MEMBERS_PAGE_SIZE]);
    const state = runMembers.states.get('m0')!;
    expect(state.members).toHaveLength(TOTAL);
    expect(state.hasMore).toBe(false);

    await runMembers.loadMore(RUN);
    expect(source.getRunMembers).toHaveBeenCalledTimes(3);
  });

  it('keeps the loaded pages across a collapse and a re-expansion', async () => {
    const { source, runMembers } = setUp();

    await runMembers.expand(RUN);
    await runMembers.toggle(RUN);
    expect(runMembers.expanded.value.has('m0')).toBe(false);

    await runMembers.toggle(RUN);

    expect(runMembers.expanded.value.has('m0')).toBe(true);
    expect(source.getRunMembers).toHaveBeenCalledTimes(1);
    expect(runMembers.states.get('m0')!.members).toHaveLength(RUN_MEMBERS_PAGE_SIZE);
  });

  it('records a failed page and retries it from the same offset', async () => {
    const { source, runMembers } = setUp();
    source.getRunMembers.mockRejectedValueOnce(new Error('500'));

    await runMembers.expand(RUN);

    const state = runMembers.states.get('m0')!;
    expect(state.error).not.toBeNull();
    expect(state.loading).toBe(false);

    await runMembers.loadMore(RUN);

    expect(source.getRunMembers.mock.calls.map(call => call[2])).toEqual([0, 0]);
    expect(state.error).toBeNull();
    expect(state.members).toHaveLength(RUN_MEMBERS_PAGE_SIZE);
  });

  it('reveals a member the pages have not reached, pinned first', async () => {
    const { source, runMembers } = setUp();

    const revealed = await runMembers.reveal(RUN, DEEP_MEMBER);

    expect(revealed?.spanId).toBe(DEEP_MEMBER);
    expect(source.getSpan).toHaveBeenCalledWith('trace-1', DEEP_MEMBER);
    const state = runMembers.states.get('m0')!;
    expect(state.members[0].spanId).toBe(DEEP_MEMBER);
    expect(state.pinnedIds).toEqual([DEEP_MEMBER]);
  });

  it('does not fetch a member a page already brought in', async () => {
    const { source, runMembers } = setUp();

    const revealed = await runMembers.reveal(RUN, 'm5');

    expect(revealed?.spanId).toBe('m5');
    expect(source.getSpan).not.toHaveBeenCalled();
  });

  it('drops a pinned member from the page that later reaches it', async () => {
    const { runMembers } = setUp();

    await runMembers.reveal(RUN, DEEP_MEMBER);
    await runMembers.loadMore(RUN);
    await runMembers.loadMore(RUN);

    const ids = runMembers.states.get('m0')!.members.map(candidate => candidate.spanId);
    expect(ids.filter(id => id === DEEP_MEMBER)).toHaveLength(1);
    expect(ids).toHaveLength(TOTAL);
    // The paging offset counts what the server returned, not the pinned member.
    expect(runMembers.states.get('m0')!.fetched).toBe(TOTAL);
  });

  it('forgets everything on reset and drops an answer that lands afterwards', async () => {
    const { runMembers } = setUp();

    const pending = runMembers.expand(RUN);
    runMembers.reset();
    await pending;

    expect(runMembers.states.size).toBe(0);
    expect(runMembers.expanded.value.size).toBe(0);
  });
});
