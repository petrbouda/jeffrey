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
import GraphType from '@/services/flamegraphs/GraphType';
import { flamegraphViewOpening } from './FlamegraphViewOpening';

const BASELINE = 'baseline-profile-id';

describe('flamegraphViewOpening', () => {
  it('opens the primary graph for graphMode PRIMARY', () => {
    expect(flamegraphViewOpening(GraphType.PRIMARY, BASELINE)).toEqual({ kind: 'PRIMARY' });
  });

  it('opens the differential graph against the adopted baseline', () => {
    expect(flamegraphViewOpening(GraphType.DIFFERENTIAL, BASELINE)).toEqual({
      kind: 'DIFFERENTIAL',
      baselineId: BASELINE
    });
  });

  it('shows the missing-baseline state for a differential graph with no baseline', () => {
    expect(flamegraphViewOpening(GraphType.DIFFERENTIAL, null)).toEqual({
      kind: 'MISSING_BASELINE'
    });
  });

  it('opens the primary graph when the link names no graphMode, baseline or not', () => {
    // Only a differential graph needs a baseline: a link without graphMode must not be told one is
    // missing.
    expect(flamegraphViewOpening(undefined, null)).toEqual({ kind: 'PRIMARY' });
    expect(flamegraphViewOpening(undefined, BASELINE)).toEqual({ kind: 'PRIMARY' });
  });

  it('opens the primary graph for an unknown graphMode', () => {
    expect(flamegraphViewOpening('SIDEWAYS', null)).toEqual({ kind: 'PRIMARY' });
  });

  it('reads the first value when graphMode is repeated', () => {
    expect(flamegraphViewOpening([GraphType.DIFFERENTIAL, GraphType.PRIMARY], null)).toEqual({
      kind: 'MISSING_BASELINE'
    });
  });
});
