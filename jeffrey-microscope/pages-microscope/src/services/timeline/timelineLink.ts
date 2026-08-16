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

import type { RouteLocationRaw } from 'vue-router';

/**
 * The one way a "Show on timeline" edge is built, wherever it appears — the trace waterfall, a slow
 * HTTP request, a JDBC statement, a gRPC call. All of them mean the same thing: the unified
 * timeline zoomed to this thing's moment, padded so the reader sees what surrounded it rather than
 * the thing wedged edge-to-edge.
 */

/** The subject's own share of window padding on each side. */
const PAD_RATIO = 0.15;
/** At least this much padding, so a sub-millisecond subject still gets a readable window. */
const MIN_PAD_MICROS = 50_000;
const MICROS_PER_MILLI = 1_000;
const NANOS_PER_MICRO = 1_000;

/** The `?from&to` window params are the timeline view's own deep-link contract, in epoch micros. */
export function timelineWindowLink(
  profileId: string,
  startEpochMillis: number,
  durationNanos: number
): RouteLocationRaw {
  const startMicros = startEpochMillis * MICROS_PER_MILLI;
  const durationMicros = Math.max(1, Math.ceil(durationNanos / NANOS_PER_MICRO));
  const pad = Math.max(Math.ceil(durationMicros * PAD_RATIO), MIN_PAD_MICROS);
  return {
    name: 'profile-technologies-traces-timeline',
    params: { profileId },
    query: {
      from: String(startMicros - pad),
      to: String(startMicros + durationMicros + pad)
    }
  };
}
