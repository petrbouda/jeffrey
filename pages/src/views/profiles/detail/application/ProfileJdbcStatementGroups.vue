<template>
  <div>
    <DashboardHeader title="JDBC Statement Groups" icon="collection" />

    <!-- Group Display with Navigation -->
    <div v-if="selectedGroupForDetail" class="group-display-large">
      <div class="group-content">
        <span class="group-name">{{ selectedGroupForDetail }}</span>
      </div>

      <button @click="clearGroupSelection"
          class="btn btn-secondary group-back-button">
        <i class="bi bi-arrow-left me-2"></i>
        All Groups
      </button>
    </div>
    
    <!-- Loading state -->
    <div v-if="isLoading" class="p-4 text-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="p-4 text-center">
      <div class="alert alert-danger" role="alert">
        Error loading JDBC statement groups: {{ error }}
      </div>
    </div>

    <!-- Main content based on state -->
    <div v-if="!isLoading && !error">
      <!-- Single Group Detail content -->
      <div v-if="selectedGroupForDetail && singleGroupData" class="dashboard-container">
        
        <!-- JDBC Overview Cards -->
        <JdbcDashboardSection :jdbc-header="singleGroupData.header"/>

        <!-- JDBC Metrics Timeline -->
        <ChartSection title="JDBC Metrics Timeline" icon="graph-up" :full-width="true" container-class="apex-chart-container">
          <ApexTimeSeriesChart
            :primary-data="singleGroupData?.executionTimeSerie.data || []"
            primary-title="Execution Time"
            :secondary-data="singleGroupData?.statementCountSerie.data || []"
            secondary-title="Executions"
            :visible-minutes="15"
            :independentSecondaryAxis="true"
            primary-axis-type="duration"
            secondary-axis-type="number"
          />
        </ChartSection>

        <!-- JDBC Distribution Charts -->
        <JdbcDistributionCharts
            :operations="singleGroupData?.operations || []"
            :statement-groups="getSelectedGroupData()"
            :total-operations="getTotalOperations()"
            :total-statements="getTotalStatements()"/>

        <!-- Slowest Statements -->
        <JdbcSlowestStatements 
          :statements="getSortedSlowStatements()" 
          @sql-button-click="showSqlModal" />

      </div>

      <!-- Group List -->
      <div v-else-if="jdbcOverviewData" class="dashboard-container">
        <JdbcGroupList
            :groups="getStatementGroups()"
            :selected-group="selectedGroup"
            @group-click="selectGroupForDetail"/>
      </div>

      <!-- No data state -->
      <div v-else class="p-4 text-center">
        <h3 class="text-muted">No JDBC Data Available</h3>
        <p class="text-muted">No JDBC statement groups found for this profile</p>
      </div>
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
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DashboardHeader from '@/components/DashboardHeader.vue';
import JdbcGroupList from '@/components/jdbc/JdbcGroupList.vue';
import JdbcDashboardSection from '@/components/jdbc/JdbcDashboardSection.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import JdbcStatementModal from '@/components/jdbc/JdbcStatementModal.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import JdbcSlowestStatements from '@/components/jdbc/JdbcSlowestStatements.vue';
import JdbcOverviewData from '@/services/profile/custom/jdbc/JdbcOverviewData.ts';
import JdbcHeader from '@/services/profile/custom/jdbc/JdbcHeader.ts';
import JdbcStatementInfo from '@/services/profile/custom/jdbc/JdbcStatementInfo.ts';
import JdbcOperationStats from '@/services/profile/custom/jdbc/JdbcOperationStats.ts';
import JdbcSlowStatement from '@/services/profile/custom/jdbc/JdbcSlowStatement.ts';
import JdbcGroup from '@/services/profile/custom/jdbc/JdbcGroup.ts';
import Serie from '@/services/timeseries/model/Serie.ts';

const route = useRoute();
const router = useRouter();

// Reactive state
const jdbcOverviewData = ref<JdbcOverviewData | null>(null);
const singleGroupData = ref<JdbcOverviewData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedGroup = ref<string | null>(null);
const selectedGroupForDetail = ref<string | null>(null);
const selectedStatement = ref<JdbcSlowStatement | null>(null);
const showModal = ref(false);

// Get route parameters
const projectId = route.params.projectId as string;
const profileId = route.params.profileId as string;

// Group selection methods
const selectGroupForDetail = (group: string) => {
  selectedGroupForDetail.value = group;
  router.push({
    name: 'profile-application-jdbc-statement-groups',
    query: { group: encodeURIComponent(group) }
  });
};

const clearGroupSelection = () => {
  selectedGroupForDetail.value = null;
  router.push({
    name: 'profile-application-jdbc-statement-groups'
  });
};

// Helper functions

const getStatementGroups = () => {
  if (!jdbcOverviewData.value) return [];
  
  // Validate and filter groups data
  return jdbcOverviewData.value.groups.filter(group => 
    group && 
    typeof group.group === 'string' && 
    typeof group.count === 'number' && 
    !isNaN(group.count) && 
    group.count >= 0
  );
};

const getSelectedGroupData = () => {
  if (!singleGroupData.value || !selectedGroupForDetail.value) return [];
  
  // Filter groups to only show the selected one
  return singleGroupData.value.groups.filter(group => 
    group.group === selectedGroupForDetail.value
  );
};

const getSortedSlowStatements = () => {
  if (!singleGroupData.value) return [];
  
  // Filter statements by selected group and sort by execution time
  return singleGroupData.value.slowStatements
    .filter(statement => statement.statementGroup === selectedGroupForDetail.value)
    .sort((a, b) => b.executionTime - a.executionTime);
};

const getTotalOperations = (): number => {
  if (!singleGroupData.value) return 1;
  return singleGroupData.value.operations.reduce((sum, op) => sum + op.count, 0);
};

const getTotalStatements = (): number => {
  if (!singleGroupData.value) return 1;
  const total = singleGroupData.value.statements
    .filter(stmt => stmt.statementGroup === selectedGroupForDetail.value)
    .reduce((sum, stmt) => sum + stmt.executionCount, 0);
  return Math.max(total, 1); // Ensure we never return 0
};

const showSqlModal = (statement: JdbcSlowStatement) => {
  selectedStatement.value = statement;
  showModal.value = true;
};

// Mock data creation (reusing from ProfileJdbcOverview)
const createMockData = (): JdbcOverviewData => {
  const header = new JdbcHeader(
    15432,  // statementCount
    2500,   // maxExecutionTime
    250,    // p99ExecutionTime
    150,    // p95ExecutionTime
    0.992,  // successRate
    123,    // errorCount
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
    new JdbcOperationStats("SELECT", 8500),
    new JdbcOperationStats("INSERT", 3200),
    new JdbcOperationStats("UPDATE", 2100),
    new JdbcOperationStats("DELETE", 800),
    new JdbcOperationStats("EXECUTE", 832)
  ];

  // Create groups from statements
  const groupMap = new Map();
  
  statements.forEach(statement => {
    if (groupMap.has(statement.statementGroup)) {
      const existing = groupMap.get(statement.statementGroup);
      existing.count += statement.executionCount;
      existing.totalExecutionTime += statement.avgExecutionTime * statement.executionCount;
      existing.totalRowsProcessed += statement.totalRowsProcessed;
      existing.maxExecutionTime = Math.max(existing.maxExecutionTime, statement.maxExecutionTime);
      existing.p99ExecutionTime = Math.max(existing.p99ExecutionTime, statement.p99ExecutionTime);
      existing.p95ExecutionTime = Math.max(existing.p95ExecutionTime, statement.p95ExecutionTime);
      existing.errorCount += statement.errorCount || 0;
    } else {
      groupMap.set(statement.statementGroup, {
        group: statement.statementGroup,
        count: statement.executionCount,
        totalRowsProcessed: statement.totalRowsProcessed,
        avgExecutionTime: statement.avgExecutionTime,
        maxExecutionTime: statement.maxExecutionTime,
        p99ExecutionTime: statement.p99ExecutionTime,
        p95ExecutionTime: statement.p95ExecutionTime,
        errorCount: statement.errorCount || 0
      });
    }
  });
  
  // Calculate average execution time for each group and create JdbcGroup instances
  const groups = Array.from(groupMap.values()).map(group => {
    return new JdbcGroup(
      group.group,
      group.count,
      group.totalExecutionTime,
      group.totalRowsProcessed,
      group.maxExecutionTime,
      group.p99ExecutionTime,
      group.p95ExecutionTime,
      group.errorCount
    );
  }).sort((a, b) => b.count - a.count);

  const slowStatements = [
    new JdbcSlowStatement(
      "SELECT COUNT(*) FROM large_transactions t JOIN users u ON t.user_id = u.id WHERE t.created_date BETWEEN '2024-01-01' AND '2024-12-31'",
      "analytics-queries", "SELECT", 850, 1, "[2024-01-01, 2024-12-31]", false, false, Date.now() - 1000000
    ),
    new JdbcSlowStatement(
      "INSERT INTO large_table (data, blob_content, metadata) VALUES (?, ?, ?)",
      "bulk-inserts", "INSERT", 450, 1000, "[data, <BLOB>, metadata]", true, true, Date.now() - 500000
    )
  ];

  // Mock time series data
  const now = Date.now();
  const executionTimeData: number[][] = [];
  const statementCountData: number[][] = [];

  for (let i = 0; i < 48; i++) {
    const timestamp = now - (47 - i) * 60 * 60 * 1000; // Hour by hour
    executionTimeData.push([timestamp, 20 + Math.random() * 80]); // 20-100ms
    statementCountData.push([timestamp, 200 + Math.random() * 300]); // 200-500 statements per hour
  }

  const executionTimeSerie = new Serie(executionTimeData, "Execution Time (ms)");
  const statementCountSerie = new Serie(statementCountData, "Executions");

  return new JdbcOverviewData(
    header,
    statements,
    operations,
    slowStatements,
    groups,
    executionTimeSerie,
    statementCountSerie,
  );
};

// Create group-specific mock data
const createGroupSpecificData = (groupName: string): JdbcOverviewData => {
  const baseData = createMockData();
  
  // Filter statements to only include those from the selected group
  const groupStatements = baseData.statements.filter(stmt => stmt.statementGroup === groupName);
  const groupSlowStatements = baseData.slowStatements.filter(stmt => stmt.statementGroup === groupName);
  
  // Create group-specific header with aggregated data from the group
  const groupData = baseData.groups.find(g => g.group === groupName);
  const groupHeader = new JdbcHeader(
    groupData?.count || 0,
    groupData?.maxExecutionTime || 0,
    groupData?.p99ExecutionTime || 0,
    groupData?.p95ExecutionTime || 0,
    0.99, // Assume good success rate
    groupData?.errorCount || 0
  );
  
  return new JdbcOverviewData(
    groupHeader,
    groupStatements,
    baseData.operations, // Keep all operations for distribution chart
    groupSlowStatements,
    [groupData].filter(Boolean) as JdbcGroup[], // Only the selected group
    baseData.executionTimeSerie, // Keep full time series for now
    baseData.statementCountSerie
  );
};

// Lifecycle methods
const loadData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    if (selectedGroupForDetail.value) {
      // Load group-specific data
      await new Promise(resolve => setTimeout(resolve, 1000));
      singleGroupData.value = createGroupSpecificData(selectedGroupForDetail.value);
    } else {
      // Load overview data when no specific group is selected
      await new Promise(resolve => setTimeout(resolve, 1000));
      jdbcOverviewData.value = createMockData();
    }
    
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading JDBC statement groups:', err);
  } finally {
    isLoading.value = false;
  }
};

// Watch for route changes to handle direct navigation
watch(() => route.query.group, (newGroup) => {
  if (newGroup && typeof newGroup === 'string') {
    selectedGroupForDetail.value = newGroup;
  } else {
    selectedGroupForDetail.value = null;
  }
  // Reload data when group selection changes
  loadData();
}, { immediate: true });

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.group-display-large {
  background: #f8f9ff;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  margin: 1.5rem 0;
  padding: 1rem 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.group-content {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 1.1rem;
  font-weight: 600;
  color: #2c3e50;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.25rem;
  flex: 1;
  min-width: 0;
}

.group-name {
  color: #2d3748;
  font-weight: 600;
  font-style: italic;
}

.group-back-button {
  flex-shrink: 0;
  white-space: nowrap;
  margin-right: 1rem;
}

.dashboard-container {
  padding: 1.5rem;
}

.empty-state {
  max-width: 600px;
  margin: 0 auto;
}

.placeholder-content {
  margin-top: 2rem;
}


@media (max-width: 768px) {
  .dashboard-container {
    padding: 1rem;
  }

  .group-display-large {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
    padding: 0.75rem 1rem;
  }

  .group-content {
    font-size: 1rem;
  }

  .group-back-button {
    align-self: flex-start;
    order: -1;
  }
}
</style>
