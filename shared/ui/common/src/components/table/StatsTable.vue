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
  <div class="stats-table-container">
    <div class="stats-table">
      <div
        v-for="(metric, index) in metrics"
        :key="index"
        class="stats-row"
        :class="[metric.variant, { clickable: clickableRows }]"
        @click="onRowClick(index, metric)"
      >
        <!-- Icon Column -->
        <div class="stats-icon">
          <i :class="['bi', `bi-${metric.icon}`]"></i>
        </div>

        <!-- Metric Info Column -->
        <div class="stats-info">
          <div class="stats-title">
            {{ metric.title }}
            <slot :name="`title-action-${index}`"></slot>
          </div>
          <div class="stats-value">{{ metric.value }}</div>
        </div>

        <!-- Breakdown Values Column -->
        <div v-if="metric.breakdown && metric.breakdown.length > 0" class="stats-breakdown">
          <div v-for="(item, i) in metric.breakdown" :key="i" class="breakdown-item">
            <span class="breakdown-label">{{ item.label }}</span>
            <!-- The variant colour comes from CSS; an explicit per-item colour still wins. -->
            <span class="breakdown-value" :style="item.color ? { color: item.color } : undefined">
              {{ item.value }}
            </span>
          </div>
        </div>
        <div v-else class="stats-breakdown stats-breakdown-empty"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface BreakdownItem {
  label: string;
  value: string | number;
  color?: string;
}

interface Metric {
  icon: string;
  title: string;
  value: string | number;
  variant?: 'highlight' | 'danger' | 'warning' | 'info' | 'success';
  breakdown?: BreakdownItem[];
}

const props = defineProps<{
  metrics: Metric[];
  clickableRows?: boolean;
}>();

const emit = defineEmits<{
  (e: 'metric-click', index: number, metric: Metric): void;
}>();

const onRowClick = (index: number, metric: Metric): void => {
  if (props.clickableRows) {
    emit('metric-click', index, metric);
  }
};
</script>

<style scoped>
.stats-table-container {
  background: var(--color-white);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.stats-table {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
}

/* On large screens, show 2 columns */
@media (min-width: 1200px) {
  .stats-table {
    grid-template-columns: 1fr 1fr;
  }
}

.stats-row {
  display: grid;
  grid-template-columns: auto 1fr 280px;
  gap: 0.875rem;
  align-items: center;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--color-border-row);
  transition: background-color 0.2s ease;
}

.stats-row:last-child {
  border-bottom: none;
}

/* Large screen 2-column layout adjustments */
@media (min-width: 1200px) {
  .stats-row {
    border-right: 1px solid var(--color-border-row);
  }

  .stats-row:nth-child(odd) {
    border-bottom: 1px solid var(--color-border-row);
  }

  .stats-row:nth-child(even) {
    border-right: none;
  }

  .stats-row:nth-last-child(-n + 2) {
    border-bottom: none;
  }
}

.stats-row:hover {
  background-color: var(--color-bg-hover);
}

.stats-row.clickable {
  cursor: pointer;
}

/* Icon Column */
.stats-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  /* Was `10%`, i.e. 3.4px at this 34px tile; --radius-sm (4px) is the nearest token. */
  border-radius: var(--radius-sm);
  font-size: 1.05rem;
  flex-shrink: 0;
  /* Variant classes below override these; an absent variant keeps the highlight scheme. */
  background: var(--color-stat-highlight-bg);
  color: var(--color-stat-highlight);
}

.stats-row.highlight .stats-icon {
  background: var(--color-stat-highlight-bg);
  color: var(--color-stat-highlight);
}

.stats-row.danger .stats-icon {
  background: var(--color-stat-danger-bg);
  color: var(--color-stat-danger);
}

.stats-row.warning .stats-icon {
  background: var(--color-stat-warning-bg);
  color: var(--color-stat-warning);
}

.stats-row.info .stats-icon {
  background: var(--color-stat-info-bg);
  color: var(--color-stat-info);
}

.stats-row.success .stats-icon {
  background: var(--color-stat-success-bg);
  color: var(--color-stat-success);
}

/* Metric Info Column */
.stats-info {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.stats-title {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.4px;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  line-height: 1.2;
}

.stats-value {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-dark);
  line-height: 1.1;
}

/* Breakdown Values Column */
.stats-breakdown {
  display: flex;
  gap: 0.875rem;
  justify-content: flex-end;
}

.stats-breakdown-empty {
  min-height: 1px;
}

.breakdown-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.4rem;
}

.breakdown-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  line-height: 1.2;
}

.breakdown-value {
  font-size: 0.8rem;
  font-weight: 700;
  line-height: 1.1;
  text-align: right;
  color: var(--color-stat-highlight);
}

.stats-row.highlight .breakdown-value {
  color: var(--color-stat-highlight);
}

.stats-row.danger .breakdown-value {
  color: var(--color-stat-danger);
}

.stats-row.warning .breakdown-value {
  color: var(--color-stat-warning);
}

.stats-row.info .breakdown-value {
  color: var(--color-stat-info);
}

.stats-row.success .breakdown-value {
  color: var(--color-stat-success);
}

/* Responsive adjustments */
@media (max-width: 992px) {
  .stats-row {
    grid-template-columns: auto 1fr;
    gap: 0.75rem;
  }

  .stats-breakdown {
    grid-column: 2;
    justify-content: flex-start;
    margin-top: 0.5rem;
  }
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: 1fr;
    gap: 0.5rem;
  }

  .stats-icon {
    width: 32px;
    height: 32px;
    font-size: 1rem;
  }

  .stats-breakdown {
    grid-column: 1;
  }
}
</style>
