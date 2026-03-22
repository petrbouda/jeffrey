<template>
  <div class="stats-table-container">
    <div class="stats-table">
      <div v-for="(metric, index) in metrics" :key="index" class="stats-row" :class="metric.variant">
        <!-- Icon Column -->
        <div class="stats-icon" :style="{ background: getIconBg(metric.variant), color: getIconColor(metric.variant) }">
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
            <span class="breakdown-value" :style="{ color: item.color || getVariantColor(metric.variant) }">
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
}>();

const getIconBg = (variant?: string): string => {
  switch (variant) {
    case 'highlight': return '#f5f8ff';
    case 'danger': return '#fff8f0';
    case 'warning': return '#fffbf0';
    case 'info': return '#f0fdf4';
    case 'success': return '#f0fdf4';
    default: return '#f5f8ff';
  }
};

const getIconColor = (variant?: string): string => {
  switch (variant) {
    case 'highlight': return '#4285F4';
    case 'danger': return '#EA4335';
    case 'warning': return '#FBBC05';
    case 'info': return '#34A853';
    case 'success': return '#28a745';
    default: return '#4285F4';
  }
};

const getVariantColor = (variant?: string): string => {
  return getIconColor(variant);
};
</script>

<style scoped>
.stats-table-container {
  background: #fff;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
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
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s ease;
}

.stats-row:last-child {
  border-bottom: none;
}

/* Large screen 2-column layout adjustments */
@media (min-width: 1200px) {
  .stats-row {
    border-right: 1px solid #f0f0f0;
  }

  .stats-row:nth-child(odd) {
    border-bottom: 1px solid #f0f0f0;
  }

  .stats-row:nth-child(even) {
    border-right: none;
  }

  .stats-row:nth-last-child(-n+2) {
    border-bottom: none;
  }
}

.stats-row:hover {
  background-color: rgba(0, 0, 0, 0.02);
}

/* Icon Column */
.stats-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10%;
  font-size: 1.05rem;
  flex-shrink: 0;
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
  color: #666;
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
  color: #111;
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
  gap: 0.40rem;
}

.breakdown-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  line-height: 1.2;
}

.breakdown-value {
  font-size: 0.8rem;
  font-weight: 700;
  line-height: 1.1;
  text-align: right;
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
