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

<!--
  Where a trace's wall-clock time went, ranked.

  The answer the bars cannot give: a span that took 200ms looks the same whether it computed for
  200ms, waited on a lock, or was stopped by a collection, and those are three different problems.
  The remainder is named as own work rather than left as the gap between a total and a list of
  parts -- a breakdown that only names the waiting invites the reader to assume the rest is
  unexplained.
-->
<template>
  <div class="why">
    <div v-for="slice in ranked" :key="slice.category" class="why-row">
      <span class="why-name">
        <i :style="{ background: contextColor(slice.category) }"></i>
        {{ contextLabel(slice.category) }}
      </span>
      <span class="why-track">
        <span
          class="why-fill"
          :style="{ width: share(slice) + '%', background: contextColor(slice.category) }"
        ></span>
      </span>
      <span class="why-value">{{ FormattingService.formatDuration2Units(slice.totalNanos) }}</span>
      <span class="why-percent">{{ percentOf(slice) }}</span>
      <span class="why-count">{{ occurrenceLabel(slice) }}</span>
    </div>

    <p class="why-foot">
      Measured against the trace's own window, not the sum of its spans — spans nest, so summing
      them counts the same instant once per level of the tree.
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import { contextColor, contextLabel } from '@/services/trace/traceLabels';
import type { TraceContextSlice } from '@/services/api/model/trace/TraceModels';

/** Below this a share rounds to 0%, where the duration itself says more than the percentage. */
const MIN_REPORTED_PERCENT = 0.1;

const props = defineProps<{
  slices: TraceContextSlice[];
  /** The trace's end-to-end duration, which the percentages are taken against. */
  traceDurationNanos: number;
}>();

/**
 * Longest first, with anything that came to nothing dropped. A category the JVM recorded zero of is
 * not a finding, and a row of zeroes pushes the ones that matter down the list.
 */
const ranked = computed(() =>
  props.slices
    .filter(slice => slice.totalNanos > 0)
    .sort((left, right) => right.totalNanos - left.totalNanos)
);

/** Scaled to the largest slice, so the ranking is legible even when one category dominates. */
function share(slice: TraceContextSlice): number {
  const widest = ranked.value[0]?.totalNanos ?? 0;
  return widest <= 0 ? 0 : Math.max(1, (slice.totalNanos / widest) * 100);
}

function percentOf(slice: TraceContextSlice): string {
  if (props.traceDurationNanos <= 0) {
    return '—';
  }
  const percent = (slice.totalNanos / props.traceDurationNanos) * 100;
  if (percent < MIN_REPORTED_PERCENT) {
    return '<0.1%';
  }
  return `${percent.toFixed(percent < 10 ? 1 : 0)}%`;
}

/**
 * How many events a total came from. One 90ms stall and nine hundred 100µs ones sum alike and are
 * not the same problem, so the count earns its column wherever there is one to show.
 */
function occurrenceLabel(slice: TraceContextSlice): string {
  if (slice.occurrences <= 0) {
    return '';
  }
  return slice.occurrences === 1
    ? '1 event'
    : `${FormattingService.formatNumber(slice.occurrences)} events`;
}
</script>

<style scoped>
.why {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.75rem 1rem 0.5rem;
}

.why-row {
  display: grid;
  grid-template-columns: 8.5rem 1fr 5rem 3rem 5.5rem;
  align-items: center;
  gap: 0.6rem;
}

.why-name {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: var(--font-size-sm);
  color: var(--color-dark);
  min-width: 0;
}

.why-name i {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: var(--radius-xs);
  flex: none;
}

.why-track {
  position: relative;
  height: 0.55rem;
  background: var(--color-lighter);
  border-radius: var(--radius-xs);
  overflow: hidden;
}

.why-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-xs);
}

.why-value,
.why-percent,
.why-count {
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  font-size: var(--font-size-sm);
  text-align: right;
  white-space: nowrap;
}

.why-value {
  color: var(--color-dark);
  font-weight: 600;
}

.why-percent,
.why-count {
  color: var(--color-text-muted);
}

.why-foot {
  margin: 0.5rem 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}
</style>
