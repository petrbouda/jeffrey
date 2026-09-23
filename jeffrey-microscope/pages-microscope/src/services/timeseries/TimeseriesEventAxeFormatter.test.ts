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
