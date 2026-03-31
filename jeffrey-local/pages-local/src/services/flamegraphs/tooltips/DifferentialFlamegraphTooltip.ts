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
import Frame from "@/services/api/model/Frame";

export default class DifferentialFlamegraphTooltip extends FlamegraphTooltip {

    constructor(eventType: string, useWeight: boolean) {
        super(eventType, useWeight);
    }

    generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string {
        let details = frame.diffDetails;

        let value, formattedValue, percent: string, formattedTotal
        if (this.useWeight) {
            value = details.weight
            formattedValue = FlamegraphTooltip.format_value_weight(this.eventType, Math.abs(details.weight))
            formattedTotal = FlamegraphTooltip.format_weight(this.eventType, frame.totalWeight, levelTotalWeight)
            percent = details.percentWeight.toFixed(2)
        } else {
            value = details.samples
            formattedValue = Math.abs(details.samples).toLocaleString()
            formattedTotal = FlamegraphTooltip.format_samples(frame.totalSamples, levelTotalSamples)
            percent = details.percentSamples.toFixed(2)
        }

        let diffFragment: string
        if (value > 0) {
            diffFragment = `
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small" style="color:#b82230">Added:</span>
                    <span class="small fw-semibold ms-2">${formattedValue} (${percent}%)</span>
                </div>`
        } else if (value < 0) {
            diffFragment = `
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small" style="color:#00994d">Removed:</span>
                    <span class="small fw-semibold ms-2">${formattedValue} (${percent}%)</span>
                </div>`
        } else {
            diffFragment = `<div class="small text-center text-muted py-0">No difference in samples</div>`
        }

        return `
            ${FlamegraphTooltip.header(frame)}
            <div style="padding:6px 0 6px">
                <div style="padding:2px 10px 6px">
                    <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                        <span class="small text-muted">Total:</span>
                        <span class="small fw-semibold ms-2">${formattedTotal}</span>
                    </div>
                    ${diffFragment}
                </div>
            </div>`
    }
}
