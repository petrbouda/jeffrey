<template>
  <section class="dashboard-section">
    <div class="charts-grid">
      <!-- Combined Response Time and Request Count Timeline -->
      <div class="chart-card full-width">
        <div class="chart-header">
          <h4><i class="bi bi-graph-up me-2"></i>HTTP Metrics Timeline</h4>
        </div>
        <div class="chart-container">
          <ApexTimeSeriesChart
            :primary-data="responseTimeData"
            primary-title="Response Time"
            :secondary-data="requestCountData"
            secondary-title="Request Count"
            :visible-minutes="15"
            :independentSecondaryAxis="true"
            primary-axis-type="duration"
            secondary-axis-type="number"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';

interface Props {
  responseTimeData: any[];
  requestCountData: any[];
}

const props = defineProps<Props>();
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
  height: 400px;
  padding: 1rem;
}

.chart-card.full-width .chart-container {
  height: 500px;
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
  
  .chart-container {
    height: 300px;
  }
}
</style>