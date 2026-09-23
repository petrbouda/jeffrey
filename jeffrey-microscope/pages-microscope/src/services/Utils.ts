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

import TimeRange from '@/services/api/model/TimeRange';
import type { Variant } from '@shared/types/ui';

export default class Utils {
  static capitalize(str: string) {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  static toTimeRange(start: number[], end: number[], absoluteTime: boolean): TimeRange {
    return new TimeRange(this.#toMillisByTime(start), this.#toMillisByTime(end), absoluteTime);
  }

  static #toMillisByTime(time: number[]) {
    return this.#toMillis(time[0], time[1]);
  }

  static #toMillis(seconds: number, millis: number) {
    return seconds * 1000 + millis;
  }

  static parseBoolean(value: any) {
    return value === true || value === 'true';
  }

  static isBlank(value: any) {
    return !Utils.isNotBlank(value);
  }

  static isNumber(value: any) {
    return !Utils.isNotNull(value) && Number.isInteger(value);
  }

  static isPositiveNumber(value: any) {
    if (typeof value === 'number') {
      return value > 0;
    }
    if (typeof value === 'string') {
      const num = parseInt(value);
      return !isNaN(num) && num > 0;
    }
    return false;
  }

  static isNotBlank(value: any) {
    return value != null && value.trim().length > 0;
  }

  static isNotNull(value: any) {
    return value != null;
  }

  /**
   * Format file type names for display
   * Converts technical file type names to more readable formats
   * @param fileType The file type string to format
   * @returns Formatted file type string for display
   */
  static formatEventSource(source: string): string {
    switch (source) {
      case 'ASYNC_PROFILER':
        return 'Async Profiler';
      case 'HEAP_DUMP':
        return 'Heap Dump';
      case 'UNKNOWN':
        return 'Unknown';
      default:
        return source;
    }
  }

  static getEventSourceVariant(source: string): Variant {
    switch (source) {
      case 'ASYNC_PROFILER':
        return 'purple';
      case 'JDK':
        return 'info';
      default:
        return 'grey';
    }
  }

  static formatFileType(fileType: string): string {
    switch (fileType) {
      case 'JFR_LZ4':
        return 'JFR (LZ4)';
      case 'PERF_COUNTERS':
        return 'Perf Counters';
      case 'HEAP_DUMP_GZ':
        return 'Heap Dump (GZ)';
      case 'HEAP_DUMP':
        return 'Heap Dump';
      case 'UNKNOWN':
        return 'Unknown';
      case 'ASPROF_TEMP':
        return 'Asprof Temp';
      case 'JVM_LOG':
        return 'JVM Log';
      case 'HS_JVM_ERROR_LOG':
        return 'HotSpot JVM Error Log';
      case 'APP_LOG':
        return 'Application Log';
      default:
        return fileType;
    }
  }
}
