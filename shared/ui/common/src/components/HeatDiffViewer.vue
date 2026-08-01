<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<!--
  A unified diff with the measurement in the gutter.

  The heat marks are not a per-line histogram — Jeffrey does not have one, because the call tree is
  keyed by method rather than by line. A mark appears only where a line IS a cited hotspot that was
  checked against the measured profile, and it carries that frame's real share. Everything else is
  left blank rather than shaded a plausible colour: invented precision in a profiler is worse than
  no precision at all.
-->

<template>
  <div class="heat-diff">
    <div class="hd-bar">
      <span v-if="peak" class="hd-cost" :title="peakTitle">
        {{ FormattingService.formatPercentage(peak.pct) }}
        <small>{{ costLabel }}</small>
      </span>
      <span class="hd-files">
        <span v-for="file in parsed.files" :key="file.path" class="hd-file" :title="file.path">
          <i class="bi bi-file-earmark-diff"></i>
          {{ shortPath(file.path) }}
        </span>
      </span>
      <span class="hd-stat">
        <span class="plus">+{{ parsed.added }}</span>
        <span class="minus">−{{ parsed.removed }}</span>
      </span>
      <button type="button" class="hd-btn" @click="copy">
        <i class="bi" :class="copied ? 'bi-check-lg' : 'bi-clipboard'"></i>
        {{ copied ? 'Copied' : 'Copy' }}
      </button>
      <button type="button" class="hd-btn primary" @click="download">
        <i class="bi bi-download"></i>
        Save .patch
      </button>
    </div>

    <div class="hd-body">
      <div v-for="(line, index) in parsed.lines" :key="index" class="dl" :class="line.type">
        <span class="dl-heat" :title="heatTitle(line)">
          <template v-if="heatOf(line) !== null">
            <span class="heat-bar" :style="{ width: heatWidth(heatOf(line)!) }"></span>
            <span class="heat-pct">{{ heatOf(line)!.toFixed(1) }}</span>
          </template>
        </span>
        <span class="dl-num">{{ lineNumberOf(line) }}</span>
        <span class="dl-mark">{{ markerOf(line.type) }}</span>
        <span class="dl-code">{{ line.text }}</span>
      </div>
    </div>

    <p class="hd-legend">
      <span class="ramp"></span>
      Marked lines are cited hotspots checked against the measured profile — the figure is that
      frame's share of it. Unmarked lines were not cited; Jeffrey measures per method, not per line.
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import {
  parseUnifiedDiff,
  sameFile,
  type DiffLine,
  type HeatMark
} from '@shared/services/unifiedDiff';

const props = withDefaults(
  defineProps<{
    patch: string;
    marks?: HeatMark[];
    /** What the percentage measures, e.g. "of blocked time". */
    costLabel?: string;
    /** Basename for the downloaded file, without the .patch suffix. */
    fileName?: string;
  }>(),
  { marks: () => [], costLabel: 'of this profile', fileName: 'advisor' }
);

/** Full width of the heat bar in pixels, reached at HEAT_FULL_PCT and above. */
const HEAT_BAR_MAX_PX = 26;
const HEAT_FULL_PCT = 60;
const PATH_SEGMENTS_SHOWN = 2;

const copied = ref(false);

const parsed = computed(() => parseUnifiedDiff(props.patch));

/** The heaviest mark the patch actually touches — the headline number. */
const peak = computed(() => {
  const touched = props.marks.filter(mark =>
    parsed.value.files.some(file => sameFile(file.path, mark.path))
  );
  return touched.reduce<HeatMark | null>(
    (best, mark) => (best === null || mark.pct > best.pct ? mark : best),
    null
  );
});

const peakTitle = computed(() =>
  peak.value ? `${peak.value.frame} — measured share of this profile` : ''
);

/**
 * A line's measured share, or null when it is not a cited hotspot. A claim that named only a file
 * marks the removed lines of that file: what is being taken out is what was measured.
 */
function heatOf(line: DiffLine): number | null {
  if (line.type !== 'del' && line.type !== 'ctx') {
    return null;
  }
  for (const mark of props.marks) {
    if (!sameFile(line.file, mark.path)) {
      continue;
    }
    if (mark.line === null) {
      if (line.type === 'del') {
        return mark.pct;
      }
      continue;
    }
    if (mark.line === line.oldLine) {
      return mark.pct;
    }
  }
  return null;
}

function heatTitle(line: DiffLine): string {
  const pct = heatOf(line);
  if (pct === null) {
    return '';
  }
  const mark = props.marks.find(entry => sameFile(line.file, entry.path) && entry.pct === pct);
  return mark ? `${mark.frame} — ${FormattingService.formatPercentage(pct)} ${props.costLabel}` : '';
}

function heatWidth(pct: number): string {
  const clamped = Math.min(pct, HEAT_FULL_PCT) / HEAT_FULL_PCT;
  return `${Math.max(3, Math.round(clamped * HEAT_BAR_MAX_PX))}px`;
}

function lineNumberOf(line: DiffLine): string {
  if (line.type === 'hunk' || line.type === 'meta') {
    return '';
  }
  const number = line.type === 'del' ? line.oldLine : line.newLine;
  return number === null ? '' : String(number);
}

function markerOf(type: DiffLine['type']): string {
  if (type === 'add') {
    return '+';
  }
  if (type === 'del') {
    return '−';
  }
  return ' ';
}

function shortPath(path: string): string {
  const segments = path.split('/');
  return segments.slice(-PATH_SEGMENTS_SHOWN).join('/');
}

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.patch);
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, 1500);
  } catch {
    ToastService.error('Patch', 'The browser blocked access to the clipboard');
  }
}

function download(): void {
  const blob = new Blob([props.patch], { type: 'text/x-patch' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${props.fileName}.patch`;
  anchor.click();
  URL.revokeObjectURL(url);
}
</script>

<style scoped>
.heat-diff {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-card);
}

.hd-bar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  padding: 0.6rem 0.7rem;
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

/* The prize, stated as a number. Warm-to-hot so it reads as cost, not as a brand accent. */
.hd-cost {
  display: inline-flex;
  align-items: baseline;
  gap: 0.3rem;
  background: linear-gradient(135deg, var(--color-danger), var(--color-warning));
  color: var(--color-white);
  border-radius: var(--radius-base);
  padding: 0.24rem 0.55rem;
  font-weight: 700;
  font-size: 0.88rem;
  letter-spacing: -0.01em;
}

.hd-cost small {
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  opacity: 0.9;
}

.hd-files {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.hd-file {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-family: var(--font-family-monospace);
  font-size: 0.73rem;
  color: var(--color-primary);
}

.hd-stat {
  font-family: var(--font-family-monospace);
  font-size: 0.73rem;
  display: inline-flex;
  gap: 0.35rem;
}

.hd-stat .plus {
  color: var(--color-success-dark);
  font-weight: 600;
}

.hd-stat .minus {
  color: var(--color-danger-dark);
  font-weight: 600;
}

.hd-btn {
  appearance: none;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.74rem;
  font-weight: 600;
  border-radius: var(--radius-base);
  border: 1px solid var(--color-primary-border-light);
  background: transparent;
  color: var(--color-primary);
  padding: 0.28rem 0.6rem;
  display: inline-flex;
  align-items: center;
  gap: 0.32rem;
  white-space: nowrap;
  transition: background 0.15s;
}

.hd-btn:hover {
  background: var(--color-primary-light);
}

.hd-btn.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.hd-btn.primary:hover {
  background: var(--color-primary-hover);
}

.hd-body {
  font-family: var(--font-family-monospace);
  font-size: 0.76rem;
  line-height: 1.65;
  overflow-x: auto;
}

.dl {
  display: flex;
  align-items: stretch;
  white-space: pre;
}

.dl-heat {
  width: 3.4rem;
  flex: none;
  display: flex;
  align-items: center;
  gap: 0.28rem;
  padding: 0 0.4rem;
  border-right: 1px solid var(--color-border-light);
  user-select: none;
}

.heat-bar {
  height: 7px;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--color-warning), var(--color-danger));
  flex: none;
}

.heat-pct {
  font-size: 0.62rem;
  color: var(--color-text-muted);
}

.dl-num {
  width: 3.1rem;
  flex: none;
  text-align: right;
  padding: 0 0.5rem;
  color: var(--color-text-muted);
  background: var(--color-light);
  border-right: 1px solid var(--color-border-light);
  user-select: none;
  font-size: 0.7rem;
  opacity: 0.75;
}

.dl-mark {
  width: 1.15rem;
  flex: none;
  text-align: center;
  user-select: none;
  font-weight: 700;
}

.dl-code {
  flex: 1;
  padding-right: 0.75rem;
}

.dl.add {
  background: var(--color-success-light);
}

.dl.add .dl-mark {
  color: var(--color-success-dark);
}

.dl.del {
  background: var(--color-danger-light);
}

.dl.del .dl-mark {
  color: var(--color-danger-dark);
}

.dl.hunk,
.dl.meta {
  background: var(--color-light);
  color: var(--color-text-muted);
  font-size: 0.7rem;
}

.dl.hunk {
  border-top: 1px solid var(--color-border-light);
  border-bottom: 1px solid var(--color-border-light);
}

.hd-legend {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  padding: 0.55rem 0.7rem;
  border-top: 1px solid var(--color-border);
  font-size: 0.7rem;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.hd-legend .ramp {
  width: 54px;
  height: 7px;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--color-border), var(--color-warning), var(--color-danger));
  flex: none;
}
</style>
