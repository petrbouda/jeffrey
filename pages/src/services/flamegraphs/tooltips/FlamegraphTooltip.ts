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

import Frame from "@/services/flamegraphs/model/Frame";
import EventTypes from "@/services/EventTypes";
import FormattingService from "@/services/FormattingService";
import FramePosition from "@/services/flamegraphs/model/FramePosition";
import FrameSampleTypes from "@/services/flamegraphs/model/FrameSampleTypes";

export default abstract class FlamegraphTooltip {

    static SAMPLE_TYPE_MAPPING = new Map<string, string>([
        ["jit", "JIT-compiled"],
        ["interpret", "Interpreted"],
        ["c1", "C1-compiled"],
        ["inlined", "Inlined"],
    ]);

    readonly eventType: string
    readonly useWeight: boolean

    protected constructor(eventType: string, useWeight: boolean) {
        this.eventType = eventType
        this.useWeight = useWeight
    }

    abstract generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string;

    /**
     * Frame types are sorted and printed.
     */
    static frame_types(types: FrameSampleTypes) {
        if (types == null) {
            return ""
        }

        let entity = `${FlamegraphTooltip.divider("All Frame Types")}<div class="px-2 pb-1">`
        let sortable = [];
        for (const type in types) {
            const valueForFrameType = types[type as keyof FrameSampleTypes];
            sortable.push([type, valueForFrameType]);
        }
        sortable.sort(function (a, b) {
            return (b[1] as number) - (a[1] as number);
        });
        sortable.forEach(function (key) {
            entity = entity + `<div class="d-flex justify-content-between align-items-center py-0">
                <span class="small text-muted">${FlamegraphTooltip.SAMPLE_TYPE_MAPPING.get(key[0] as string)}:</span>
                <span class="small fw-semibold ms-2">${key[1]}</span>
            </div>`
        })
        return entity + '</div>'
    }

    static position(position: FramePosition) {
        if (position == null) {
            return ""
        }

        return `
            ${FlamegraphTooltip.divider("Positioning")}
            <div class="px-2 pb-1">
                <div class="d-flex justify-content-between align-items-center py-0">
                    <span class="small text-muted">Bytecode (bci):</span>
                    <span class="small fw-semibold ms-2">${position.bci}</span>
                </div>
                <div class="d-flex justify-content-between align-items-center py-0">
                    <span class="small text-muted">Line number:</span>
                    <span class="small fw-semibold ms-2">${position.line}</span>
                </div>
            </div>`
    }

    static divider(text: string) {
        return `<div class="py-1 px-2 text-muted small fst-italic">${text}</div>`
    }

    static format_samples(value: number, base: number) {
        return value + ' (' + FlamegraphTooltip.pct(value, base) + '%)'
    }

    static format_weight(eventType: string, value: number, base: number) {
        if (EventTypes.isAllocationEventType(eventType)) {
            return FlamegraphTooltip.format_bytes(value, base)
        } else if (EventTypes.isBlockingEventType(eventType)) {
            return FlamegraphTooltip.format_duration(value, base)
        } else {
            return FlamegraphTooltip.format_samples(value, base)
        }
    }

    static format_value_weight(eventType: string, value: number) {
        if (EventTypes.isAllocationEventType(eventType)) {
            return FormattingService.formatBytes(value)
        } else if (EventTypes.isBlockingEventType(eventType)) {
            return FormattingService.formatDuration2Units(value)
        } else {
            return value
        }
    }

    static format_bytes(value: number, base: number) {
        return FormattingService.formatBytes(value) + ' (' + FlamegraphTooltip.pct(value, base) + '%)'
    }

    static format_duration(value: number, base: number) {
        return FormattingService.formatDuration2Units(value) + ' (' + FlamegraphTooltip.pct(value, base) + '%)'
    }

    static pct(a: number, b: number) {
        return a >= b ? '100' : ((100 * a) / b).toFixed(2);
    }
}
