<template>
  <section class="dashboard-section">
    <div class="charts-grid">
      <div class="chart-card" :class="{ 'full-width': fullWidth }">
        <div class="chart-header">
          <h4>
            <i v-if="icon" :class="`bi bi-${icon} me-2`"></i>
            {{ title }}
          </h4>
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
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-header {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e9ecef;
}

.chart-header h4 {
  margin: 0;
  color: #2c3e50;
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
