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

import { describe, expect, it } from 'vitest';
import {
  analysisDurationMs,
  docketItems,
  hasPatch,
  patchStats
} from '@/views/profiles/detail/advisor/advisorDocket';
import { shortMethodName } from '@/views/profiles/detail/advisor/eventTypeStyle';
import type { AdvisorEventType, AdvisorRunResult } from '@/services/api/model/Advisor';

const CPU = 'jdk.ExecutionSample';
const WALL = 'profiler.WallClockSample';
const ALLOC = 'jdk.ObjectAllocationSample';
const BLOCKING = 'jdk.JavaMonitorEnter';

const type = (eventType: string, label: string, available: boolean): AdvisorEventType => ({
  eventType,
  label,
  available
});

describe('docketItems', () => {
  it('orders every type CPU, Wall-Clock, Allocation, Blocking whatever order the backend sends', () => {
    const items = docketItems(
      [
        type(BLOCKING, 'Blocking', true),
        type(ALLOC, 'Allocation', true),
        type(WALL, 'Wall-Clock', true),
        type(CPU, 'CPU', true)
      ],
      () => undefined
    );

    expect(items.map(item => item.label)).toEqual(['CPU', 'Wall-Clock', 'Allocation', 'Blocking']);
  });

  it('keeps a type the recording has no samples for in its own place', () => {
    const items = docketItems(
      [type(CPU, 'CPU', true), type(WALL, 'Wall-Clock', false), type(ALLOC, 'Allocation', true)],
      () => undefined
    );

    // Dropping Wall-Clock — or pushing it to the end — is exactly what the docket must not do: a type
    // sits in the same place on every profile so the three pages read alike.
    expect(items.map(item => item.eventType)).toEqual([CPU, WALL, ALLOC]);
    expect(items[1].available).toBe(false);
  });

  it('grades only the types the recording carries', () => {
    const items = docketItems(
      [type(CPU, 'CPU', true), type(WALL, 'Wall-Clock', false)],
      () => ({ value: 'Critical', variant: 'danger' })
    );

    expect(items[0].badge?.value).toBe('Critical');
    expect(items[1].badge).toBeUndefined();
  });
});

describe('patchStats', () => {
  const PATCH = `--- a/RateTable.java
+++ b/RateTable.java
@@ -1,4 +1,5 @@
 unchanged
-slow
+fast
+extra
--- a/Cache.java
+++ b/Cache.java
@@ -9,1 +9,1 @@
-old
+new
`;

  it('counts files and changed lines without counting the diff headers', () => {
    // The headers start with the very prefixes an added and a removed line do; counting them would
    // inflate every patch by one line of each per file.
    expect(patchStats(PATCH)).toEqual({ files: 2, added: 3, removed: 2 });
  });

  it('reports nothing for a type the model proposed no edit for', () => {
    expect(patchStats(null)).toEqual({ files: 0, added: 0, removed: 0 });
    expect(hasPatch(null)).toBe(false);
    expect(hasPatch('   ')).toBe(false);
    expect(hasPatch(PATCH)).toBe(true);
  });

  it('does not count a deleted file as a file the patch writes', () => {
    const deletion = '--- a/Gone.java\n+++ /dev/null\n@@ -1,1 +0,0 @@\n-gone\n';
    expect(patchStats(deletion).files).toBe(0);
  });
});

describe('shortMethodName', () => {
  it('keeps the class and method and drops the package', () => {
    expect(shortMethodName('com.acme.pricing.PricingEngine.applyRules')).toBe(
      'PricingEngine.applyRules'
    );
  });

  it('leaves a name with nothing to drop alone', () => {
    expect(shortMethodName('socketRead0')).toBe('socketRead0');
    expect(shortMethodName('Cache.lookup')).toBe('Cache.lookup');
  });
});

describe('analysisDurationMs', () => {
  const runResult: AdvisorRunResult = {
    totalElapsedMs: 9_000,
    completedTypes: 1,
    totalTypes: 2,
    completedAt: 1_754_000_000_000,
    types: [{ eventType: CPU, status: 'COMPLETED', totalMs: 4_200, steps: [] }]
  };

  it('finds what the last run spent on a type', () => {
    expect(analysisDurationMs(runResult, CPU)).toBe(4_200);
  });

  it('has nothing to report for a type that run never touched', () => {
    expect(analysisDurationMs(runResult, WALL)).toBeNull();
    expect(analysisDurationMs(null, CPU)).toBeNull();
  });
});
