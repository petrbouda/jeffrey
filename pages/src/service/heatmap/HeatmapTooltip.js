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

import FormattingService from "@/service/FormattingService";
import EventTypes from "@/service/EventTypes";

export default class HeatmapTooltip {

    constructor(eventType, useWeight) {
        this.eventType = eventType;
        this.useWeight = useWeight;
    }

    generate(value, second, millis) {
        const valueDiv = this.#generateValue(value)

        return `
            <table>`
            + valueDiv +
            `<tr>
                    <th style="text-align: right">Second:</th>
                    <td>${second}<td>
                </tr>
                <tr>
                    <th style="text-align: right">Millis:</th>
                    <td>${millis}<td>
                </tr>
            </table>`
    }

    #generateValue(value) {
        if (EventTypes.isAllocationEventType(this.eventType) && this.useWeight) {
            return this.#allocSamplesWithWeight(value)
        } else if (EventTypes.isBlockingEventType(this.eventType) && this.useWeight) {
            return this.#blockSamplesWithWeight(value)
        } else {
            return this.#basicValue(value)
        }
    }

    #basicValue(value) {
        return `
            <tr>
                <th style="text-align: right">Samples:</th>
                <td>` + value + `<td>
            </tr>`;
    }

    #blockSamplesWithWeight(value) {
        return `
            <tr>
                <th style="text-align: right">Blocked Time:</th>
                <td>` + FormattingService.formatDuration(value) + `<td>
            </tr>`;
    }

    #allocSamplesWithWeight(value) {
        return `
            <tr>
                <th style="text-align: right">Allocated:</th>
                <td>` + FormattingService.formatBytes(value) + `<td>
            </tr>`;
    }
}
