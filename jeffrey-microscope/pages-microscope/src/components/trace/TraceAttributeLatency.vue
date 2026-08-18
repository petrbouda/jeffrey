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
    <EmptyState
      v-if="rows.length === 0"
      icon="bi-bar-chart"
      title="Nothing to distribute"
      description="No trace carries this key, so there is no duration distribution to draw."
    />

    <template v-else>
      <MainCard>
        <template #header>
          <MainCardHeader icon="bi-grid-3x3" title="Trace duration by value">
            <template #actions>
              <span class="scale">
                few
                <span class="scale-swatches">
                  <i
                    v-for="step in SCALE_STEPS"
                    :key="step"
                    :style="{ background: shade(step / (SCALE_STEPS - 1)) }"
                  ></i>
                </span>
                most of the row
              </span>
            </template>
          </MainCardHeader>
        </template>

        <div class="heatmap-scroll">
          <div class="heatmap" :style="{ '--bucket-count': buckets.length }">
            <div class="heat-row heat-head">
              <span class="heat-label"></span>
              <span v-for="bucket in buckets" :key="bucket" class="heat-cell heat-axis">
                {{ bucketLabel(bucket) }}
              </span>
            </div>

            <div v-for="row in rows" :key="row.value" class="heat-row">
              <span class="heat-label" :title="row.value">{{ row.value }}</span>
              <span
                v-for="bucket in buckets"
                :key="bucket"
                class="heat-cell"
                :style="cellStyle(row, bucket)"
                :title="cellTitle(row, bucket)"
              >
                {{ countOf(row, bucket) || '' }}
              </span>
            </div>
          </div>
        </div>

        <!--
          The colour is normalised inside each row, not across the grid. Absolute counts would make
          the busiest value the only visible row, and the question here is the *shape* of each
          value's distribution rather than which of them ran most.
        -->
        <p class="heatmap-note">
          Buckets are log-spaced trace durations. Colour is each value's own busiest bucket, so rows
          are comparable in shape even where one of them ran ten times as often.
        </p>
      </MainCard>

      <MainCard v-if="bimodal.length > 0" class="mt-3">
        <template #header>
          <MainCardHeader icon="bi-exclamation-triangle" title="Worth a look" />
        </template>
        <ul class="findings">
          <li v-for="row in bimodal" :key="row.value">
            <span class="mono">{{ row.value }}</span> is spread over {{ spread(row) }} of duration,
            with a gap in the middle — one value with two populations behind it, which a median
            cannot show.
          </li>
        </ul>
      </MainCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import EmptyState from '@shared/components/EmptyState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import FormattingService from '@shared/services/FormattingService';
import {
  bucketLowerNanos,
  type TraceAttributeLatency
} from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{
  latency: TraceAttributeLatency;
}>();

/** Steps in the colour ramp shown in the legend; the cells themselves are continuous. */
const SCALE_STEPS = 7;

/**
 * How faint the emptiest populated cell may get. Below this a cell with one trace in it is
 * indistinguishable from an empty one, which is the difference between "rare" and "never".
 */
const MIN_SHADE_PERCENT = 12;
const MAX_SHADE_PERCENT = 100;

/** Above this share of the row's peak, the count is legible only in white. */
const INVERT_TEXT_ABOVE = 0.55;

/** A value is worth calling out when its traces sit in two clusters with an empty gap between. */
const BIMODAL_GAP_BUCKETS = 2;

interface HeatRow {
  value: string;
  counts: Map<number, number>;
  peak: number;
  occupied: number[];
}

const buckets = computed(() => {
  const range: number[] = [];
  for (let bucket = props.latency.minBucket; bucket <= props.latency.maxBucket; bucket++) {
    range.push(bucket);
  }
  return range;
});

const rows = computed<HeatRow[]>(() => {
  const byValue = new Map<string, HeatRow>();

  for (const cell of props.latency.cells) {
    let row = byValue.get(cell.value);
    if (row === undefined) {
      row = { value: cell.value, counts: new Map(), peak: 0, occupied: [] };
      byValue.set(cell.value, row);
    }
    row.counts.set(cell.bucket, cell.traceCount);
    row.peak = Math.max(row.peak, cell.traceCount);
    row.occupied.push(cell.bucket);
  }

  for (const row of byValue.values()) {
    row.occupied.sort((left, right) => left - right);
  }
  return [...byValue.values()];
});

/** The values whose traces fall into two separated clusters rather than one. */
const bimodal = computed(() =>
  rows.value.filter(row => {
    for (let index = 1; index < row.occupied.length; index++) {
      if (row.occupied[index] - row.occupied[index - 1] > BIMODAL_GAP_BUCKETS) {
        return true;
      }
    }
    return false;
  })
);

function countOf(row: HeatRow, bucket: number): number {
  return row.counts.get(bucket) ?? 0;
}

/**
 * A step of the sequential ramp, mixed from the brand hue rather than written as a literal — one
 * hue light to dark, which is what a magnitude scale has to be.
 */
function shade(intensity: number): string {
  const percent = MIN_SHADE_PERCENT + intensity * (MAX_SHADE_PERCENT - MIN_SHADE_PERCENT);
  return `color-mix(in srgb, var(--color-primary) ${percent.toFixed(0)}%, var(--color-white))`;
}

function cellStyle(row: HeatRow, bucket: number): Record<string, string> {
  const count = countOf(row, bucket);
  if (count === 0) {
    return { background: 'var(--color-light)', color: 'var(--color-text-light)' };
  }
  const intensity = row.peak === 0 ? 0 : count / row.peak;
  return {
    background: shade(intensity),
    color: intensity > INVERT_TEXT_ABOVE ? 'var(--color-white)' : 'var(--color-text)'
  };
}

function bucketLabel(bucket: number): string {
  return FormattingService.formatDuration2Units(bucketLowerNanos(bucket));
}

function cellTitle(row: HeatRow, bucket: number): string {
  const from = bucketLabel(bucket);
  const to = bucketLabel(bucket + 1);
  const count = countOf(row, bucket);
  return `${row.value} · ${from}–${to} · ${FormattingService.formatNumber(count)} traces`;
}

function spread(row: HeatRow): string {
  const first = row.occupied[0];
  const last = row.occupied[row.occupied.length - 1];
  return `${bucketLabel(first)} to ${bucketLabel(last + 1)}`;
}
</script>

<style scoped>
.heatmap-scroll {
  overflow-x: auto;
}

.heatmap {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 640px;
}

.heat-row {
  display: grid;
  grid-template-columns: 150px repeat(var(--bucket-count), minmax(52px, 1fr));
  gap: 3px;
  align-items: center;
}

.heat-label {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  color: var(--color-dark);
  text-align: right;
  padding-right: var(--spacing-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.heat-cell {
  height: 26px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}

.heat-axis {
  height: auto;
  background: transparent;
  color: var(--color-text-light);
  font-size: var(--font-size-xs);
}

.scale {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.scale-swatches {
  display: inline-flex;
}

.scale-swatches i {
  width: 16px;
  height: 9px;
  display: block;
}

.scale-swatches i:first-child {
  border-radius: var(--radius-xs) 0 0 var(--radius-xs);
}

.scale-swatches i:last-child {
  border-radius: 0 var(--radius-xs) var(--radius-xs) 0;
}

.heatmap-note {
  margin: var(--spacing-3) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.findings {
  margin: 0;
  padding-left: var(--spacing-5);
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.findings li + li {
  margin-top: var(--spacing-2);
}

.mono {
  font-family: var(--font-family-monospace);
  font-weight: 600;
  color: var(--color-dark);
}
</style>
