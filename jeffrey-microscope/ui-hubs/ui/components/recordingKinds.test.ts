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
import { HEAP_DUMP_INIT_CHIPS, PROFILE_INIT_CHIPS } from './profileInitChips';
import { recordingKindOf } from './recordingKinds';

describe('recordingKindOf', () => {
  it('reads every JFR event source, and a missing one, as a JFR recording', () => {
    for (const source of ['JDK', 'ASYNC_PROFILER', 'UNKNOWN', null, undefined]) {
      const kind = recordingKindOf(source);
      expect(kind.key).toBe('JFR');
      expect(kind.cardClass).toBeNull();
      expect(kind.chips).toBe(PROFILE_INIT_CHIPS);
    }
  });

  it('gives a heap dump its own tag, accent and the heap initialization chips', () => {
    const kind = recordingKindOf('HEAP_DUMP');
    expect(kind.key).toBe('HEAP');
    expect(kind.cardClass).toBe('rec-card--heap-dump');
    expect(kind.chips).toBe(HEAP_DUMP_INIT_CHIPS);
  });

  it('files pprof and OpenTelemetry imports under their own filter keys', () => {
    expect(recordingKindOf('PPROF').key).toBe('PPROF');
    expect(recordingKindOf('OPEN_TELEMETRY').key).toBe('OTLP');
    expect(recordingKindOf('PPROF').chips).toBe(PROFILE_INIT_CHIPS);
  });

  it('gives every kind a distinct filter key and badge colour', () => {
    const kinds = ['JDK', 'HEAP_DUMP', 'PPROF', 'OPEN_TELEMETRY'].map(recordingKindOf);
    expect(new Set(kinds.map(kind => kind.key)).size).toBe(kinds.length);
    expect(new Set(kinds.map(kind => kind.badgeVariant)).size).toBe(kinds.length);
  });
});
