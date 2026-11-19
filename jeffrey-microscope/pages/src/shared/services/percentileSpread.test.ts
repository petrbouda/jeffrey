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
import {
  MIN_BAND_PERCENT,
  bandWidthPercent,
  positionPercent
} from '@shared/services/percentileSpread';

describe('positionPercent', () => {
  it('places a value as a percentage of the shared scale', () => {
    expect(positionPercent(25, 100)).toBe(25);
  });

  it('clamps a value above the scale so it cannot overhang the rail', () => {
    expect(positionPercent(150, 100)).toBe(100);
  });

  it('collapses to the left edge when there is no scale to measure against', () => {
    expect(positionPercent(42, 0)).toBe(0);
  });
});

describe('bandWidthPercent', () => {
  it('spans the gap between the two percentiles', () => {
    expect(bandWidthPercent(20, 60, 200)).toBe(20);
  });

  it('stays visible when an operation has no spread at all', () => {
    expect(bandWidthPercent(50, 50, 1000)).toBe(MIN_BAND_PERCENT);
  });

  it('is nothing at all when there is no scale', () => {
    expect(bandWidthPercent(10, 20, 0)).toBe(0);
  });
});
