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

import { describe, expect, it } from 'vitest';
import { profileKindOf } from '@/services/ProfileKind';

describe('profileKindOf', () => {
  it('offers a JFR profile every recording mode', () => {
    const jfr = profileKindOf('JDK');

    for (const mode of [
      'Overview',
      'JVM',
      'Application',
      'Traces',
      'Visualization',
      'Tools'
    ] as const) {
      expect(jfr.modes.has(mode)).toBe(true);
    }
    expect(jfr.modes.has('PerfCounters')).toBe(false);
    // Heap dumps are profiles of their own; a recording never offers one.
    expect(jfr.modes.has('HeapDump')).toBe(false);
    expect(profileKindOf('ASYNC_PROFILER')).toBe(jfr);
  });

  it('gives a heap dump and a perf-counters snapshot one mode each', () => {
    expect([...profileKindOf('HEAP_DUMP').modes]).toEqual(['HeapDump']);
    expect([...profileKindOf('PERF_COUNTERS').modes]).toEqual(['PerfCounters']);
  });

  it('keeps Tools for OpenTelemetry, which can be re-exported, but not for pprof', () => {
    expect(profileKindOf('OPEN_TELEMETRY').modes.has('Tools')).toBe(true);
    expect(profileKindOf('PPROF').modes.has('Tools')).toBe(false);
  });

  it('lands each kind inside one of its own modes', () => {
    expect(profileKindOf('PERF_COUNTERS').landing).toBe('perf-counters/overview');
    expect(profileKindOf('HEAP_DUMP').landing).toBe('heap-dump/overview');
    expect(profileKindOf('PPROF').landing).toBe('flamegraphs/primary');
  });

  // A profile is opened before its event source is known; until then it is treated as JFR.
  it('reads an unknown or missing source as JFR', () => {
    expect(profileKindOf(undefined)).toBe(profileKindOf('JDK'));
    expect(profileKindOf('SOMETHING_NEW')).toBe(profileKindOf('JDK'));
  });
});
