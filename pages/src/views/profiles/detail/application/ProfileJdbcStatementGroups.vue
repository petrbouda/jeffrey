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
            second-chart-title="Statements Distribution"
            :second-chart-data="getStatementsData()"
            :total="singleGroupData.header.statementCount"/>

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
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DashboardHeader from '@/components/DashboardHeader.vue';
import JdbcGroupList from '@/components/jdbc/JdbcGroupList.vue';
import JdbcDashboardSection from '@/components/jdbc/JdbcDashboardSection.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import JdbcStatementModal from '@/components/jdbc/JdbcStatementModal.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import JdbcSlowestStatements from '@/components/jdbc/JdbcSlowestStatements.vue';
import ProfileJdbcStatementClient from '@/services/profile/custom/jdbc/ProfileJdbcStatementClient.ts';
import JdbcOverviewData from '@/services/profile/custom/jdbc/JdbcOverviewData.ts';
import JdbcSlowStatement from '@/services/profile/custom/jdbc/JdbcSlowStatement.ts';

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

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.projectId as string, route.params.profileId as string);

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
    !isNaN(group.count) && 
    group.count >= 0
  );
};

const getSortedSlowStatements = () => {
  if (!singleGroupData.value) return [];
  
  // Sort by execution time (group-specific data should already be filtered by the backend)
  return singleGroupData.value.slowStatements
    .sort((a, b) => b.executionTime - a.executionTime);
};

const getStatementsData = () => {
  if (!singleGroupData.value) return [];
  
  // There's always only one group when viewing a single group
  const selectedGroup = singleGroupData.value.groups[0];
  return selectedGroup?.statementNames || [];
};

const showSqlModal = (statement: JdbcSlowStatement) => {
  selectedStatement.value = statement;
  showModal.value = true;
};


// Lifecycle methods
const loadData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    if (selectedGroupForDetail.value) {
      // Load group-specific data from API
      singleGroupData.value = await client.getOverviewGroup(selectedGroupForDetail.value);

      // Check if the group data was loaded successfully
      if (!singleGroupData.value) {
        error.value = `Group not found: ${decodeURIComponent(selectedGroupForDetail.value)}`;
      }
    } else {
      // Load overview data when no specific group is selected
      jdbcOverviewData.value = await client.getOverview();
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
