<template>
  <section class="dashboard-section">
    <div class="dashboard-grid">
      <DashboardCard
        title="Total Requests"
        :value="httpHeader.requestCount || 0"
        variant="info"
      />
      <DashboardCard
        title="Response Time"
        :value="FormattingService.formatDuration2Units(httpHeader.maxResponseTime)"
        :valueA="FormattingService.formatDuration2Units(httpHeader.p99ResponseTime)"
        :valueB="FormattingService.formatDuration2Units(httpHeader.p95ResponseTime)"
        labelA="P99"
        labelB="P95"
        variant="highlight"
      />
      <DashboardCard
        title="Success Rate"
        :value="`${(httpHeader.successRate * 100 || 0).toFixed(1)}%`"
        :valueA="httpHeader.count4xx"
        :valueB="httpHeader.count5xx"
        labelA="4xx Errors"
        labelB="5xx Errors"
        :variant="(httpHeader.successRate || 0) == 1 ? 'success' : httpHeader.count5xx > 0 ? 'danger' : 'warning'"
      />
      <DashboardCard
        title="Data Transferred"
        :value="httpHeader.totalBytesTransferred < 0 ? '?' : FormattingService.formatBytes(httpHeader.totalBytesTransferred)"
        :valueA="httpHeader.totalBytesReceived < 0 ? '?' : FormattingService.formatBytes(httpHeader.totalBytesReceived)"
        :valueB="httpHeader.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(httpHeader.totalBytesSent)"
        labelA="Received"
        labelB="Sent"
        variant="info"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import DashboardCard from '@/components/DashboardCard.vue';
import FormattingService from '@/services/FormattingService.ts';
import HttpHeader from '@/services/profile/custom/http/HttpHeader.ts';

defineProps<{
  httpHeader: HttpHeader;
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