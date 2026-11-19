<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<template>
  <!--
    A run's facts, shown while its stats chip is pressed: labeled figures with room to be read, and
    the durations as a small histogram — the shape of 462 writes, which no single number carries.
    One strip for both kinds of run, so a run the browser rolled up and one the server folded say
    the same things in the same places; the server's adds what only it can know.
  -->
  <div class="wf-run-detail" :style="{ marginLeft: indent, '--span-color': color }">
    <span class="wf-run-stat">
      <span class="wf-run-stat-label">Spans</span>
      <span class="wf-run-stat-value">{{ facts.count.toLocaleString() }}</span>
    </span>
    <span class="wf-run-stat">
      <span class="wf-run-stat-label">Total</span>
      <span class="wf-run-stat-value">{{ duration(facts.totalNanos) }}</span>
    </span>
    <span class="wf-run-stat">
      <span class="wf-run-stat-label">Median</span>
      <span class="wf-run-stat-value">{{ duration(facts.medianNanos) }}</span>
    </span>
    <span class="wf-run-stat">
      <span class="wf-run-stat-label">P95</span>
      <span class="wf-run-stat-value">{{ duration(facts.p95Nanos) }}</span>
    </span>
    <span class="wf-run-stat">
      <span class="wf-run-stat-label">P99</span>
      <span class="wf-run-stat-value">{{ duration(facts.p99Nanos) }}</span>
    </span>
    <span class="wf-run-stat">
      <span class="wf-run-stat-label">Max</span>
      <span class="wf-run-stat-value">{{ duration(facts.maxNanos) }}</span>
    </span>
    <!--
      In the critical-path orange, because it is that rule's number: how much of the trace's length
      this run accounts for, summed over every member the server walked the path through.
    -->
    <span v-if="facts.criticalPathNanos !== null" class="wf-run-stat">
      <span class="wf-run-stat-label">On critical path</span>
      <span class="wf-run-stat-value is-critical">{{ duration(facts.criticalPathNanos) }}</span>
    </span>
    <span class="wf-run-histogram-wrap">
      <span class="wf-run-histogram" :title="histogramTitle">
        <i
          v-for="(bucket, index) in facts.histogram"
          :key="index"
          :class="{ hot: bucket.height === 1 }"
          :style="{ height: 4 + bucket.height * 26 + 'px' }"
          :title="bucketTitle(bucket)"
        ></i>
      </span>
      <!--
        Only under a log-scaled histogram: there the bars' heights cannot be read as counts, and a
        reader who does not know that sees a run with no fast majority.
      -->
      <span v-if="facts.logScale" class="wf-run-histogram-axis">
        <span>{{ duration(facts.minNanos) }}</span>
        <span>log scale</span>
        <span>{{ duration(facts.maxNanos) }}</span>
      </span>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import type { RunFacts, RunHistogramBucket } from '@/services/trace/traceRuns';

const props = defineProps<{
  facts: RunFacts;
  /** The run's span colour, which the histogram's bars wear. */
  color: string;
  /** Where the strip starts, so it sits under the name of the run it belongs to. */
  indent: string;
}>();

const histogramTitle = computed(() =>
  props.facts.logScale
    ? "How the run's durations are distributed, fastest on the left, slowest on the right; heights on a log scale"
    : "How the run's durations are distributed, fastest on the left, slowest on the right"
);

function duration(nanos: number): string {
  return FormattingService.formatDuration2Units(nanos);
}

/** One bar, said in words: which slice of durations it covers and how many spans landed in it. */
function bucketTitle(bucket: RunHistogramBucket): string {
  const spans = bucket.count === 1 ? '1 span' : `${bucket.count.toLocaleString()} spans`;
  return `${duration(bucket.fromNanos)} – ${duration(bucket.toNanos)}: ${spans}`;
}
</script>

<style scoped>
.wf-run-detail {
  display: flex;
  align-items: center;
  gap: var(--spacing-6);
  flex-wrap: wrap;
  /* The left margin comes inline, per row — it follows the run's own indent depth. */
  margin: var(--spacing-1) var(--spacing-4) var(--spacing-2) 0;
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-light);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
}

.wf-run-stat {
  display: flex;
  flex-direction: column;
}

.wf-run-stat-label {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.wf-run-stat-value {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  font-variant-numeric: tabular-nums;
  color: var(--color-dark);
}

.wf-run-stat-value.is-critical {
  color: var(--color-warning-hover);
}

.wf-run-histogram-wrap {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
}

.wf-run-histogram {
  display: inline-flex;
  align-items: flex-end;
  gap: 3px;
  height: 30px;
}

.wf-run-histogram i {
  width: 22px;
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
  background: var(--span-color);
  opacity: 0.35;
}

.wf-run-histogram i.hot {
  opacity: 1;
}

.wf-run-histogram-axis {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-2);
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
}
</style>
