/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

export type TimeUnit = 'seconds' | 'milliseconds' | 'absolute-milliseconds';

/**
 * Utility class for converting between data timestamps and ApexCharts time format.
 * ApexCharts always expects time in milliseconds, but data may be in seconds or milliseconds.
 *
 * - 'seconds': relative profile time in seconds (converted to ms for chart)
 * - 'milliseconds': relative profile time already in milliseconds
 * - 'absolute-milliseconds': absolute epoch timestamps in milliseconds (uses local timezone for display)
 */
export default class TimeConverter {
  private readonly timeUnit: TimeUnit;

  constructor(timeUnit: TimeUnit = 'seconds') {
    this.timeUnit = timeUnit;
  }

  /** Convert data timestamp to ApexCharts time (always milliseconds) */
  toChartTime(value: number): number {
    return this.timeUnit === 'seconds' ? value * 1000 : value;
  }

  /** Convert ApexCharts time back to data timestamp */
  fromChartTime(value: number): number {
    return this.timeUnit === 'seconds' ? value / 1000 : value;
  }

  /** Format timestamp as HH:MM:SS string */
  formatTime(value: number): string {
    const date = new Date(this.toChartTime(value));
    if (this.timeUnit === 'absolute-milliseconds') {
      // Absolute timestamps: use local timezone
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const secs = String(date.getSeconds()).padStart(2, '0');
      return `${hours}:${minutes}:${secs}`;
    }
    // Relative timestamps: use UTC (time from 0)
    const hours = String(date.getUTCHours()).padStart(2, '0');
    const minutes = String(date.getUTCMinutes()).padStart(2, '0');
    const secs = String(date.getUTCSeconds()).padStart(2, '0');
    return `${hours}:${minutes}:${secs}`;
  }

  /** Format time range as "HH:MM:SS - HH:MM:SS" string */
  formatTimeRange(startTime: number, endTime: number): string {
    return `${this.formatTime(startTime)} - ${this.formatTime(endTime)}`;
  }

  /** Calculate visible range in data time units from minutes */
  getVisibleRangeFromMinutes(minutes: number): number {
    const rangeInSeconds = minutes * 60;
    return this.timeUnit === 'seconds' ? rangeInSeconds : rangeInSeconds * 1000;
  }
}
