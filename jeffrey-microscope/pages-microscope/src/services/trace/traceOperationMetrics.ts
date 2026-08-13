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

import type { TraceOperationRow } from '@/services/api/model/trace/TraceModels';

/**
 * The totals shown above the operation list.
 *
 * Reduced from exactly the rows the list renders, rather than fetched separately: the list is
 * capped at the slowest operations, and a profile-wide total sitting on top of a capped list would
 * describe a different set of operations than the one underneath it.
 */
export interface TraceOperationTotals {
  operations: number;
  calls: number;
  errors: number;
  totalNanos: number;
  worstP95Nanos: number;
  slowestNanos: number;
}

export function operationTotals(operations: TraceOperationRow[]): TraceOperationTotals {
  return operations.reduce<TraceOperationTotals>(
    (totals, operation) => ({
      operations: totals.operations + 1,
      calls: totals.calls + operation.count,
      errors: totals.errors + operation.errorCount,
      totalNanos: totals.totalNanos + operation.totalNanos,
      worstP95Nanos: Math.max(totals.worstP95Nanos, operation.p95Nanos),
      slowestNanos: Math.max(totals.slowestNanos, operation.maxNanos)
    }),
    { operations: 0, calls: 0, errors: 0, totalNanos: 0, worstP95Nanos: 0, slowestNanos: 0 }
  );
}
