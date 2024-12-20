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
import BasicFlamegraphTooltip from "@/service/flamegraphs/tooltips/BasicFlamegraphTooltip";

export default class CpuFlamegraphTooltip extends FlamegraphTooltip {

    constructor(eventType: string, useWeight: boolean) {
        super(eventType, useWeight);
    }

    generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string {
        let entity = new BasicFlamegraphTooltip(this.eventType, this.useWeight)
            .generate(frame, levelTotalSamples, levelTotalWeight)
        entity = entity + FlamegraphTooltip.position(frame.position)
        entity = entity + FlamegraphTooltip.frame_types(frame.sampleTypes)
        return entity
    }
}
