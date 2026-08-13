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
 * The per-operation extremes shown above the operation list.
 *
 * Reduced from the rows the list renders, because they describe *an operation* — the profile-wide
 * overview cannot express "the slowest single operation". The profile totals beside them come from
 * the overview instead, which is uncapped.
 */
export interface TraceOperationTotals {
  worstP95Nanos: number;
  slowestNanos: number;
}

export function operationTotals(operations: TraceOperationRow[]): TraceOperationTotals {
  return operations.reduce<TraceOperationTotals>(
    (totals, operation) => ({
      worstP95Nanos: Math.max(totals.worstP95Nanos, operation.p95Nanos),
      slowestNanos: Math.max(totals.slowestNanos, operation.maxNanos)
    }),
    { worstP95Nanos: 0, slowestNanos: 0 }
  );
}
