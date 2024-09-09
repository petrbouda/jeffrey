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

export default class Utils {

    static toTimeRange(start, end, absoluteTime) {
        return {
            start: this.#toMillisByTime(start),
            end: this.#toMillisByTime(end),
            absoluteTime: absoluteTime
        }
    }

    static #toMillisByTime(time) {
        return this.#toMillis(time[0], time[1])
    }

    static #toMillis(seconds, millis) {
        return seconds * 1000 + millis
    }

    static formatDateTime(dateTime) {
        const date = new Date(dateTime)
        const month = ("0" + (date.getMonth() + 1)).slice(-2)
        const day = ("0" + (date.getDate())).slice(-2)
        const hour = ("0" + (date.getHours())).slice(-2)
        const minute = ("0" + (date.getMinutes())).slice(-2)
        const second = ("0" + (date.getSeconds())).slice(-2)
        return date.getFullYear() + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second
    }

    static parseBoolean(value) {
        return value === true || value === 'true';
    }

    static isNotBlank(value) {
        return value != null && value.trim().length > 0
    }

    static isNotNull(value) {
        return value != null
    }
}
