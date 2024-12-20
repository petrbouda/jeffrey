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
import EventTypes from "@/service/EventTypes";
import DifferentialFlamegraphTooltip from "@/service/flamegraphs/tooltips/DifferentialFlamegraphTooltip";
import CpuFlamegraphTooltip from "@/service/flamegraphs/tooltips/CpuFlamegraphTooltip";
import TlabAllocFlamegraphTooltip from "@/service/flamegraphs/tooltips/TlabAllocFlamegraphTooltip";
import BlockingFlamegraphTooltip from "@/service/flamegraphs/tooltips/BlockingFlamegraphTooltip";
import BasicFlamegraphTooltip from "@/service/flamegraphs/tooltips/BasicFlamegraphTooltip";

export default class FlamegraphTooltipFactory {

    static create(eventType: string, useWeight: boolean, isDifferential: boolean): FlamegraphTooltip {
        if (isDifferential) {
            return new DifferentialFlamegraphTooltip(eventType, useWeight)
        } else if (EventTypes.isExecutionEventType(eventType)) {
            return new CpuFlamegraphTooltip(eventType, useWeight)
        } else if (EventTypes.isAllocationEventType(eventType)) {
            return new TlabAllocFlamegraphTooltip(eventType, useWeight)
        } else if (EventTypes.isBlockingEventType(eventType)) {
            return new BlockingFlamegraphTooltip(eventType, useWeight)
        } else {
            return new BasicFlamegraphTooltip(eventType, useWeight)
        }
    }
}
