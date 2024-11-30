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

export default class ThreadTooltips {

    static header(threadInfo) {
        return `
            <div style="color: black" class="w-full text-center p-1 pl-2 pr-2 text-sm font-bold">` + threadInfo.javaName + `</div>
            <hr class="mt-1">`
    }

    static basic(eventLabel, segments) {
        let typeFragment = ""
        if (segments.length > 0) {
            typeFragment = `<tr>
                <th class="text-right"># of Events:</th>
                <td>` + segments.length + `<td>
            </tr>`
        }

        return `
            ${ThreadTooltips.#divider(eventLabel)}
            <table class="pl-1 pr-1 text-sm">
                ${typeFragment}
                <tr>
                    <th class="text-right">Samples (total):</th>
                    <td> --- <td>
                </tr>
                <tr>
                    <th class="text-right">Samples (self):</th>
                    <td> --- <td>
                </tr>
            </table>`
    }

    static #divider(text) {
        return `<div class="m-2 ml-4 italic text-gray-500 text-sm">${text}</div>`
    }
}
