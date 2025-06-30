<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

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
