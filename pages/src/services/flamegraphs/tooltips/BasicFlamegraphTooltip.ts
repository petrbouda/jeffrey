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

export default class BasicFlamegraphTooltip extends FlamegraphTooltip {

    constructor(eventType: string, useWeight: boolean) {
        super(eventType, useWeight);
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

        let selfFragment = ""
        if (frame.selfSamples != null) {
            selfFragment = `
                <div class="d-flex justify-content-between align-items-center py-0">
                    <span class="small text-muted">Self:</span>
                    <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(frame.selfSamples, frame.totalSamples)}</span>
                </div>`
        }

        return `
            <div class="card-header py-1 px-2 text-center fw-bold border-bottom bg-light small">${frame.title}</div>
            <div class="card-body p-0 pt-1">
                <div class="px-2 pb-1">
                    ${typeFragment}
                    <div class="d-flex justify-content-between align-items-center py-0">
                        <span class="small text-muted">Samples (total):</span>
                        <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(frame.totalSamples, levelTotalSamples)}</span>
                    </div>
                    <div class="d-flex justify-content-between align-items-center py-0">
                        <span class="small text-muted">Samples (self):</span>
                        <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(frame.selfSamples, levelTotalSamples)}</span>
                    </div>
                    ${selfFragment}
                </div>
            </div>`
    }
}
