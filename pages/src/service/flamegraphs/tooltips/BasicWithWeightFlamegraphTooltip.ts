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

import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import Frame from "@/service/flamegraphs/model/Frame";

export default class BasicWithWeightFlamegraphTooltip extends FlamegraphTooltip {
    private readonly weightTitle: string;
    private readonly formatter: (value: number, base: number) => string;

    constructor(eventType: string, useWeight: boolean, weightTitle: string, formatter: (value: number, base: number) => string) {
        super(eventType, useWeight);
        this.weightTitle = weightTitle;
        this.formatter = formatter;
    }

    generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string {
        let typeFragment = ""
        if (frame.type != null) {
            typeFragment = `<tr>
                <th class="text-right">Frame Type:</th>
                <td>${frame.typeTitle}<td>
            </tr>`
        }

        return `<div style="color: black" class="w-full text-center p-1 pl-2 pr-2 text-sm font-bold">${frame.title}</div>
            <hr class="mt-1">
            <table class="pl-1 pr-1 text-sm">
                ${typeFragment}
                <tr>
                    <th class="text-right">Samples:</th>
                    <td>${FlamegraphTooltip.format_samples(frame.totalSamples, levelTotalSamples)}<td>
                </tr>
                <tr>
                    <th class="text-right">${this.weightTitle}:</th>
                    <td>${this.formatter(frame.totalWeight, levelTotalWeight)}<td>
                </tr>
            </table>`
    }
}
