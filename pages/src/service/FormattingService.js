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
    static LONG_MAX = 9223372036854775807;

    static formatBytes(bytes) {
        if (bytes === 0) {
            return "0.00 B";
        }

        let e = Math.floor(Math.log(bytes) / Math.log(1024));
        return (bytes / Math.pow(1024, e)).toFixed(2) + ' ' + FormattingService.UNITS[e];
    }

    static formatPercentage(value) {
        const percentage = (value * 100).toFixed(2)
        return percentage + "%"
    }

    static formatDuration(nanos) {
        if (nanos === undefined || nanos === null) {
            return "-"
        } else if (nanos === FormattingService.LONG_MAX) {
            return "∞"
        } else if (nanos === 0) {
            return 0
        }

        let us = nanos / 1000;
        if (us < 0) us = -us;
        const time = {
            d: Math.floor(us / 86_400_000_000),
            h: Math.floor(us / 3_600_000_000) % 24,
            m: Math.floor(us / 60_000_000) % 60,
            s: Math.floor(us / 1_000_000) % 60,
            ms: Math.floor(us / 1_000) % 1_000,
            us: Math.floor(us) % 1_000
        };
        return Object.entries(time)
            .filter(val => val[1] !== 0)
            .map(([key, val]) => `${val}${key}`)
            .join(' ');
    };

    static formatDuration2Units(nanos) {
        const durationString = FormattingService.formatDuration(nanos)
        return durationString.split(' ').slice(0, 2).join(' ')
    };

    static formatTimestamp(millis) {
        if (millis === undefined || millis === null) {
            return "-"
        } else if (millis === 0) {
            return 0
        } else {
            return new Date(millis).toISOString();
        }
    };
}
