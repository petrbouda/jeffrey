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
