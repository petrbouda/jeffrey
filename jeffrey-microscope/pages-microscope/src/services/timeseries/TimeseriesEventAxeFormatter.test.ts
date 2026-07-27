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
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter';

describe('TimeseriesEventAxeFormatter', () => {
  it('formats the weight of every blocking event type as a duration', () => {
    const blocking = [
      'jdk.JavaMonitorEnter',
      'jdk.JavaMonitorWait',
      'jdk.ThreadPark',
      'jdk.ThreadSleep',
      'jdk.VirtualThreadPinned'
    ];

    for (const eventType of blocking) {
      expect(
        TimeseriesEventAxeFormatter.resolveAxisFormatter(true, eventType),
        `${eventType} carries a nanosecond weight and must render as a duration`
      ).toBe(AxisFormatType.DURATION_IN_NANOS);
    }
  });

  it('formats allocation weight as bytes', () => {
    expect(
      TimeseriesEventAxeFormatter.resolveAxisFormatter(true, 'jdk.ObjectAllocationInNewTLAB')
    ).toBe(AxisFormatType.BYTES);
  });

  it('counts samples as plain numbers when the weight is not used', () => {
    expect(TimeseriesEventAxeFormatter.resolveAxisFormatter(false, 'jdk.ThreadSleep')).toBe(
      AxisFormatType.NUMBER
    );
    expect(TimeseriesEventAxeFormatter.resolveAxisFormatter(true, 'jdk.ExecutionSample')).toBe(
      AxisFormatType.NUMBER
    );
  });
});
