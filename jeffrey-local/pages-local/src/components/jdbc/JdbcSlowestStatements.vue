<template>
  <ChartSection v-if="showWrapper" title="Slowest Statements" icon="stopwatch" :full-width="true">
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
          <tr v-for="statement in sortedStatements" :key="statement.timestamp" class="statement-row">
            <td class="statement-cell">
              <div class="statement-display">
                <JdbcOperationBadge :operation="statement.operation" />
                <div class="group-display" :title="statement.statementGroup">
                  {{ statement.statementGroup }}
                </div>
              </div>
              <div class="statement-meta">
                <Badge 
                  :value="FormattingService.formatTimestamp(statement.timestamp).replace('T', ' ')"
                  variant="grey"
                  size="s"
                />
                <div class="statement-flags">
                  <span v-if="statement.isBatch" class="statement-flag-badge flag-batch">BATCH</span>
                  <span v-if="statement.isLob" class="statement-flag-badge flag-lob">LOB</span>
                </div>
              </div>
            </td>
            <td class="text-center">{{ FormattingService.formatDuration2Units(statement.executionTime) }}</td>
            <td class="text-center">{{ FormattingService.formatNumber(statement.rowsProcessed) }}</td>
            <td class="text-center">
              <button type="button" 
                      class="btn btn-sm btn-outline-primary sql-button"
                      :disabled="!statement.sql || !statement.sql.trim()"
                      @click="handleSqlButtonClick(statement)"
                      :title="statement.sql && statement.sql.trim() ? 'View SQL Statement' : 'No SQL available'">
                <i class="bi bi-code"></i>
                SQL
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </ChartSection>
  
  <div v-else class="table-responsive">
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
        <tr v-for="statement in sortedStatements" :key="statement.timestamp" class="statement-row">
          <td class="statement-cell">
            <div class="statement-display">
              <JdbcOperationBadge :operation="statement.operation" />
              <div class="group-display" :title="statement.statementGroup">
                {{ statement.statementGroup }}
              </div>
            </div>
            <div class="statement-meta">
              <Badge 
                :value="FormattingService.formatTimestamp(statement.timestamp).replace('T', ' ')"
                variant="grey"
                size="s"
              />
              <div class="statement-flags">
                <span v-if="statement.isBatch" class="statement-flag-badge flag-batch">BATCH</span>
                <span v-if="statement.isLob" class="statement-flag-badge flag-lob">LOB</span>
              </div>
            </div>
          </td>
          <td class="text-center">{{ FormattingService.formatDuration2Units(statement.executionTime) }}</td>
          <td class="text-center">{{ FormattingService.formatNumber(statement.rowsProcessed) }}</td>
          <td class="text-center">
            <button type="button" 
                    class="btn btn-sm btn-outline-primary sql-button"
                    :disabled="!statement.sql || !statement.sql.trim()"
                    @click="handleSqlButtonClick(statement)"
                    :title="statement.sql && statement.sql.trim() ? 'View SQL Statement' : 'No SQL available'">
              <i class="bi bi-code"></i>
              SQL
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed, withDefaults } from 'vue';
import ChartSection from '@/components/ChartSection.vue';
import JdbcOperationBadge from '@/components/jdbc/JdbcOperationBadge.vue';
import Badge from '@/components/Badge.vue';
import JdbcSlowStatement from '@/services/api/model/JdbcSlowStatement.ts';
import FormattingService from "@/services/FormattingService.ts";

interface Props {
  statements: JdbcSlowStatement[];
  showWrapper?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showWrapper: true
});

// Sort statements by executionTime in descending order (slowest first)
const sortedStatements = computed(() => 
  [...props.statements].sort((a, b) => b.executionTime - a.executionTime)
);

const emit = defineEmits<{
  sqlButtonClick: [statement: JdbcSlowStatement]
}>();

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
  padding: 0.375rem 0.625rem;
  border-radius: 5px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  flex: 1;
  color: #2d3748;
  min-height: 2rem;
}

.statement-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.375rem;
}


.statement-row:hover {
  background-color: #f8f9fa;
}


.statement-flags {
  display: flex;
  gap: 0.25rem;
}

.statement-flag-badge {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  min-height: 1.5rem;
}

.statement-flag-badge.flag-batch {
  background-color: #e3f2fd;
  color: #1565c0;
  border: 1px solid #bbdefb;
  padding: 0.2rem 0.4rem;
  font-size: 0.65rem;
  min-height: 1.25rem;
}

.statement-flag-badge.flag-lob {
  background-color: #fff8e1;
  color: #f57c00;
  border: 1px solid #ffecb3;
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
