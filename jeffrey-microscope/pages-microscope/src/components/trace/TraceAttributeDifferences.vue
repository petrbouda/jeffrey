<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi-funnel" title="The selection">
          <template #actions>
            <div class="btn-group btn-group-sm" role="group" aria-label="What to select">
              <button
                v-for="preset in PRESETS"
                :key="preset.id"
                type="button"
                class="btn"
                :class="preset.id === activePreset ? 'btn-primary' : 'btn-outline-secondary'"
                @click="$emit('select', preset.selection(thresholdNanos))"
              >
                {{ preset.label }}
              </button>
            </div>
          </template>
        </MainCardHeader>
      </template>

      <div class="selection">
        <div class="selection-counts">
          <Badge
            key-label="Selection"
            :value="FormattingService.formatNumber(differences.selectionTraces)"
            variant="purple"
            size="s"
            borderless
          />
          <Badge
            key-label="Baseline"
            :value="FormattingService.formatNumber(differences.baselineTraces)"
            variant="secondary"
            size="s"
            borderless
          />
        </div>

        <label v-if="activePreset === 'SLOW'" class="threshold">
          Traces slower than
          <input
            v-model.number="thresholdMillis"
            type="range"
            class="form-range"
            :min="MIN_THRESHOLD_MS"
            :max="MAX_THRESHOLD_MS"
            :step="MIN_THRESHOLD_MS"
            @change="$emit('select', { minDurationNanos: thresholdNanos, errorsOnly: false })"
          />
          <span class="threshold-value">
            {{ FormattingService.formatDuration2Units(thresholdNanos) }}
          </span>
        </label>
      </div>
    </MainCard>

    <EmptyState
      v-if="differences.selectionTraces === 0"
      class="mt-3"
      icon="bi-funnel"
      title="Nothing selected"
      description="No trace matches the selection, so there is nothing to compare against the rest."
    />

    <EmptyState
      v-else-if="differences.differences.length === 0"
      class="mt-3"
      icon="bi-check2-circle"
      title="Nothing stands out"
      description="No attribute is meaningfully more common inside this selection than outside it. Whatever these traces have in common, it is not something the spans recorded."
    />

    <MainCard v-else class="mt-3">
      <template #header>
        <MainCardHeader icon="bi-bar-chart-steps" title="Ranked differences">
          <template #actions>
            <span class="legend">
              <span><i class="swatch selection"></i> in selection</span>
              <span><i class="swatch baseline"></i> in baseline</span>
            </span>
          </template>
        </MainCardHeader>
      </template>

      <div class="ranked">
        <div v-for="row in differences.differences" :key="rowKey(row)" class="rank-row">
          <div class="rank-key" :title="describe(row)">
            <span class="rank-name">{{ row.key }}</span>
            <span class="rank-value">{{ row.value }}</span>
            <span v-if="row.owner" class="rank-owner">{{ row.owner }}</span>
          </div>

          <div class="rank-bars">
            <div class="rank-bar">
              <span class="bar-track">
                <i class="bar-fill selection" :style="{ width: percent(row.selectionShare) }"></i>
              </span>
              <span class="bar-label">
                {{ FormattingService.formatPercentValue(row.selectionShare * 100, 0) }} ·
                {{ FormattingService.formatNumber(row.selectionTraces) }}
              </span>
            </div>
            <div class="rank-bar">
              <span class="bar-track">
                <i class="bar-fill baseline" :style="{ width: percent(row.baselineShare) }"></i>
              </span>
              <span class="bar-label">
                {{ FormattingService.formatPercentValue(row.baselineShare * 100, 0) }} ·
                {{ FormattingService.formatNumber(row.baselineTraces) }}
              </span>
            </div>
          </div>

          <div class="rank-lift">{{ row.lift.toFixed(1) }}×</div>

          <button
            type="button"
            class="btn btn-sm btn-outline-primary rank-action"
            @click="$emit('filter', row)"
          >
            <i class="bi bi-funnel"></i> Filter
          </button>
        </div>
      </div>

      <!--
        The counts sit beside every percentage on purpose: a share of 100% reads as damning until you
        see it is 100% of three traces, and a ranking that shows only the percentage invites exactly
        that mistake.
      -->
      <p class="ranked-note">
        Ranked by how much more of the selection carries a value than of the baseline. Values that
        are no more common inside than outside are left out — a dimension shared equally by both
        explains nothing.
      </p>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import FormattingService from '@shared/services/FormattingService';
import type {
  TraceAttributeDifferenceRow,
  TraceAttributeDifferences
} from '@/services/api/model/trace/TraceAttributeModels';

/** What the caller asked for, so the buttons can show which of them is showing. */
export interface DifferenceSelection {
  minDurationNanos: number;
  errorsOnly: boolean;
}

const props = defineProps<{
  differences: TraceAttributeDifferences;
  selection: DifferenceSelection;
}>();

defineEmits<{
  select: [selection: DifferenceSelection];
  filter: [row: TraceAttributeDifferenceRow];
}>();

const MIN_THRESHOLD_MS = 50;
const MAX_THRESHOLD_MS = 5_000;
const NANOS_PER_MILLI = 1_000_000;
const DEFAULT_THRESHOLD_MS = 500;

const PRESETS = [
  {
    id: 'SLOW',
    label: 'Slowest traces',
    selection: (nanos: number): DifferenceSelection => ({
      minDurationNanos: nanos,
      errorsOnly: false
    })
  },
  {
    id: 'ERRORS',
    label: 'Traces with errors',
    selection: (): DifferenceSelection => ({ minDurationNanos: 0, errorsOnly: true })
  }
];

const thresholdMillis = ref(
  props.selection.minDurationNanos > 0
    ? props.selection.minDurationNanos / NANOS_PER_MILLI
    : DEFAULT_THRESHOLD_MS
);

const thresholdNanos = computed(() => thresholdMillis.value * NANOS_PER_MILLI);

const activePreset = computed(() => (props.selection.errorsOnly ? 'ERRORS' : 'SLOW'));

function rowKey(row: TraceAttributeDifferenceRow): string {
  return `${row.source}~${row.owner ?? ''}~${row.key}~${row.value}`;
}

function percent(share: number): string {
  return `${Math.min(100, share * 100).toFixed(1)}%`;
}

function describe(row: TraceAttributeDifferenceRow): string {
  const owner = row.owner ? `${row.owner} · ` : '';
  return `${owner}${row.key} = ${row.value} — in ${row.selectionTraces} of the selection, ${row.baselineTraces} of the baseline`;
}
</script>

<style scoped>
.selection {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
  flex-wrap: wrap;
}

.selection-counts {
  display: flex;
  gap: var(--spacing-2);
}

.threshold {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  font-size: var(--font-size-base);
  color: var(--color-text-muted);
  flex: 1;
  min-width: 260px;
  margin: 0;
}

.threshold .form-range {
  flex: 1;
  min-width: 120px;
}

.threshold-value {
  font-family: var(--font-family-monospace);
  font-weight: 600;
  color: var(--color-dark);
  min-width: 72px;
}

.legend {
  display: inline-flex;
  gap: var(--spacing-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
}

.swatch {
  width: 9px;
  height: 9px;
  border-radius: var(--radius-xs);
  display: block;
}

.swatch.selection,
.bar-fill.selection {
  background: var(--color-danger);
}

.swatch.baseline,
.bar-fill.baseline {
  background: var(--color-text-light);
}

.ranked {
  display: flex;
  flex-direction: column;
}

.rank-row {
  display: grid;
  grid-template-columns: minmax(180px, 250px) minmax(0, 1fr) 72px auto;
  gap: var(--spacing-3);
  align-items: center;
  padding: var(--spacing-2) 0;
  border-bottom: 1px solid var(--color-border-row);
}

.rank-row:last-child {
  border-bottom: 0;
}

.rank-key {
  min-width: 0;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-base);
}

.rank-name {
  font-weight: 700;
  color: var(--color-dark);
}

.rank-name::after {
  content: ' = ';
  color: var(--color-text-muted);
  font-weight: 400;
}

.rank-value {
  color: var(--color-dark);
}

.rank-owner {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-light);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-bars {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.rank-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.bar-track {
  flex: 1;
  min-width: 0;
  height: 9px;
  background: var(--color-lighter);
  border-radius: var(--radius-xs);
  overflow: hidden;
}

.bar-fill {
  display: block;
  height: 100%;
  border-radius: var(--radius-xs);
}

.bar-label {
  width: 96px;
  flex-shrink: 0;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.rank-lift {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-danger);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.rank-action {
  white-space: nowrap;
}

.ranked-note {
  margin: var(--spacing-3) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
</style>
