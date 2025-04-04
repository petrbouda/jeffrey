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

import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import Frame from "@/services/flamegraphs/model/Frame";

export default class DifferentialFlamegraphTooltip extends FlamegraphTooltip {

    constructor(eventType: string, useWeight: boolean) {
        super(eventType, useWeight);
    }

    generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string {
        let diffFragment = ""
        let details = frame.diffDetails;

        let value, formattedValue, percent, formattedTotal
        if (this.useWeight) {
            value = details.weight
            formattedValue = FlamegraphTooltip.format_value_weight(this.eventType, Math.abs(details.weight))
            formattedTotal = FlamegraphTooltip.format_weight(this.eventType, frame.totalWeight, levelTotalWeight)
            percent = details.percentWeight
        } else {
            value = details.samples
            formattedValue = Math.abs(details.samples)
            formattedTotal = FlamegraphTooltip.format_samples(frame.totalSamples, levelTotalSamples)
            percent = details.percentSamples
        }

        if (value > 0) {
            diffFragment = diffFragment + `<tr>
                <th class="text-right text-red-500">Added:</th>
                <td>${formattedValue} (${percent}%)<td>
            </tr>`
        } else if (value < 0) {
            diffFragment = diffFragment + `<tr>
                <th class="text-right text-green-500">Removed:</th>
                <td>${formattedValue} (${percent}%)<td>
            </tr>`
        } else {
            diffFragment = diffFragment + `There is no difference in samples`
        }

        return `<div style="color: black" class="w-full text-center p-1 pl-2 pr-2 text-sm font-bold">${frame.title}</div>
           <hr class="mt-1">
           <table class="pl-1 pr-1 text-sm">
               <tr>
                   <th class="text-right">Total:</th>
                   <td>${formattedTotal}<td>
               </tr>
               ${diffFragment}
           </table>`
    }
}
