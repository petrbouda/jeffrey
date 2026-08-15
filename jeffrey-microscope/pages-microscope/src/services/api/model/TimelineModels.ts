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

import type { TracePause } from '@/services/api/model/trace/TraceModels';

/**
 * One span as the unified timeline draws it. `depth` is lane depth on its thread, not depth in its
 * trace: a thread that served two unrelated requests shows both at depth 0.
 */
export interface TimelineSpan {
  traceId: string;
  spanId: string;
  name: string;
  kind: string;
  status: string;
  startEpochMicros: number;
  durationNanos: number;
  depth: number;
}

/**
 * One stretch during which a track's thread was waiting rather than running — the underlay that
 * turns a blank gap into a finding. The category names are the trace views' context vocabulary,
 * so a park is the same colour here, on the waterfall and in the why-slow panel.
 */
export interface TimelineStatePeriod {
  category: string;
  startEpochMicros: number;
  durationNanos: number;
}

/** How many spans started in one slice of a capped window — the honest zoomed-out shape. */
export interface TimelineDensityBucket {
  bucketIndex: number;
  count: number;
}

/** One thread's row, with the spans that ran on it inside the window. */
export interface TimelineTrack {
  threadHash: string;
  threadName: string | null;
  isVirtual: boolean;
  /** How many depth lanes the track needs, so the row can be sized before it is drawn. */
  laneCount: number;
  spans: TimelineSpan[];
  states: TimelineStatePeriod[];
  /** Non-empty exactly when the window was capped; then `spans` is empty. */
  density: TimelineDensityBucket[];
}

/**
 * Everything drawn for one viewport. Fetched per viewport rather than per recording — a capture
 * holds far more spans than a screen can show, which is what makes pan and zoom affordable.
 */
export interface TimelineWindow {
  fromEpochMicros: number;
  toEpochMicros: number;
  pauses: TracePause[];
  tracks: TimelineTrack[];
  /** Whether the span cap was hit. Surfaced, never silent. */
  truncated: boolean;
  /** Whether the thread-state cap was hit — separate, since the spans being complete says nothing. */
  statesTruncated: boolean;
  /** How many slices the density view cut the window into; 0 outside density mode. */
  densityBuckets: number;
}
