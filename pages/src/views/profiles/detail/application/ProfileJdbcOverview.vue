<template>
  <div>
    <DashboardHeader title="JDBC Statements Overview" icon="database"/>

    <!-- Loading state -->
    <div v-if="isLoading" class="p-4 text-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="p-4 text-center">
      <div class="alert alert-danger" role="alert">
        Error loading JDBC data: {{ error }}
      </div>
    </div>

    <!-- Dashboard content -->
    <div v-if="jdbcOverviewData" class="dashboard-container">
      
      <!-- JDBC Overview Cards -->
      <JdbcDashboardSection :jdbc-header="jdbcOverviewData.header"/>

      <!-- JDBC Metrics Timeline -->
      <ChartSection title="JDBC Metrics Timeline" icon="graph-up" :full-width="true" container-class="apex-chart-container">
        <ApexTimeSeriesChart
          :primary-data="jdbcOverviewData?.executionTimeSerie.data || []"
          primary-title="Execution Time"
          :secondary-data="jdbcOverviewData?.statementCountSerie.data || []"
          secondary-title="Statement Count"
          :visible-minutes="15"
          :independentSecondaryAxis="true"
          primary-axis-type="duration"
          secondary-axis-type="number"
        />
      </ChartSection>


      <!-- JDBC Distribution Charts -->
      <JdbcDistributionCharts
          :operations="jdbcOverviewData?.operations || []"
          :statement-groups="getStatementGroups()"
          :total-operations="getTotalOperations()"
          :total-statements="getTotalStatements()"/>

      <!-- Slowest Statements -->
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
              <tr v-for="statement in getSortedSlowStatements()" :key="statement.timestamp" class="statement-row">
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
                          @click="showSqlModal(statement)"
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

    </div>

    <!-- No data state -->
    <div v-else class="p-4 text-center">
      <h3 class="text-muted">No JDBC Data Available</h3>
      <p class="text-muted">No JDBC statement events found for this profile</p>
    </div>

    <!-- JDBC Statement Modal -->
    <JdbcStatementModal 
      :statement="selectedStatement" 
      modal-id="jdbcStatementModal"
      :show="showModal"
      @update:show="showModal = $event" />
  </div>
</template>

<script setup lang="ts">
import {nextTick, onMounted, ref} from 'vue';
import DashboardHeader from '@/components/DashboardHeader.vue';
import JdbcDashboardSection from '@/components/JdbcDashboardSection.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import JdbcStatementModal from '@/components/JdbcStatementModal.vue';
import JdbcDistributionCharts from '@/components/JdbcDistributionCharts.vue';
import JdbcOverviewData from '@/services/profile/custom/jdbc/JdbcOverviewData.ts';
import JdbcHeader from '@/services/profile/custom/jdbc/JdbcHeader.ts';
import JdbcStatementInfo from '@/services/profile/custom/jdbc/JdbcStatementInfo.ts';
import JdbcOperationStats from '@/services/profile/custom/jdbc/JdbcOperationStats.ts';
import JdbcSlowStatement from '@/services/profile/custom/jdbc/JdbcSlowStatement.ts';
import Serie from '@/services/timeseries/model/Serie.ts';

// Reactive state
const jdbcOverviewData = ref<JdbcOverviewData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedStatement = ref<JdbcSlowStatement | null>(null);
const showModal = ref(false);

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

const getSortedSlowStatements = () => {
  if (!jdbcOverviewData.value) return [];
  return [...jdbcOverviewData.value.slowStatements].sort((a, b) => b.executionTime - a.executionTime);
};

const getTotalOperations = (): number => {
  if (!jdbcOverviewData.value) return 1;
  return jdbcOverviewData.value.operations.reduce((sum, op) => sum + op.count, 0);
};

const getStatementGroups = () => {
  if (!jdbcOverviewData.value) return [];
  
  const groupMap = new Map();
  
  jdbcOverviewData.value.statements.forEach(statement => {
    if (groupMap.has(statement.statementGroup)) {
      const existing = groupMap.get(statement.statementGroup);
      existing.count += statement.executionCount;
      existing.totalExecutionTime += statement.avgExecutionTime * statement.executionCount;
      existing.totalRowsProcessed += statement.totalRowsProcessed;
    } else {
      groupMap.set(statement.statementGroup, {
        group: statement.statementGroup,
        count: statement.executionCount,
        totalExecutionTime: statement.avgExecutionTime * statement.executionCount,
        totalRowsProcessed: statement.totalRowsProcessed,
        avgExecutionTime: statement.avgExecutionTime
      });
    }
  });
  
  // Calculate average execution time for each group
  const groups = Array.from(groupMap.values()).map(group => ({
    ...group,
    avgExecutionTime: group.totalExecutionTime / group.count
  }));
  
  return groups.sort((a, b) => b.count - a.count);
};

const getTotalStatements = (): number => {
  if (!jdbcOverviewData.value) return 1;
  return jdbcOverviewData.value.statements.reduce((sum, stmt) => sum + stmt.executionCount, 0);
};

const showSqlModal = (statement: JdbcSlowStatement) => {
  selectedStatement.value = statement;
  showModal.value = true;
};


// Mock data creation
const createMockData = (): JdbcOverviewData => {
  const header = new JdbcHeader(
    15432,  // statementCount
    2500,   // maxExecutionTime
    250,    // p99ExecutionTime
    150,    // p95ExecutionTime
    0.992,  // successRate
    123,    // errorCount
    2100000, // totalRowsProcessed
    0.153,  // batchOperationPercentage
    342,    // lobOperationCount
    136     // avgRowsPerStatement
  );

  const statements = [
    new JdbcStatementInfo(
      "user-queries", 
      "SELECT u.id, u.username, u.email FROM users u WHERE u.status = ? AND u.created_date > ?",
      1234, 15, 25, 20, 15, 12300, 10, 0, 1.0, 0.0, 0
    ),
    new JdbcStatementInfo(
      "order-inserts",
      "INSERT INTO orders (user_id, product_id, quantity, total_amount, created_date) VALUES (?, ?, ?, ?, ?)",
      856, 45, 85, 65, 45, 856, 1, 2, 0.998, 0.15, 0
    ),
    new JdbcStatementInfo(
      "preference-updates",
      "UPDATE user_preferences SET theme = ?, language = ?, notifications = ? WHERE user_id = ?",
      543, 23, 45, 35, 23, 543, 1, 0, 1.0, 0.8, 0
    ),
    new JdbcStatementInfo(
      "analytics-queries",
      "SELECT COUNT(*) as total, DATE(created_date) as date FROM transactions WHERE created_date BETWEEN ? AND ? GROUP BY DATE(created_date)",
      234, 180, 350, 250, 180, 234, 1, 0, 1.0, 0.0, 0
    ),
    new JdbcStatementInfo(
      "bulk-inserts",
      "INSERT INTO audit_log (user_id, action, details, timestamp) VALUES (?, ?, ?, ?)",
      1890, 12, 35, 25, 12, 1890, 1, 0, 1.0, 0.95, 12
    )
  ];

  const operations = [
    new JdbcOperationStats("SELECT", 8500, 45, 1450000),
    new JdbcOperationStats("INSERT", 3200, 35, 485000),
    new JdbcOperationStats("UPDATE", 2100, 28, 125000),
    new JdbcOperationStats("DELETE", 800, 42, 40000),
    new JdbcOperationStats("EXECUTE", 832, 65, 0)
  ];

  const slowStatements = [
    new JdbcSlowStatement(
      "SELECT COUNT(*) FROM large_transactions t JOIN users u ON t.user_id = u.id WHERE t.created_date BETWEEN '2024-01-01' AND '2024-12-31'",
      "analytics-queries", "SELECT", 850, 1, "[2024-01-01, 2024-12-31]", false, false, Date.now() - 1000000
    ),
    new JdbcSlowStatement(
      "INSERT INTO large_table (data, blob_content, metadata) VALUES (?, ?, ?)",
      "bulk-inserts", "INSERT", 450, 1000, "[data, <BLOB>, metadata]", true, true, Date.now() - 500000
    ),
    new JdbcSlowStatement(
      "UPDATE complex_join SET status = ? WHERE id IN (SELECT id FROM sub_query WHERE condition = ?)",
      "complex-updates", "UPDATE", 380, 25, "[ACTIVE, condition_value]", false, false, Date.now() - 300000
    ),
    new JdbcSlowStatement(
      "DELETE FROM temp_data WHERE created_date < ? AND processed = ?",
      "cleanup-deletes", "DELETE", 320, 15000, "[2024-01-01, true]", false, false, Date.now() - 200000
    )
  ];

  // Mock time series data (48 hours worth of data)
  const now = Date.now();
  const executionTimeData: number[][] = [];
  const statementCountData: number[][] = [];
  const rowsProcessedData: number[][] = [];

  for (let i = 0; i < 48; i++) {
    const timestamp = now - (47 - i) * 60 * 60 * 1000; // Hour by hour
    executionTimeData.push([timestamp, 20 + Math.random() * 80]); // 20-100ms
    statementCountData.push([timestamp, 200 + Math.random() * 300]); // 200-500 statements per hour
    rowsProcessedData.push([timestamp, 30000 + Math.random() * 50000]); // 30K-80K rows per hour
  }

  const executionTimeSerie = new Serie(executionTimeData, "Execution Time (ms)");
  const statementCountSerie = new Serie(statementCountData, "Statement Count");
  const rowsProcessedSerie = new Serie(rowsProcessedData, "Rows Processed");

  return new JdbcOverviewData(
    header,
    statements,
    operations,
    slowStatements,
    executionTimeSerie,
    statementCountSerie,
    rowsProcessedSerie
  );
};

// Lifecycle methods
const loadJdbcData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    // Simulate API call delay
    await new Promise(resolve => setTimeout(resolve, 1000));

    // Load mock data
    jdbcOverviewData.value = createMockData();

    // Wait for DOM updates
    await nextTick();

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading JDBC data:', err);
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  loadJdbcData();
});
</script>

<style scoped>
.dashboard-container {
  padding: 1.5rem;
}

.sql-pattern {
  font-size: 0.85rem;
  color: #6f42c1;
  background-color: #f8f9fa;
  padding: 0.2rem 0.4rem;
  border-radius: 0.25rem;
  word-break: break-all;
}

.sql-code {
  font-size: 0.8rem;
  color: #e83e8c;
  background-color: #f8f9fa;
  padding: 0.2rem 0.4rem;
  border-radius: 0.25rem;
  display: block;
  max-width: 400px;
  word-break: break-all;
}

.distribution-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1rem;
}

.distribution-item {
  padding: 0.75rem;
  border: 1px solid #e9ecef;
  border-radius: 0.375rem;
  background-color: #f8f9fa;
  height: 100%;
}

.operation-item {
  padding: 0.75rem;
  border: 1px solid #e9ecef;
  border-radius: 0.375rem;
  background-color: #f8f9fa;
  height: 100%;
}

.progress {
  background-color: #e9ecef;
}


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
  .dashboard-container {
    padding: 1rem;
  }
  
  .sql-pattern,
  .sql-code {
    font-size: 0.75rem;
    max-width: 200px;
  }

  .distribution-grid {
    grid-template-columns: 1fr;
  }

  .jdbc-table th:nth-child(1) { width: 50%; }
  .jdbc-table th:nth-child(2) { width: 15%; }
  .jdbc-table th:nth-child(3) { width: 15%; }
  .jdbc-table th:nth-child(4) { width: 20%; }
}
</style>
