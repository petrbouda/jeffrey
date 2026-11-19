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

import type { LocationQueryValue } from 'vue-router';
import GraphType from '@/services/flamegraphs/GraphType';

type QueryValue = LocationQueryValue | LocationQueryValue[] | undefined;

/**
 * What the flamegraph view draws when it opens: one profile's graph, the differential graph against
 * a baseline, or - a differential graph with no baseline to subtract - the missing-baseline state
 * instead of a request for profile "null".
 */
export type FlamegraphViewOpening =
  | { kind: 'PRIMARY' }
  | { kind: 'DIFFERENTIAL'; baselineId: string }
  | { kind: 'MISSING_BASELINE' };

/**
 * Decides the opening from the link's graphMode and the baseline adopted for this profile. Only
 * graphMode DIFFERENTIAL asks for a baseline; a link that names no mode, or one the view does not
 * know, opens the primary graph rather than reporting a baseline it never asked for.
 */
export function flamegraphViewOpening(
  graphMode: QueryValue,
  baselineId: string | null
): FlamegraphViewOpening {
  const mode = Array.isArray(graphMode) ? graphMode[0] : graphMode;
  if (mode !== GraphType.DIFFERENTIAL) {
    return { kind: 'PRIMARY' };
  }
  if (baselineId == null) {
    return { kind: 'MISSING_BASELINE' };
  }
  return { kind: 'DIFFERENTIAL', baselineId };
}
