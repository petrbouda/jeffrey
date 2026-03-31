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

export default class BasicFlamegraphTooltip extends FlamegraphTooltip {
    private readonly weightTitle: string | null;
    private readonly weightFormatter: ((value: number, base: number) => string) | null;
    private readonly showPositionAndTypes: boolean;

    constructor(
        eventType: string,
        useWeight: boolean,
        weightTitle: string | null = null,
        weightFormatter: ((value: number, base: number) => string) | null = null,
        showPositionAndTypes: boolean = false) {

        super(eventType, useWeight);
        this.weightTitle = weightTitle;
        this.weightFormatter = weightFormatter;
        this.showPositionAndTypes = showPositionAndTypes;
    }

    generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string {
        const selfSamples = frame.selfSamples ?? 0;

        let samplesHtml = `
            <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                <span class="small text-muted">Samples (total):</span>
                <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(frame.totalSamples, levelTotalSamples)}</span>
            </div>`;

        if (selfSamples > 0) {
            samplesHtml += `
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">Samples (self):</span>
                    <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(selfSamples, levelTotalSamples)}</span>
                </div>`;
        }

        if (this.weightTitle && this.weightFormatter) {
            samplesHtml += `
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">${this.weightTitle}:</span>
                    <span class="small fw-semibold ms-2">${this.weightFormatter(frame.totalWeight ?? 0, levelTotalWeight)}</span>
                </div>`;
        }

        let extraSections = ""
        if (this.showPositionAndTypes) {
            extraSections += FlamegraphTooltip.position(frame.position);
            extraSections += FlamegraphTooltip.frame_types(frame.sampleTypes);
            extraSections += FlamegraphTooltip.self_vs_total(selfSamples, frame.totalSamples);
        }

        return `
            ${FlamegraphTooltip.header(frame)}
            <div style="padding:6px 0 6px">
                <div style="padding:2px 10px 6px">
                    ${samplesHtml}
                </div>
                ${extraSections}
            </div>`;
    }
}
