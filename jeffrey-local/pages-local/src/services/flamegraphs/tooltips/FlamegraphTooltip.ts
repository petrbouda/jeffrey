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

import Frame from "@/services/api/model/Frame";
import EventTypes from "@/services/EventTypes";
import FormattingService from "@/services/FormattingService";
import FramePosition from "@/services/api/model/FramePosition";
import FrameSampleTypes from "@/services/api/model/FrameSampleTypes";
import JavaMethodParser from "@/services/flamegraphs/JavaMethodParser";
import FrameColorResolver from "@/services/flamegraphs/FrameColorResolver";

export default abstract class FlamegraphTooltip {

    private static readonly COMPILATION_TYPES: { key: keyof FrameSampleTypes, label: string, color: string }[] = [
        { key: 'jit', label: 'JIT-compiled', color: '#94f25a' },
        { key: 'c1', label: 'C1-compiled', color: '#cce880' },
        { key: 'interpret', label: 'Interpreted', color: '#b2e1b2' },
        { key: 'inlined', label: 'Inlined', color: '#8eeded' },
    ];

    readonly eventType: string
    readonly useWeight: boolean

    protected constructor(eventType: string, useWeight: boolean) {
        this.eventType = eventType
        this.useWeight = useWeight
    }

    abstract generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string;

    /**
     * Compilation type breakdown as a stacked bar chart with legend.
     */
    static frame_types(types: FrameSampleTypes) {
        if (types == null) {
            return ""
        }

        const segments = FlamegraphTooltip.COMPILATION_TYPES
            .map(t => ({ ...t, value: types[t.key] ?? 0 }))
            .filter(s => s.value > 0);

        if (segments.length === 0) return "";

        const total = segments.reduce((sum, s) => sum + s.value, 0);

        let bar = '<div style="display:flex;height:14px;border-radius:3px;overflow:hidden;margin-bottom:6px">';
        for (const s of segments) {
            bar += `<div style="width:${s.value / total * 100}%;background:${s.color};min-width:2px" title="${s.label}: ${s.value}"></div>`;
        }
        bar += '</div>';

        let legend = '<div style="display:flex;flex-wrap:wrap;gap:4px 12px">';
        for (const s of segments) {
            legend += `<div style="display:flex;align-items:center;gap:3px;font-size:11px">
                <span style="width:7px;height:7px;border-radius:2px;background:${s.color};flex-shrink:0"></span>
                <span style="color:#748194">${s.label}</span>
                <span style="font-weight:600;color:#0b1727">${s.value.toLocaleString()}</span>
            </div>`;
        }
        legend += '</div>';

        return `${FlamegraphTooltip.divider("Compilation Breakdown")}<div style="padding:2px 10px 8px">${bar}${legend}</div>`;
    }

    /**
     * Self vs Total visual comparison bar.
     */
    static self_vs_total(selfSamples: number, totalSamples: number) {
        if (selfSamples <= 0 || totalSamples <= 0) return "";

        const selfPct = Math.max(selfSamples / totalSamples * 100, 1);
        const selfPctLabel = FlamegraphTooltip.pct(selfSamples, totalSamples);

        return `${FlamegraphTooltip.divider("Self vs Total")}
            <div style="padding:2px 10px 8px">
                <div style="height:16px;background:#edf2f9;border-radius:3px;overflow:hidden;position:relative;margin-bottom:4px">
                    <div style="width:100%;height:100%;background:rgba(94,100,255,0.1);border-radius:3px"></div>
                    <div style="position:absolute;top:0;left:0;height:100%;width:${selfPct}%;background:rgba(94,100,255,0.55);border-radius:3px"></div>
                </div>
                <div style="display:flex;justify-content:space-between;font-size:11px">
                    <span style="color:#5e64ff;font-weight:600">Self: ${selfSamples.toLocaleString()} (${selfPctLabel}%)</span>
                    <span style="color:#748194">Total: ${totalSamples.toLocaleString()}</span>
                </div>
            </div>`;
    }

    static position(position: FramePosition) {
        if (position == null) {
            return ""
        }

        return `
            ${FlamegraphTooltip.divider("Positioning")}
            <div style="padding:2px 10px 8px">
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">Bytecode (bci):</span>
                    <span class="small fw-semibold ms-2">${position.bci}</span>
                </div>
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">Line number:</span>
                    <span class="small fw-semibold ms-2">${position.line}</span>
                </div>
            </div>`
    }

    static divider(text: string) {
        return `<div style="padding:8px 10px 3px;color:#5e6e82;font-size:10px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px">${text}</div>`
    }

    /**
     * Generates the tooltip header with frame type color indicator.
     */
    static header(frame: Frame): string {
        const color = frame.type ? FrameColorResolver.resolveByType(frame.type) : '#888';
        const typeTitle = frame.type ? FrameColorResolver.resolveTitle(frame.type) : '';

        let titleHtml: string;
        if (frame.type && JavaMethodParser.isJavaFrame(frame.type)) {
            const parsed = JavaMethodParser.parse(frame.title);
            if (parsed && parsed.packageName) {
                titleHtml = `<div style="font-size:12px;color:#0b1727"><span style="font-weight:700">${parsed.className}</span><span style="font-style:italic">.${parsed.methodName}</span></div>
                    <div style="font-size:11px;color:#748194;margin-top:1px">${parsed.packageName}</div>`;
            } else {
                titleHtml = `<div style="font-weight:700;font-size:12px;color:#0b1727">${frame.title}</div>`;
            }
        } else {
            titleHtml = `<div style="font-weight:700;font-size:12px;color:#0b1727">${frame.title}</div>`;
        }

        const typeBadge = typeTitle
            ? `<span style="display:inline-block;padding:1px 6px;border-radius:3px;font-size:10px;font-weight:600;background:${color}40;color:#0b1727">${typeTitle}</span>`
            : '';

        return `<div style="padding:10px 12px;border-bottom:1px solid #eaedf1;background:#f9fafd">
            ${titleHtml}
            <div style="margin-top:5px">${typeBadge}</div>
        </div>`;
    }

    static format_samples(value: number, base: number) {
        return value.toLocaleString() + ' (' + FlamegraphTooltip.pct(value, base) + '%)'
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
