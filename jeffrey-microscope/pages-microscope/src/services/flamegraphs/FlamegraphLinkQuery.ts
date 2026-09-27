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

import type { LocationQuery, LocationQueryValue } from 'vue-router';
import TimeRange from '@/services/api/model/TimeRange';
import type { RecordingWindow } from '@/stores/profileStore';
import linkParams from '@/router/link-params.json';

/**
 * The query parameters that open the flamegraph view on the graph an answer described: a window of
 * the recording and a search. The MCP tools write them into their links, so the page a reader opens
 * is the graph the agent read rather than the whole recording, unsearched. The names come from the
 * link contract (link-params.json) the server's tests hold every MCP link to.
 */
const FLAMEGRAPH_VIEW_PARAMS = linkParams['flamegraph-view'];
export const START_EPOCH_MS_QUERY_PARAM = FLAMEGRAPH_VIEW_PARAMS.START_EPOCH_MS;
export const END_EPOCH_MS_QUERY_PARAM = FLAMEGRAPH_VIEW_PARAMS.END_EPOCH_MS;
export const SEARCH_QUERY_PARAM = FLAMEGRAPH_VIEW_PARAMS.SEARCH;

const WHOLE_MILLIS = /^-?\d+$/;

type QueryValue = LocationQueryValue | LocationQueryValue[] | undefined;

/** What a link asks a flamegraph to open on; each part is null when the link does not ask for it. */
export interface LinkedGraphState {
  /** The window, as the offsets from the recording start that every flamegraph request takes. */
  timeRange: TimeRange | null;
  /** The expression to search for, as the view's own search box would pass it. */
  search: string | null;
}

/**
 * Reads the window and the search a link names.
 * <p>
 * The window arrives as UTC epoch milliseconds, the base every MCP answer uses, and leaves as the
 * relative range the flamegraph request and the timeseries brush already work in — no second time
 * base enters the UI. A missing bound is the recording's own, a bound outside the recording is
 * clamped into it, and a window that cannot be placed at all (no recording span, nothing left after
 * clamping) is dropped, which keeps the view's opening zoom as it is without a link.
 */
export function linkedGraphState(
  query: LocationQuery,
  recording: RecordingWindow | null
): LinkedGraphState {
  return {
    timeRange: timeRangeOf(query, recording),
    search: searchOf(query[SEARCH_QUERY_PARAM])
  };
}

function timeRangeOf(query: LocationQuery, recording: RecordingWindow | null): TimeRange | null {
  const start = epochMillisOf(query[START_EPOCH_MS_QUERY_PARAM]);
  const end = epochMillisOf(query[END_EPOCH_MS_QUERY_PARAM]);
  if ((start == null && end == null) || recording == null) {
    return null;
  }
  const from = clamp(start == null ? 0 : start - recording.startEpochMillis, recording);
  const to = clamp(
    end == null ? recording.durationMillis : end - recording.startEpochMillis,
    recording
  );
  if (to <= from) {
    return null;
  }
  return new TimeRange(from, to, false);
}

function clamp(offset: number, recording: RecordingWindow): number {
  return Math.min(Math.max(offset, 0), recording.durationMillis);
}

function epochMillisOf(raw: QueryValue): number | null {
  const value = firstOf(raw);
  if (value == null || !WHOLE_MILLIS.test(value.trim())) {
    return null;
  }
  return Number(value.trim());
}

function searchOf(raw: QueryValue): string | null {
  const value = firstOf(raw)?.trim();
  if (value == null || value.length === 0) {
    return null;
  }
  return value;
}

function firstOf(raw: QueryValue): string | null {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return typeof value === 'string' ? value : null;
}
