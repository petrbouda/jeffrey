/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
