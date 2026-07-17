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

import Frame from '@/services/api/model/Frame';
import EventTypes from '@/services/EventTypes';
import FormattingService from '@shared/services/FormattingService';
import FramePosition from '@/services/api/model/FramePosition';
import FrameSampleTypes from '@/services/api/model/FrameSampleTypes';
import JavaMethodParser from '@/services/flamegraphs/JavaMethodParser';
import FrameColorResolver from '@/services/flamegraphs/FrameColorResolver';
import FrameType from '@/services/flamegraphs/FrameType';
import { parseUnknownFrame, type ParsedFrameName } from '@/services/flamegraphs/FrameNameParser';
import ideConfigStore from '@/stores/ideConfigStore';

export default abstract class FlamegraphTooltip {
  private static readonly COMPILATION_TYPES: {
    key: keyof FrameSampleTypes;
    label: string;
    color: string;
  }[] = [
    { key: 'jit', label: 'JIT-compiled', color: '#94f25a' },
    { key: 'c1', label: 'C1-compiled', color: '#cce880' },
    { key: 'interpret', label: 'Interpreted', color: '#b2e1b2' },
    { key: 'inlined', label: 'Inlined', color: '#8eeded' }
  ];

  readonly eventType: string;
  readonly useWeight: boolean;

  protected constructor(eventType: string, useWeight: boolean) {
    this.eventType = eventType;
    this.useWeight = useWeight;
  }

  abstract generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string;

  /**
   * Compilation type breakdown as a stacked bar chart with legend.
   */
  static frame_types(types: FrameSampleTypes) {
    if (types == null) {
      return '';
    }

    const segments = FlamegraphTooltip.COMPILATION_TYPES.map(t => ({
      ...t,
      value: types[t.key] ?? 0
    })).filter(s => s.value > 0);

    if (segments.length === 0) return '';

    const total = segments.reduce((sum, s) => sum + s.value, 0);

    let bar =
      '<div style="display:flex;height:14px;border-radius:3px;overflow:hidden;margin-bottom:6px">';
    for (const s of segments) {
      bar += `<div style="width:${(s.value / total) * 100}%;background:${s.color};min-width:2px" title="${s.label}: ${s.value}"></div>`;
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

    return `${FlamegraphTooltip.divider('Compilation Breakdown')}<div style="padding:2px 10px 8px">${bar}${legend}</div>`;
  }

  /**
   * Self vs Total visual comparison bar.
   */
  static self_vs_total(selfSamples: number, totalSamples: number) {
    if (selfSamples <= 0 || totalSamples <= 0) return '';

    const selfPct = Math.max((selfSamples / totalSamples) * 100, 1);
    const selfPctLabel = FlamegraphTooltip.pct(selfSamples, totalSamples);

    return `${FlamegraphTooltip.divider('Self vs Total')}
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

  static position(position: FramePosition, frameType: string | undefined) {
    if (position == null) {
      return '';
    }

    // Bytecode index is JVM-bytecode-specific — only Java (JIT/C1/interpreted/inlined) frames carry
    // it. pprof and native/C++/kernel frames have no bci, so drop that row for them. Line numbers
    // exist beyond the JVM (pprof records them in its Line message), so show a line only when set.
    const showBci = frameType != null && JavaMethodParser.isJavaFrame(frameType);
    const showLine = position.line > 0;
    if (!showBci && !showLine) {
      return '';
    }

    const bciRow = showBci
      ? `<div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">Bytecode (bci):</span>
                    <span class="small fw-semibold ms-2">${position.bci}</span>
                </div>`
      : '';
    const lineRow = showLine
      ? `<div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">Line number:</span>
                    <span class="small fw-semibold ms-2">${position.line}</span>
                </div>`
      : '';

    return `
            ${FlamegraphTooltip.divider('Positioning')}
            <div style="padding:2px 10px 8px">${bciRow}${lineRow}</div>`;
  }

  static divider(text: string) {
    return `<div style="padding:8px 10px 3px;color:#5e6e82;font-size:10px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px">${text}</div>`;
  }

  /**
   * Generates the tooltip header with frame type color indicator.
   */
  static header(frame: Frame): string {
    const color = frame.type ? FrameColorResolver.resolveByType(frame.type) : '#888';
    const typeTitle = frame.type ? FrameColorResolver.resolveTitle(frame.type) : '';

    // Split JFR Java frames, and pprof (UNKNOWN) frames carrying the '#' boundary, into package
    // (muted) + class (bold) + method (italic). C++ frames use '::' as the method separator.
    let parsed: ParsedFrameName | null = null;
    if (frame.type && JavaMethodParser.isJavaFrame(frame.type)) {
      const java = JavaMethodParser.parse(frame.title);
      parsed = java
        ? { pkg: java.packageName, className: java.className, separator: '.', methodName: java.methodName }
        : null;
    } else if (frame.type === FrameType.UNKNOWN && frame.title.includes('#')) {
      parsed = parseUnknownFrame(frame.title);
    }

    let titleHtml: string;
    if (parsed && parsed.pkg) {
      titleHtml = `<div style="font-size:12px;color:#0b1727"><span style="font-weight:700">${parsed.className}</span><span style="font-style:italic">${parsed.separator}${parsed.methodName}</span></div>
                    <div style="font-size:11px;color:#748194;margin-top:1px">${parsed.pkg}</div>`;
    } else if (parsed) {
      titleHtml = `<div style="font-size:12px;color:#0b1727"><span style="font-weight:700">${parsed.className}</span><span style="font-style:italic">${parsed.separator}${parsed.methodName}</span></div>`;
    } else {
      titleHtml = `<div style="font-weight:700;font-size:12px;color:#0b1727">${frame.title}</div>`;
    }

    // UNKNOWN conveys nothing useful (e.g. every pprof frame is UNKNOWN, as pprof carries no
    // frame-type info), so omit the type badge entirely for it.
    const typeBadge =
      typeTitle && frame.type !== FrameType.UNKNOWN
        ? `<span style="display:inline-block;padding:1px 6px;border-radius:3px;font-size:10px;font-weight:600;background:${color}40;color:#0b1727">${typeTitle}</span>`
        : '';

    return `<div style="padding:10px 12px;border-bottom:1px solid #eaedf1;background:#f9fafd">
            ${titleHtml}
            <div style="margin-top:5px">${typeBadge}</div>
        </div>`;
  }

  static ide_action(frame: Frame): string {
    if (!frame.type || !JavaMethodParser.isJavaFrame(frame.type)) {
      return '';
    }
    if (!ideConfigStore.isEnabled()) {
      return '';
    }
    const parsed = JavaMethodParser.parse(frame.title);
    if (!parsed || !parsed.className) {
      return '';
    }
    const fqn = parsed.packageName ? `${parsed.packageName}.${parsed.className}` : parsed.className;
    const method = `${parsed.className}.${parsed.methodName}`;
    const line = frame.position?.line ?? -1;
    const fqnAttr = FlamegraphTooltip.escapeAttr(fqn);
    const methodAttr = FlamegraphTooltip.escapeAttr(method);
    const titleAttr = FlamegraphTooltip.escapeAttr(parsed.className);
    const dataAttrs = `data-fqn="${fqnAttr}" data-method="${methodAttr}" data-line="${line}" data-title="${titleAttr}"`;
    // In JFR Profiler Plugin mode the buttons start disabled and are enabled asynchronously once the
    // IDE confirms it contains the class (see Tooltip.applyIdeGate). In Jeffrey Plugin mode they are
    // always enabled.
    const gated = ideConfigStore.isJfrProfilerMode();
    const gateAttrs = gated ? 'data-ide-gated="true" disabled' : '';
    return `<div style="padding:8px 10px 10px;border-top:1px solid #eaedf1;background:#fbfcfe;display:flex;gap:6px">
            <button type="button" class="ide-jump-button" style="flex:1" data-ide-action="open" ${dataAttrs} ${gateAttrs}>
                <i class="bi bi-box-arrow-up-right"></i> Open in IDE
            </button>
            <button type="button" class="ide-jump-button" style="flex:1" data-ide-action="source" ${dataAttrs} ${gateAttrs}>
                <i class="bi bi-file-earmark-code"></i> View Source
            </button>
        </div>`;
  }

  private static escapeAttr(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/"/g, '&quot;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  static format_samples(value: number, base: number) {
    return value.toLocaleString() + ' (' + FlamegraphTooltip.pct(value, base) + '%)';
  }

  static format_weight(eventType: string, value: number, base: number) {
    if (EventTypes.isAllocationEventType(eventType)) {
      return FlamegraphTooltip.format_bytes(value, base);
    } else if (EventTypes.isBlockingEventType(eventType)) {
      return FlamegraphTooltip.format_duration(value, base);
    } else {
      return FlamegraphTooltip.format_samples(value, base);
    }
  }

  static format_value_weight(eventType: string, value: number) {
    if (EventTypes.isAllocationEventType(eventType)) {
      return FormattingService.formatBytes(value);
    } else if (EventTypes.isBlockingEventType(eventType)) {
      return FormattingService.formatDuration2Units(value);
    } else {
      return value;
    }
  }

  static format_bytes(value: number, base: number) {
    return FormattingService.formatBytes(value) + ' (' + FlamegraphTooltip.pct(value, base) + '%)';
  }

  static format_duration(value: number, base: number) {
    return (
      FormattingService.formatDuration2Units(value) +
      ' (' +
      FlamegraphTooltip.pct(value, base) +
      '%)'
    );
  }

  static pct(a: number, b: number) {
    return a >= b ? '100' : ((100 * a) / b).toFixed(2);
  }

  /**
   * Special-cased tooltip for TRUNCATED_SYNTHETIC frames. Skips the standard
   * self-vs-total bar (would show 100% by construction) and shows the pruned-sample
   * total, the count of direct children that were pruned, and (when the graph is
   * rendering by weight) the pruned weight aggregate in the matching unit.
   */
  static truncated(
    frame: Frame,
    levelTotalSamples: number,
    useWeight: boolean = false,
    eventType: string | null = null,
    levelTotalWeight: number = 0
  ): string {
    const prunedCount = frame.prunedChildrenCount ?? 0;
    const weightRow = FlamegraphTooltip.truncatedWeightRow(
      frame,
      useWeight,
      eventType,
      levelTotalWeight
    );
    return `
            <div style="padding:10px 12px;border-bottom:1px solid #eaedf1;background:#f9fafd">
                <div style="font-weight:700;font-size:12px;color:#0b1727">Truncated subtree</div>
                <div style="margin-top:5px">
                    <span style="display:inline-block;padding:1px 6px;border-radius:3px;font-size:10px;font-weight:600;background:#fce7f3;color:#831843">SYNTHETIC · BELOW THRESHOLD</span>
                </div>
            </div>
            <div style="padding:6px 10px 10px">
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">Pruned samples:</span>
                    <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(frame.totalSamples, levelTotalSamples)}</span>
                </div>
                ${weightRow}
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">Children pruned:</span>
                    <span class="small fw-semibold ms-2">${prunedCount}</span>
                </div>
                <div style="margin-top:6px;padding-top:5px;border-top:1px dashed #e5e7eb;color:#748194;font-size:10px;line-height:1.35">
                    Truncated children whose totals fell below the rendering threshold.
                </div>
            </div>`;
  }

  private static truncatedWeightRow(
    frame: Frame,
    useWeight: boolean,
    eventType: string | null,
    levelTotalWeight: number
  ): string {
    if (!useWeight || eventType == null) {
      return '';
    }
    let label: string;
    if (EventTypes.isAllocationEventType(eventType)) {
      label = 'Pruned allocated';
    } else if (EventTypes.isBlockingEventType(eventType)) {
      label = 'Pruned duration';
    } else {
      return '';
    }
    return `
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">${label}:</span>
                    <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_weight(eventType, frame.totalWeight ?? 0, levelTotalWeight)}</span>
                </div>`;
  }
}
