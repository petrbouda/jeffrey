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

import type { SpanKind, TraceOperationId } from '@/services/api/model/trace/TraceModels';

/**
 * How many spans failed, written the way a person would say it. Shared by the trace row and the
 * span view's header so the same trace never reads "1 errors" in one place and "1 error" in the
 * other.
 */
export function errorLabel(count: number): string {
  return count === 1 ? '1 error' : `${count} errors`;
}

/**
 * The badge variant a span kind is drawn in. One mapping, used everywhere a kind appears, so a
 * CLIENT span cannot read as one colour in the operation list and another in the waterfall.
 */
export function spanKindVariant(kind: SpanKind): 'primary' | 'info' | 'secondary' {
  if (kind === 'SERVER') {
    return 'primary';
  }
  if (kind === 'CLIENT') {
    return 'info';
  }
  return 'secondary';
}

/**
 * A stable key for one trace type.
 *
 * The name alone is not one: an inbound and an outbound call of the same name are two operations,
 * and keying a list on the name would make them collide.
 */
export function operationKey(operation: TraceOperationId): string {
  return `${operation.eventType}|${operation.kind}|${operation.name}`;
}
