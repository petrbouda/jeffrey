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

import type { ProfileMode } from '@/views/profiles/navigation/profileNavConfig';

/**
 * What each kind of profile is in its Profile Detail: the mode pills it offers and the page it opens
 * at. One table instead of an `isHeapDumpOnly` / `isPprofOnly` check per pill.
 *
 * The kind is read from the profile's event source. A source this table does not name is a JFR
 * recording, which is right for the common case and never hides a page a JFR profile has.
 */
export interface ProfileKind {
  /** The mode pills the profile offers. */
  modes: ReadonlySet<ProfileMode>;
  /** The sub-path under `/profiles/{profileId}` the profile opens at. */
  landing: string;
}

const JFR: ProfileKind = {
  modes: new Set<ProfileMode>([
    'Overview',
    'JVM',
    'Application',
    'Traces',
    'Visualization',
    'Technologies',
    'Tools'
  ]),
  landing: 'dashboard'
};

/** Stack samples only: none of the GC, safepoint or thread data the Summary dashboard is built from. */
const PPROF: ProfileKind = {
  modes: new Set<ProfileMode>(['Visualization']),
  landing: 'flamegraphs/primary'
};

/** Stack samples like pprof, but re-exportable, so it keeps Tools. */
const OPEN_TELEMETRY: ProfileKind = {
  modes: new Set<ProfileMode>(['Visualization', 'Tools']),
  landing: 'flamegraphs/primary'
};

const HEAP_DUMP: ProfileKind = {
  modes: new Set<ProfileMode>(['HeapDump']),
  landing: 'heap-dump/overview'
};

const PERF_COUNTERS: ProfileKind = {
  modes: new Set<ProfileMode>(['PerfCounters']),
  landing: 'perf-counters/overview'
};

const KINDS_BY_SOURCE: ReadonlyMap<string, ProfileKind> = new Map([
  ['PPROF', PPROF],
  ['OPEN_TELEMETRY', OPEN_TELEMETRY],
  ['HEAP_DUMP', HEAP_DUMP],
  ['PERF_COUNTERS', PERF_COUNTERS]
]);

/** The kind of a profile, from its event source; anything not listed — or not known yet — is JFR. */
export function profileKindOf(eventSource?: string | null): ProfileKind {
  return (eventSource ? KINDS_BY_SOURCE.get(eventSource) : undefined) ?? JFR;
}
