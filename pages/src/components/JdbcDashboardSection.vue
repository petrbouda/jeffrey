<template>
  <section class="dashboard-section">
    <div class="dashboard-grid">
      <DashboardCard
        title="Total Statements"
        :value="jdbcHeader.statementCount || 0"
        variant="info"
      />
      <DashboardCard
        title="Execution Time"
        :value="FormattingService.formatDuration2Units(jdbcHeader.maxExecutionTime)"
        :valueA="FormattingService.formatDuration2Units(jdbcHeader.p99ExecutionTime)"
        :valueB="FormattingService.formatDuration2Units(jdbcHeader.p95ExecutionTime)"
        labelA="P99"
        labelB="P95"
        variant="highlight"
      />
      <DashboardCard
        title="Success Rate"
        :value="`${(jdbcHeader.successRate * 100 || 0).toFixed(1)}%`"
        :valueA="jdbcHeader.errorCount"
        labelA="Errors"
        :variant="(jdbcHeader.successRate || 0) >= 0.99 ? 'success' : jdbcHeader.errorCount > 0 ? 'danger' : 'warning'"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import DashboardCard from '@/components/DashboardCard.vue';
import FormattingService from '@/services/FormattingService.ts';
import JdbcHeader from '@/services/profile/custom/jdbc/JdbcHeader.ts';

defineProps<{
  jdbcHeader: JdbcHeader;
}>();
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

/* Responsive Design */
@media (max-width: 768px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
