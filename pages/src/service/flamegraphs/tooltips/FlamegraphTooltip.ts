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

import Frame from "@/service/flamegraphs/model/Frame";
import EventTypes from "@/service/EventTypes";
import FormattingService from "@/service/FormattingService";
import FramePosition from "@/service/flamegraphs/model/FramePosition";
import FrameSampleTypes from "@/service/flamegraphs/model/FrameSampleTypes";

export default abstract class FlamegraphTooltip {

    static SAMPLE_TYPE_MAPPING = new Map<string, string>([
        ["jit", "JIT-compiled"],
        ["interpret", "Interpreted"],
        ["c1", "C1-compiled"],
        ["inlined", "Inlined"],
    ]);

    protected eventType: string
    protected useWeight: boolean

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

        let entity = `${FlamegraphTooltip.divider("All Frame Types")}<table class="pl-1 pr-1 text-sm">`
        let sortable = [];
        for (const type in types) {
            const valueForFrameType = types[type as keyof FrameSampleTypes];
            sortable.push([type, valueForFrameType]);
        }
        sortable.sort(function (a, b) {
            return (b[1] as number) - (a[1] as number);
        });
        sortable.forEach(function (key) {
            entity = entity + `<tr>
                <th class="text-right">${FlamegraphTooltip.SAMPLE_TYPE_MAPPING.get(key[0] as string)}:</th>
                <td>${key[1]}<td>
            </tr>`
        })
        return entity + '</table>'
    }

    static position(position: FramePosition) {
        if (position == null) {
            return ""
        }

        return `
            ${FlamegraphTooltip.divider("Positioning")}
            <table class="pl-1 pr-1 text-sm">
                <tr>
                    <th class="text-right">Bytecode (bci):</th>
                    <td>${position.bci}<td>
                </tr>
                <tr>
                    <th class="text-right">Line number:</th>
                    <td>${position.line}<td>
                </tr>
            </table>`
    }

    static divider(text: string) {
        return `<div class="m-2 ml-4 italic text-gray-500 text-sm">${text}</div>`
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
            return FormattingService.formatDuration(value)
        } else {
            return value
        }
    }

    static format_bytes(value: number, base: number) {
        return FormattingService.formatBytes(value) + ' (' + FlamegraphTooltip.pct(value, base) + '%)'
    }

    static format_duration(value: number, base: number) {
        return FormattingService.formatDuration(value) + ' (' + FlamegraphTooltip.pct(value, base) + '%)'
    }

    static pct(a: number, b: number) {
        return a >= b ? '100' : ((100 * a) / b).toFixed(2);
    }
}
