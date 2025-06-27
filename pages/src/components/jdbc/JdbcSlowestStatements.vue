<template>
  <ChartSection title="Slowest Statements" icon="stopwatch" :full-width="true">
    <div class="table-responsive">
      <table class="table table-hover jdbc-table">
        <thead>
          <tr>
            <th>Statement Group</th>
            <th class="text-center">Execution Time</th>
            <th class="text-center">Rows</th>
            <th class="text-center">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="statement in statements" :key="statement.timestamp" class="statement-row">
            <td class="statement-cell">
              <div class="statement-display">
                <span class="statement-method-badge" :class="`method-${statement.operation.toLowerCase()}`">
                  {{ statement.operation }}
                </span>
                <div class="group-display" :title="statement.statementGroup">
                  {{ statement.statementGroup }}
                </div>
              </div>
              <div class="statement-meta">
                <div class="statement-timestamp">
                  <i class="bi bi-clock"></i>
                  <span class="timestamp-value">{{ formatTimestamp(statement.timestamp) }}</span>
                </div>
                <div class="statement-flags">
                  <span v-if="statement.isBatch" class="badge bg-info me-1">Batch</span>
                  <span v-if="statement.isLob" class="badge bg-warning">LOB</span>
                </div>
              </div>
            </td>
            <td class="text-center">{{ formatDuration(statement.executionTime) }}</td>
            <td class="text-center">{{ formatNumber(statement.rowsProcessed) }}</td>
            <td class="text-center">
              <button type="button" 
                      class="btn btn-sm btn-outline-primary sql-button"
                      @click="handleSqlButtonClick(statement)"
                      title="View SQL Statement">
                <i class="bi bi-code-slash"></i>
                SQL
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </ChartSection>
</template>

<script setup lang="ts">
import ChartSection from '@/components/ChartSection.vue';
import JdbcSlowStatement from '@/services/profile/custom/jdbc/JdbcSlowStatement.ts';

interface Props {
  statements: JdbcSlowStatement[];
}

defineProps<Props>();

const emit = defineEmits<{
  sqlButtonClick: [statement: JdbcSlowStatement]
}>();

// Helper functions
const formatNumber = (num: number): string => {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
  return num.toString();
};

const formatDuration = (milliseconds: number): string => {
  if (milliseconds >= 1000) return (milliseconds / 1000).toFixed(1) + 's';
  return Math.round(milliseconds) + 'ms';
};

const formatTimestamp = (timestamp: number): string => {
  return new Date(timestamp).toISOString().replace('T', ' ').substring(0, 19);
};

const handleSqlButtonClick = (statement: JdbcSlowStatement) => {
  emit('sqlButtonClick', statement);
};
</script>

<style scoped>
/* JDBC Slowest Statements Styling */
.jdbc-table {
  width: 100%;
  table-layout: fixed;
}

.jdbc-table th:nth-child(1) { width: 55%; }
.jdbc-table th:nth-child(2) { width: 15%; }
.jdbc-table th:nth-child(3) { width: 15%; }
.jdbc-table th:nth-child(4) { width: 15%; }

.statement-cell {
  font-family: 'Courier New', monospace;
  font-size: 0.85rem;
}

.statement-display {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.group-display {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 0.875rem;
  font-weight: 500;
  font-style: italic;
  background: #f7fafc;
  padding: 0.25rem 0.5rem;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  flex: 1;
  color: #2d3748;
}

.statement-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.375rem;
}

.statement-method-badge {
  padding: 0.375rem 0.625rem;
  border-radius: 5px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  min-height: 2rem;
}

.statement-method-badge.method-select { background-color: #cce5ff; color: #004085; }
.statement-method-badge.method-insert { background-color: #d4edda; color: #155724; }
.statement-method-badge.method-update { background-color: #fff3cd; color: #856404; }
.statement-method-badge.method-delete { background-color: #f8d7da; color: #721c24; }
.statement-method-badge.method-execute { background-color: #e2e3e5; color: #383d41; }

.statement-row:hover {
  background-color: #f8f9fa;
}

.statement-timestamp {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0.25rem 0.6rem;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 500;
  width: fit-content;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  gap: 0.4rem;
}

.statement-timestamp i {
  font-size: 0.7rem;
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.timestamp-value {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-weight: 500;
  letter-spacing: 0.015em;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

.statement-flags {
  display: flex;
  gap: 0.25rem;
}

.sql-button {
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.sql-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.sql-button i {
  margin-right: 0.25rem;
}

@media (max-width: 768px) {
  .jdbc-table th:nth-child(1) { width: 50%; }
  .jdbc-table th:nth-child(2) { width: 15%; }
  .jdbc-table th:nth-child(3) { width: 15%; }
  .jdbc-table th:nth-child(4) { width: 20%; }
}
</style>