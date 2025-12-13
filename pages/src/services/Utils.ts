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

import TimeRange from "@/services/flamegraphs/model/TimeRange";

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

    static formatDateTime(dateTime: string) {
        const date = new Date(dateTime)
        const month = ("0" + (date.getMonth() + 1)).slice(-2)
        const day = ("0" + (date.getDate())).slice(-2)
        const hour = ("0" + (date.getHours())).slice(-2)
        const minute = ("0" + (date.getMinutes())).slice(-2)
        const second = ("0" + (date.getSeconds())).slice(-2)
        return date.getFullYear() + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second
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
    static formatFileType(fileType: string): string {
        switch (fileType) {
            case 'PERF_COUNTERS':
                return 'Perf Counters';
            case 'HEAP_DUMP':
                return 'Heap Dump';
            case 'UNKNOWN':
                return 'Unknown';
            case 'ASPROF_TEMP':
                return 'Asprof Temp'
            case 'JVM_LOG':
                return 'JVM Log'
            default:
                return fileType;
        }
    }
}
