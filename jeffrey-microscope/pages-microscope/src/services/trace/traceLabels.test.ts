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

import { errorLabel, operationKey, spanKindVariant } from '@/services/trace/traceLabels';
import type { TraceOperationId } from '@/services/api/model/trace/TraceModels';

describe('errorLabel', () => {
  it('says "1 error", not "1 errors"', () => {
    expect(errorLabel(1)).toBe('1 error');
  });

  it('pluralises everything else', () => {
    expect(errorLabel(0)).toBe('0 errors');
    expect(errorLabel(3)).toBe('3 errors');
  });
});

describe('spanKindVariant', () => {
  it('gives each kind its own variant, so the three never read alike', () => {
    const variants = [
      spanKindVariant('SERVER'),
      spanKindVariant('CLIENT'),
      spanKindVariant('INTERNAL')
    ];

    expect(new Set(variants).size).toBe(3);
  });
});

describe('operationKey', () => {
  const inbound: TraceOperationId = {
    name: 'GET /api/internal/health',
    kind: 'SERVER',
    eventType: 'jeffrey.HttpServerExchange'
  };

  it('separates an inbound call from an outbound one of the same name', () => {
    // The whole reason the key is not the name: these are two operations, and a list keyed on the
    // name alone would collide them into one row.
    const outbound: TraceOperationId = {
      ...inbound,
      kind: 'CLIENT',
      eventType: 'jeffrey.HttpClientExchange'
    };

    expect(operationKey(inbound)).not.toBe(operationKey(outbound));
  });

  it('is stable for the same identity', () => {
    expect(operationKey(inbound)).toBe(operationKey({ ...inbound }));
  });

  it('separates operations differing only in kind', () => {
    expect(operationKey(inbound)).not.toBe(operationKey({ ...inbound, kind: 'INTERNAL' }));
  });

  it('separates operations differing only in instrumentation', () => {
    expect(operationKey(inbound)).not.toBe(
      operationKey({ ...inbound, eventType: 'jeffrey.TraceSpan' })
    );
  });
});
