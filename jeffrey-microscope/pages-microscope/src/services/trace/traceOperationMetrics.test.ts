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
import { operationTotals } from '@/services/trace/traceOperationMetrics';
import type { TraceOperationRow } from '@/services/api/model/trace/TraceModels';

function operation(overrides: Partial<TraceOperationRow>): TraceOperationRow {
  return {
    name: 'op',
    kind: 'INTERNAL',
    eventType: 'jeffrey.TraceSpan',
    count: 0,
    errorCount: 0,
    spanCount: 0,
    totalNanos: 0,
    p50Nanos: 0,
    p95Nanos: 0,
    maxNanos: 0,
    ...overrides
  };
}

describe('operationTotals', () => {
  it('reports zeros for a profile with no operations', () => {
    expect(operationTotals([])).toEqual({
      worstP95Nanos: 0,
      slowestNanos: 0
    });
  });

  it('passes a single operation through unchanged', () => {
    const totals = operationTotals([
      operation({ count: 12, errorCount: 2, totalNanos: 900, p95Nanos: 120, maxNanos: 300 })
    ]);

    expect(totals).toEqual({
      worstP95Nanos: 120,
      slowestNanos: 300
    });
  });

  it('sums the counts but takes the worst of the latencies', () => {
    const totals = operationTotals([
      operation({ count: 10, errorCount: 1, totalNanos: 500, p95Nanos: 90, maxNanos: 200 }),
      operation({ count: 5, errorCount: 0, totalNanos: 250, p95Nanos: 400, maxNanos: 410 })
    ]);

    expect(totals.worstP95Nanos).toBe(400);
    expect(totals.slowestNanos).toBe(410);
  });
});
