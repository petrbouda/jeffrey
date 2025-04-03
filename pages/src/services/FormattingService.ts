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

export default class FormattingService {

    static UNITS = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB']

    // eslint-disable-next-line no-loss-of-precision
    static LONG_MAX: number = 9223372036854775807;
    static NO_TIMESTAMP: number = -9223372036854776000;

    static format(value: any, jfrType: string) {
        if (jfrType === "jdk.jfr.MemoryAddress") {
            return "0x" + parseInt(value).toString(16).toUpperCase()
        } else if (jfrType === "jdk.jfr.DataAmount") {
            return FormattingService.formatBytes(parseInt(value))
        } else if (jfrType === "jdk.jfr.Percentage") {
            return FormattingService.formatPercentage(parseFloat(value));
        } else if (jfrType === "jdk.jfr.Timestamp") {
            return FormattingService.formatTimestamp(value)
        } else if (jfrType === "jdk.jfr.Timespan") {
            return FormattingService.formatDuration2Units(value)
        } else if (jfrType === "Class") {
            return FormattingService.formatClass(value)
        } else if (jfrType === "Thread") {
            return FormattingService.formatThread(value)
        } else if (jfrType === "Boolean") {
            return FormattingService.formatBoolean(value)
        } else {
            if (value === null) {
                return "-"
            }
            return value
        }
    }

    static formatBytes(bytes: number) {
        if (bytes < 0) {
            return bytes + '';
        }

        if (bytes == 0) {
            return "0.00 B";
        }

        let e = Math.floor(Math.log(bytes) / Math.log(1024));
        return (bytes / Math.pow(1024, e)).toFixed(2) + ' ' + FormattingService.UNITS[e];
    }

    static formatPercentage(value: number) {
        const percentage = (value * 100).toFixed(2)
        return percentage + "%"
    }

    static formatClass(value: string | null): string {
        if (value === null) {
            return "-"
        } else {
            return value
        }
    }

    static formatThread(value: string): string {
        if (value === null) {
            return "-"
        } else {
            return value
        }
    }

    static formatBoolean(value: boolean | null) {
        if (value === null) {
            return "-"
        } else {
            return value
        }
    }

    static formatDuration(nanos: number): string {
        if (nanos === undefined || nanos === null || nanos < 0) {
            return "-"
        } else if (nanos === FormattingService.LONG_MAX) {
            return "∞"
        } else if (nanos === 0) {
            return "0"
        }

        const time = {
            d: Math.floor(nanos / 86_400_000_000_000),
            h: Math.floor(nanos / 3_600_000_000_000) % 24,
            m: Math.floor(nanos / 60_000_000_000) % 60,
            s: Math.floor(nanos / 1_000_000_000) % 60,
            ms: Math.floor(nanos / 1_000_000) % 1_000,
            us: Math.floor(nanos / 1_000) % 1_000,
            ns: Math.floor(nanos) % 1_000
        };
        return Object.entries(time)
            .filter(val => val[1] !== 0)
            .map(([key, val]) => `${val}${key}`)
            .join(' ');
    };

    static formatDuration2Units(nanos: number) {
        const durationString = FormattingService.formatDuration(nanos)
        return durationString.split(' ').slice(0, 2).join(' ')
    };

    static formatTimestamp(millis: number) {
        if (millis === undefined || millis === null || millis === FormattingService.NO_TIMESTAMP) {
            return "-"
        } else if (millis === 0) {
            return 0
        } else {
            return new Date(millis).toISOString();
        }
    };
}
