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

import TimeRange from "@/services/api/model/TimeRange";

export default class Utils {

    static capitalize(str: string) {
        return str.charAt(0).toUpperCase() + str.slice(1);
    }

    static toTimeRange(start: number[], end: number[], absoluteTime: boolean) : TimeRange {
        return new TimeRange(this.#toMillisByTime(start), this.#toMillisByTime(end), absoluteTime)
    }

    static #toMillisByTime(time: number[]) {
        return this.#toMillis(time[0], time[1])
    }

    static #toMillis(seconds: number, millis: number) {
        return seconds * 1000 + millis
    }

    static parseBoolean(value: any) {
        return value === true || value === 'true';
    }

    static isBlank(value: any) {
        return !Utils.isNotBlank(value)
    }

    static isNumber(value: any) {
        return !Utils.isNotNull(value) && Number.isInteger(value)
    }

    static isPositiveNumber(value: any) {
        if (typeof value === 'number') {
            return value > 0
        }
        if (typeof value === 'string') {
            const num = parseInt(value)
            return !isNaN(num) && num > 0
        }
        return false
    }

    static isNotBlank(value: any) {
        return value != null && value.trim().length > 0
    }

    static isNotNull(value: any) {
        return value != null
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

    static getEventSourceVariant(source: string): string {
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
                return 'Asprof Temp'
            case 'JVM_LOG':
                return 'JVM Log'
            case 'HS_JVM_ERROR_LOG':
                return 'HotSpot JVM Error Log'
            default:
                return fileType;
        }
    }
}
