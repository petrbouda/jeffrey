<template>
  <section class="dashboard-section">
    <div class="charts-grid">
      <div class="chart-card" :class="{ 'full-width': fullWidth }">
        <div class="chart-header">
          <h4>
            <i v-if="icon" :class="`bi bi-${icon} me-2`"></i>
            {{ title }}
          </h4>
          <div v-if="$slots['header-actions']" class="chart-header-actions">
            <slot name="header-actions"></slot>
          </div>
        </div>
        <div class="chart-container" :class="containerClass">
          <slot></slot>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
interface Props {
  title: string;
  icon?: string;
  fullWidth?: boolean;
  containerClass?: string;
}

const props = withDefaults(defineProps<Props>(), {
  fullWidth: false,
  containerClass: ''
});
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 1.5rem;
}

.chart-card {
  background: var(--card-bg);
  border: 1px solid var(--card-border-color);
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--card-border-color);
}

.chart-header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.chart-header h4 {
  margin: 0;
  color: var(--color-dark);
  font-size: 1rem;
  font-weight: 600;
}

.chart-container {
  min-height: 200px;
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
