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
            typeFragment = `
                <div class="d-flex justify-content-between align-items-center py-0">
                    <span class="small text-muted">Frame Type:</span>
                    <span class="small fw-semibold ms-2">${frame.typeTitle}</span>
                </div>`
        }

        return `
            ${FlamegraphTooltip.header(frame)}
            <div class="card-body p-0 pt-1">
                <div class="px-2 pb-1">
                    ${typeFragment}
                    <div class="d-flex justify-content-between align-items-center py-0">
                        <span class="small text-muted">Samples:</span>
                        <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(frame.totalSamples, levelTotalSamples)}</span>
                    </div>
                    <div class="d-flex justify-content-between align-items-center py-0">
                        <span class="small text-muted">${this.weightTitle}:</span>
                        <span class="small fw-semibold ms-2">${this.formatter(frame.totalWeight, levelTotalWeight)}</span>
                    </div>
                </div>
            </div>`
    }
}
