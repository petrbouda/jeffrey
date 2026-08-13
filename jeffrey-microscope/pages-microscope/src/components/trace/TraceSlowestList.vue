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
  <div v-if="sortedTraces.length > 0">
    <SlowestCountHeader :shown="sortedTraces.length" :total="traces.length" />
    <div class="slowest-list">
      <div
        v-for="trace in sortedTraces"
        :key="trace.traceId"
        class="slowest-row"
        @click="$emit('rowClick', trace)"
      >
        <!-- The gutter carries the outcome: a failed trace is visible before anything is read. -->
        <div class="left-accent" :class="accentClass(trace)"></div>
        <div class="row-content">
          <div class="row-header">
            <div class="row-header-left">
              <div class="group-text" :title="trace.rootName">
                <i class="bi bi-diagram-3 root-icon"></i> {{ trace.rootName }}
              </div>
            </div>
            <div class="time-bar-wrap">
              <span class="time-bar-value">{{
                FormattingService.formatDuration2Units(trace.durationNanos)
              }}</span>
              <div class="time-bar-track">
                <div
                  class="time-bar-fill"
                  :class="{ 'has-error': trace.errorCount > 0 }"
                  :style="{ width: timePercentage(trace.durationNanos) + '%' }"
                ></div>
              </div>
            </div>
          </div>
          <div class="row-details">
            <Badge
              :value="trace.rootKind"
              :variant="kindVariant(trace.rootKind)"
              size="s"
              icon="bi bi-diagram-2"
            />
            <Badge
              v-if="trace.errorCount > 0"
              :value="errorLabel(trace.errorCount)"
              variant="danger"
              size="s"
              icon="bi bi-exclamation-triangle"
              :uppercase="false"
            />
            <span class="detail-chip">
              <i class="bi bi-bounding-box"></i> {{ trace.spanCount }} spans
            </span>
            <span class="detail-chip">
              <i class="bi bi-clock"></i>
              {{ FormattingService.formatTimestamp(trace.startEpochMillis).replace('T', ' ') }}
            </span>
            <span class="detail-chip trace-id-chip">
              <i class="bi bi-hash"></i> {{ trace.traceId }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="slowest-empty">No traces for this filter.</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import SlowestCountHeader from '@/components/SlowestCountHeader.vue';
import { errorLabel } from '@/services/trace/traceLabels';
import type { SpanKind, TraceRow } from '@/services/api/model/trace/TraceModels';

const DISPLAY_LIMIT = 50;

const props = defineProps<{
  traces: TraceRow[];
}>();

defineEmits<{
  rowClick: [trace: TraceRow];
}>();

const sortedTraces = computed(() =>
  [...props.traces].sort((a, b) => b.durationNanos - a.durationNanos).slice(0, DISPLAY_LIMIT)
);

// Scaled to the slowest trace in the whole list, not just the displayed page, so the bars keep
// meaning when the list is truncated.
const maxDuration = computed(() => {
  if (props.traces.length === 0) {
    return 1;
  }
  return Math.max(...props.traces.map(trace => trace.durationNanos));
});

function timePercentage(durationNanos: number): number {
  return Math.max((durationNanos / maxDuration.value) * 100, 2);
}

function accentClass(trace: TraceRow): string {
  if (trace.errorCount > 0) {
    return 'accent-error';
  }
  return 'accent-' + trace.rootKind.toLowerCase();
}

function kindVariant(kind: SpanKind): string {
  if (kind === 'SERVER') {
    return 'primary';
  }
  return kind === 'CLIENT' ? 'info' : 'secondary';
}
</script>

<style scoped>
.slowest-list {
  padding: 0.5rem 1rem;
}

.slowest-row {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid var(--color-border-light);
  padding: 0.75rem 0;
  cursor: pointer;
}

.slowest-row:last-child {
  border-bottom: none;
}

.slowest-row:hover {
  background: var(--color-bg-hover);
}

.left-accent {
  width: 3px;
  border-radius: var(--radius-xs);
  flex-shrink: 0;
  margin-right: 1rem;
  background: var(--color-border-light);
}

.accent-error {
  background: var(--color-danger);
}

.accent-server {
  background: var(--color-primary);
}

.accent-client {
  background: var(--color-info);
}

.accent-internal {
  background: var(--color-secondary);
}

.row-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.row-header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  flex: 1;
}

.group-text {
  font-family: var(--font-family-monospace);
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.root-icon {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-right: 0.15rem;
}

.time-bar-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
  min-width: 120px;
  flex-shrink: 0;
}

.time-bar-track {
  width: 100%;
  height: 6px;
  background: var(--color-lighter);
  border-radius: var(--radius-xs);
  overflow: hidden;
}

.time-bar-fill {
  height: 100%;
  border-radius: var(--radius-xs);
  background: linear-gradient(90deg, var(--color-primary), var(--color-secondary));
}

.time-bar-fill.has-error {
  background: linear-gradient(90deg, var(--color-danger), var(--color-warning));
}

.time-bar-value {
  font-family: var(--font-family-monospace);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-dark);
  min-width: 70px;
  text-align: right;
}

.row-details {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.detail-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-family: var(--font-family-base);
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--color-text-muted);
  letter-spacing: 0.01em;
}

.detail-chip i {
  font-size: 0.6rem;
  opacity: 0.7;
}

/* The id is the one thing here worth copying out, so it keeps the monospace treatment. */
.trace-id-chip {
  font-family: var(--font-family-monospace);
  color: var(--color-text-light);
}

.slowest-empty {
  font-size: 0.85rem;
  color: var(--color-text-muted);
  padding: 1rem;
}

@media (max-width: 768px) {
  .row-header {
    flex-direction: column;
    align-items: stretch;
    gap: 0.5rem;
  }

  .time-bar-wrap {
    min-width: 0;
  }
}
</style>
