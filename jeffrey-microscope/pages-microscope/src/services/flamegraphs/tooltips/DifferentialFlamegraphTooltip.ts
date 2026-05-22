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

import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import Frame from '@/services/api/model/Frame';
import FrameType from '@/services/flamegraphs/FrameType';

const COLOR_IMPROVED = '#059669';
const COLOR_REGRESSED = '#e63757';
const COLOR_BORDER = '#eaedf1';
const COLOR_DELIMITER = '#cbd5e1';

const PRIMARY_GRADIENT = 'linear-gradient(90deg, #5e64ff, #4c52db)';
const BASELINE_GRADIENT = 'linear-gradient(90deg, #cbd5e1, #94a3b8)';

interface DeltaStyle {
  label: string;
  color: string;
  value: string;
  pct: string | null;
}

export default class DifferentialFlamegraphTooltip extends FlamegraphTooltip {
  constructor(eventType: string, useWeight: boolean) {
    super(eventType, useWeight);
  }

  generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string {
    if (frame.type === FrameType.TRUNCATED_SYNTHETIC) {
      return FlamegraphTooltip.truncated(
        frame,
        levelTotalSamples,
        this.useWeight,
        this.eventType,
        levelTotalWeight
      );
    }

    const details = frame.diffDetails!;

    const delta = this.useWeight ? details.weight : details.samples;
    const baseline = this.useWeight ? details.secondaryWeight : details.secondarySamples;
    const primary = baseline + delta;
    const percent = this.useWeight ? details.percentWeight : details.percentSamples;

    const max = Math.max(primary, baseline) || 1;
    const baselineWidth = (baseline / max) * 100;
    const primaryWidth = (primary / max) * 100;

    const deltaStyle = this.resolveDelta(delta, primary, baseline, percent);

    return `
            ${FlamegraphTooltip.header(frame)}
            <div style="padding:12px 14px 12px">
                ${this.barRow('Baseline', baseline, baselineWidth, BASELINE_GRADIENT)}
                ${this.barRow('Primary', primary, primaryWidth, PRIMARY_GRADIENT)}
                ${deltaStyle ? this.deltaLine(deltaStyle) : ''}
            </div>`;
  }

  private barRow(role: string, value: number, widthPct: number, gradient: string): string {
    const formatted =
      value === 0 ? '<span style="color:#748194">—</span>' : this.formatValue(value);
    return `
        <div style="display:flex;align-items:center;gap:8px;margin:6px 0">
            <span style="width:56px;font-size:10px;font-weight:700;color:#748194;text-transform:uppercase;letter-spacing:0.06em">${role}</span>
            <div style="flex:1;height:8px;background:#f9fafb;border:1px solid #eaedf1;border-radius:4px;position:relative;overflow:hidden">
                <div style="position:absolute;inset:0 auto 0 0;width:${widthPct.toFixed(2)}%;background:${gradient};border-radius:4px"></div>
            </div>
            <span style="min-width:88px;text-align:right;font-variant-numeric:tabular-nums;font-weight:700;font-size:13px;color:#0b1727">${formatted}</span>
        </div>`;
  }

  private deltaLine(style: DeltaStyle): string {
    const pctSegment = style.pct
      ? `<span style="margin:0 8px;color:${COLOR_DELIMITER};font-weight:400">·</span><span style="font-weight:700;font-size:13px;color:${style.color}">${style.pct}</span>`
      : '';
    return `
        <div style="margin-top:10px;padding-top:8px;border-top:1px solid ${COLOR_BORDER};display:flex;align-items:center;justify-content:space-between;font-variant-numeric:tabular-nums">
            <span style="font-size:11px;font-weight:700;color:${style.color};letter-spacing:0.06em;text-transform:uppercase">${style.label}</span>
            <span><span style="font-weight:700;font-size:13px;color:${style.color}">${style.value}</span>${pctSegment}</span>
        </div>`;
  }

  private resolveDelta(
    delta: number,
    primary: number,
    baseline: number,
    percent: number
  ): DeltaStyle | null {
    const isAdded = baseline === 0 && primary > 0;
    const isRemoved = primary === 0 && baseline > 0;

    if (isAdded) {
      return {
        label: 'NEW',
        color: COLOR_REGRESSED,
        value: `+${this.formatValue(primary)}`,
        pct: null
      };
    }
    if (isRemoved) {
      return {
        label: 'REMOVED',
        color: COLOR_IMPROVED,
        value: `−${this.formatValue(baseline)}`,
        pct: null
      };
    }
    if (delta === 0) {
      return null;
    }
    const improved = delta < 0;
    const sign = delta > 0 ? '+' : '−';
    const pctSign = percent > 0 ? '+' : '−';
    const pctText = Number.isFinite(percent) ? `${pctSign}${Math.abs(percent).toFixed(2)}%` : null;
    return {
      label: improved ? 'IMPROVED' : 'REGRESSED',
      color: improved ? COLOR_IMPROVED : COLOR_REGRESSED,
      value: `${sign}${this.formatValue(Math.abs(delta))}`,
      pct: pctText
    };
  }

  private formatValue(value: number): string {
    if (this.useWeight) {
      const formatted = FlamegraphTooltip.format_value_weight(this.eventType, value);
      return typeof formatted === 'number' ? formatted.toLocaleString() : formatted;
    }
    return value.toLocaleString();
  }
}
