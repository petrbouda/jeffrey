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
  <div v-if="sortedSpans.length > 0">
    <SlowestCountHeader :shown="sortedSpans.length" :total="spans.length" />
    <div class="slowest-list">
      <div
        v-for="(span, index) in sortedSpans"
        :key="index"
        class="slowest-row"
        @click="$emit('rowClick', span)"
      >
      <div class="left-accent"></div>
      <div class="row-content">
        <div class="row-header">
          <div class="row-header-left">
            <div class="group-text" :title="span.threadName || 'unknown'">
              <i class="bi bi-cpu thread-icon"></i> {{ span.threadName || 'unknown' }}
            </div>
          </div>
          <div class="time-bar-wrap">
            <span class="time-bar-value">{{
              FormattingService.formatDuration2Units(span.durationNanos)
            }}</span>
            <div class="time-bar-track">
              <div
                class="time-bar-fill"
                :style="{ width: timePercentage(span.durationNanos) + '%' }"
              ></div>
            </div>
          </div>
        </div>
        <div class="row-details">
          <Badge
            v-if="span.tag"
            :value="span.tag"
            variant="primary"
            size="s"
            icon="bi bi-tag"
            :uppercase="false"
            class="span-tag-badge"
          />
          <span class="detail-chip">
            <i class="bi bi-clock"></i>
            {{ FormattingService.formatTimestamp(span.startEpochMillis).replace('T', ' ') }}
          </span>
        </div>
      </div>
      </div>
    </div>
  </div>
  <div v-else class="slowest-empty">No spans for this filter.</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import SlowestCountHeader from '@/components/SlowestCountHeader.vue';

const DISPLAY_LIMIT = 50;

// Accepts both per-tag rows (no tag) and cross-tag slowest rows (with tag). The tag
// chip is only rendered when present.
interface SlowestSpanRow {
  startEpochMillis: number;
  durationNanos: number;
  threadHash: string;
  threadName: string;
  isVirtual: boolean;
  tag?: string;
}

const props = defineProps<{
  spans: SlowestSpanRow[];
}>();

defineEmits<{
  rowClick: [span: SlowestSpanRow];
}>();

const sortedSpans = computed(() =>
  [...props.spans].sort((a, b) => b.durationNanos - a.durationNanos).slice(0, DISPLAY_LIMIT)
);

const maxDuration = computed(() => {
  if (props.spans.length === 0) {
    return 1;
  }
  return Math.max(...props.spans.map(s => s.durationNanos));
});

function timePercentage(durationNanos: number): number {
  return Math.max((durationNanos / maxDuration.value) * 100, 2);
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
  border-radius: 2px;
  flex-shrink: 0;
  margin-right: 1rem;
  background: var(--color-border-light);
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
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.thread-icon {
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
  border-radius: 3px;
  overflow: hidden;
}

.time-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--color-primary), var(--color-secondary));
}

.time-bar-value {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
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

.span-tag-badge {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
