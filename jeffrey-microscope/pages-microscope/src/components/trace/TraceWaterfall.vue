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
  <div class="waterfall">
    <div class="wf-head">
      <span>Span</span>
      <span class="wf-scale">
        <span>0</span>
        <span>{{ FormattingService.formatDuration2Units(windowNanos) }}</span>
      </span>
      <span class="wf-duration">Duration</span>
    </div>

    <button
      v-for="span in spans"
      :key="span.spanId"
      type="button"
      class="wf-row"
      :class="{ selected: span.spanId === selectedSpanId }"
      @click="$emit('select', span)"
    >
      <span class="wf-name">
        <span class="wf-indent" :style="{ width: indentRem(span.depth) + 'rem' }"></span>
        <span class="wf-kind" :class="kindClass(span)"></span>
        <span class="wf-label" :title="span.name">{{ span.name }}</span>
        <Badge v-if="span.status === 'ERROR'" variant="danger" size="xs" value="error" />
      </span>

      <span class="wf-track">
        <span
          class="wf-bar"
          :class="barClass(span)"
          :style="barStyle(span)"
          :title="tooltip(span)"
        >
          <span class="wf-self" :style="{ width: bar(span).selfPercent + '%' }"></span>
        </span>
      </span>

      <span class="wf-duration">{{ FormattingService.formatDuration2Units(span.durationNanos) }}</span>
    </button>

    <div class="wf-legend">
      <span><i class="swatch swatch-self"></i> self time</span>
      <span><i class="swatch swatch-children"></i> time in children</span>
      <span><i class="swatch swatch-server"></i> server</span>
      <span><i class="swatch swatch-client"></i> client</span>
      <span><i class="swatch swatch-internal"></i> internal</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import type { TraceSpanRow } from '@/services/api/model/trace/TraceModels';
import { indentRem, spanBar, traceWindow } from '@/services/trace/TraceWaterfallLayout';

const props = defineProps<{
  spans: TraceSpanRow[];
  selectedSpanId?: string | null;
}>();

defineEmits<{ (event: 'select', span: TraceSpanRow): void }>();

const NANOS_PER_MILLI = 1_000_000;

const window = computed(() => traceWindow(props.spans));

const windowNanos = computed(
  () => (window.value.endMillis - window.value.startMillis) * NANOS_PER_MILLI
);

function bar(span: TraceSpanRow) {
  return spanBar(span, window.value);
}

function barStyle(span: TraceSpanRow) {
  const geometry = bar(span);
  return { left: geometry.leftPercent + '%', width: geometry.widthPercent + '%' };
}

function barClass(span: TraceSpanRow): string {
  if (span.status === 'ERROR') {
    return 'bar-error';
  }
  return 'bar-' + span.kind.toLowerCase();
}

function kindClass(span: TraceSpanRow): string {
  return 'kind-' + span.kind.toLowerCase();
}

/**
 * Self time is the number worth surfacing on hover: the duration is already in its own column,
 * so repeating it would say nothing the row does not already show.
 */
function tooltip(span: TraceSpanRow): string {
  const total = FormattingService.formatDuration2Units(span.durationNanos);
  const self = FormattingService.formatDuration2Units(span.selfDurationNanos);
  const thread = span.threadName ? ` · ${span.threadName}` : '';
  return `${span.name} — ${total} total, ${self} self${thread}`;
}
</script>

<style scoped>
.waterfall {
  display: flex;
  flex-direction: column;
}

.wf-head,
.wf-row {
  display: grid;
  grid-template-columns: 20rem 1fr 5.5rem;
  align-items: center;
  gap: 0.5rem;
}

.wf-head {
  padding: 0.45rem 1rem;
  background: var(--color-bg-hover);
  border-bottom: 1px solid var(--color-border);
  font-size: var(--font-size-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  font-weight: 600;
}

.wf-scale {
  display: flex;
  justify-content: space-between;
  font-variant-numeric: tabular-nums;
}

.wf-row {
  width: 100%;
  padding: 0.28rem 1rem;
  border: 0;
  border-bottom: 1px solid var(--color-border-row);
  background: transparent;
  font-family: inherit;
  font-size: var(--font-size-sm);
  text-align: left;
  cursor: pointer;
}

.wf-row:hover {
  background: var(--color-bg-hover-alt);
}

.wf-row.selected {
  background: var(--color-primary-light);
}

.wf-row:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.wf-name {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.wf-indent {
  flex: none;
}

.wf-kind {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: var(--radius-circle);
  flex: none;
}

.kind-server {
  background: var(--color-primary);
}

.kind-client {
  background: var(--color-info);
}

.kind-internal {
  background: var(--color-secondary);
}

.wf-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-heading);
}

/* The track a bar is positioned inside, by percentage of the trace window. */
.wf-track {
  position: relative;
  height: 1.1rem;
}

.wf-track::before {
  content: '';
  position: absolute;
  inset: 0.5rem 0 auto 0;
  height: 1px;
  background: var(--color-border-light);
}

.wf-bar {
  position: absolute;
  top: 0.15rem;
  height: 0.8rem;
  border-radius: var(--radius-xs);
  overflow: hidden;
  display: block;
}

/*
 * The pale body is the whole span; the solid head is its own work. Reading the two together
 * answers "where did this span's time go" without opening it.
 */
.wf-self {
  display: block;
  height: 100%;
  border-radius: var(--radius-xs) 0 0 var(--radius-xs);
}

.bar-server {
  background: color-mix(in srgb, var(--flamegraph-color-blue) 35%, transparent);
}

.bar-server .wf-self {
  background: var(--flamegraph-color-blue);
}

.bar-client {
  background: color-mix(in srgb, var(--flamegraph-color-cyan) 35%, transparent);
}

.bar-client .wf-self {
  background: var(--flamegraph-color-cyan);
}

.bar-internal {
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
}

.bar-internal .wf-self {
  background: var(--flamegraph-color-green);
}

.bar-error {
  background: var(--color-danger-light);
}

.bar-error .wf-self {
  background: var(--flamegraph-color-red);
}

.wf-duration {
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
  color: var(--color-text);
}

.wf-legend {
  display: flex;
  gap: 0.9rem;
  flex-wrap: wrap;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  padding: 0.6rem 1rem;
  border-top: 1px solid var(--color-border-light);
}

.wf-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.swatch {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: var(--radius-xs);
  display: inline-block;
}

.swatch-self {
  background: var(--flamegraph-color-green);
}

.swatch-children {
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
}

.swatch-server {
  background: var(--color-primary);
}

.swatch-client {
  background: var(--color-info);
}

.swatch-internal {
  background: var(--color-secondary);
}
</style>
