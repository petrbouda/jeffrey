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
            diffFragment = `
                <div class="d-flex justify-content-between align-items-center py-0">
                    <span class="small text-danger">Added:</span>
                    <span class="small fw-semibold ms-2">${formattedValue} (${percent}%)</span>
                </div>`
        } else if (value < 0) {
            diffFragment = `
                <div class="d-flex justify-content-between align-items-center py-0">
                    <span class="small text-success">Removed:</span>
                    <span class="small fw-semibold ms-2">${formattedValue} (${percent}%)</span>
                </div>`
        } else {
            diffFragment = `<div class="small text-center text-muted py-0">There is no difference in samples</div>`
        }

        return `
            <div class="card-header py-1 px-2 text-center fw-bold border-bottom bg-light small">${frame.title}</div>
            <div class="card-body p-0 pt-1">
                <div class="px-2 pb-1">
                    <div class="d-flex justify-content-between align-items-center py-0">
                        <span class="small text-muted">Total:</span>
                        <span class="small fw-semibold ms-2">${formattedTotal}</span>
                    </div>
                    ${diffFragment}
                </div>
            </div>`
    }
}
