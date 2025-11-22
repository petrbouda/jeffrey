<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert 
      v-if="isJdbcStatementsDisabled"
      title="JDBC Statements Dashboard"
      eventType="JDBC statement"
    />

    <div v-else>
      <PageHeader title="JDBC Statement Groups" icon="bi-collection" />

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
        <ChartSectionWithTabs 
          title="JDBC Metrics Timeline" 
          icon="graph-up" 
          :full-width="true"
          :tabs="timelineTabs"
          id-prefix="timeline-"
          @tab-change="onTabChange"
        >
          <template #total>
            <ApexTimeSeriesChart
              :primary-data="singleGroupData?.executionTimeSerie.data || []"
              primary-title="Execution Time"
              :secondary-data="singleGroupData?.statementCountSerie.data || []"
              secondary-title="Executions"
              :visible-minutes="15"
              :independentSecondaryAxis="true"
              primary-axis-type="durationInNanos"
              secondary-axis-type="number"
            />
          </template>
          
          <!-- Dynamic statement name tabs -->
          <template v-for="statementName in getStatementNames()" :key="statementName.label" v-slot:[getStatementSlotName(statementName.label)]>
            <!-- Loading state -->
            <div v-if="isStatementLoading(statementName.label)" class="statement-loading">
              <div class="text-center p-4">
                <div class="spinner-border text-primary" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
                <p class="mt-2 text-muted">Loading {{ statementName.label }} timeline data...</p>
              </div>
            </div>
            
            <!-- Chart with data -->
            <ApexTimeSeriesChart
              v-else-if="getStatementTimeseries(statementName.label)"
              :primary-data="getStatementTimeseries(statementName.label)?.executionTime || []"
              primary-title="Execution Time"
              :secondary-data="getStatementTimeseries(statementName.label)?.executions || []"
              secondary-title="Executions"
              :visible-minutes="15"
              :independentSecondaryAxis="true"
              primary-axis-type="durationInNanos"
              secondary-axis-type="number"
            />
            
            <!-- Placeholder for not loaded yet -->
            <div v-else class="statement-placeholder">
              <div class="text-center p-4">
                <i class="bi bi-graph-up display-4 text-muted mb-3"></i>
                <h5 class="text-muted">{{ statementName.label }} Timeline</h5>
                <p class="text-muted">Click this tab to load statement-specific timeline data</p>
                <div class="mt-3">
                  <span class="badge bg-primary">Total Executions: {{ statementName.value.toLocaleString() }}</span>
                </div>
              </div>
            </div>
          </template>
        </ChartSectionWithTabs>

        <!-- JDBC Distribution Charts -->
        <JdbcDistributionCharts
            :operations="singleGroupData?.operations || []"
            second-chart-title="Statements Distribution"
            :second-chart-data="getStatementsData()"
            :total="singleGroupData.header.statementCount"/>

        <!-- Slowest Statements -->
        <ChartSectionWithTabs 
          title="Slowest Statements" 
          icon="stopwatch" 
          :full-width="true"
          :tabs="slowestStatementsTabs"
          id-prefix="slowest-"
          @tab-change="onSlowestStatementsTabChange"
        >
          <template #total>
            <JdbcSlowestStatements 
              :statements="getSortedSlowStatements()" 
              :show-wrapper="false"
              @sql-button-click="showSqlModal" />
          </template>
          
          <!-- Dynamic statement name tabs -->
          <template v-for="statementName in getStatementNames()" :key="statementName.label" v-slot:[getStatementSlotName(statementName.label)]>
            <!-- Loading state -->
            <div v-if="isSlowestStatementsLoading(statementName.label)" class="statement-loading">
              <div class="text-center p-4">
                <div class="spinner-border text-primary" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
                <p class="mt-2 text-muted">Loading {{ statementName.label }} slowest statements...</p>
              </div>
            </div>
            
            <!-- Slowest statements with data -->
            <JdbcSlowestStatements 
              v-else-if="getStatementSlowestStatements(statementName.label)"
              :statements="getStatementSlowestStatements(statementName.label)" 
              :show-wrapper="false"
              @sql-button-click="showSqlModal" />
            
            <!-- Placeholder for not loaded yet -->
            <div v-else class="statement-placeholder">
              <div class="text-center p-4">
                <i class="bi bi-stopwatch display-4 text-muted mb-3"></i>
                <h5 class="text-muted">{{ statementName.label }} Slowest Statements</h5>
                <p class="text-muted">Click this tab to load statement-specific slowest statements</p>
                <div class="mt-3">
                  <span class="badge bg-primary">Total Executions: {{ statementName.value.toLocaleString() }}</span>
                </div>
              </div>
            </div>
          </template>
        </ChartSectionWithTabs>

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
import { ref, computed, watch, withDefaults, defineProps } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import JdbcGroupList from '@/components/jdbc/JdbcGroupList.vue';
import JdbcDashboardSection from '@/components/jdbc/JdbcDashboardSection.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import JdbcStatementModal from '@/components/jdbc/JdbcStatementModal.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import JdbcSlowestStatements from '@/components/jdbc/JdbcSlowestStatements.vue';
import ProfileJdbcStatementClient from '@/services/profile/custom/jdbc/ProfileJdbcStatementClient.ts';
import JdbcOverviewData from '@/services/profile/custom/jdbc/JdbcOverviewData.ts';
import JdbcSlowStatement from '@/services/profile/custom/jdbc/JdbcSlowStatement.ts';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/profile/features/FeatureType';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

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

// Statement-specific timeseries data
const statementTimeseriesData = ref<Map<string, { executionTime: number[][], executions: number[][] }>>(new Map());
const loadingStatements = ref<Set<string>>(new Set());
const activeTimelineTab = ref<string>('total');

// Statement-specific slowest statements data
const statementSlowestData = ref<Map<string, JdbcSlowStatement[]>>(new Map());
const loadingSlowestStatements = ref<Set<string>>(new Set());
const activeSlowestTab = ref<string>('total');

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(
  route.params.workspaceId as string,
  route.params.projectId as string,
  route.params.profileId as string
);

// Computed property for timeline tabs
const timelineTabs = computed(() => {
  const tabs = [
    {
      id: 'total',
      label: 'Total'
    }
  ];
  
  // Add tabs for each statement name from the groups field (there should be only one group)
  if (singleGroupData.value?.groups && singleGroupData.value.groups.length > 0) {
    const group = singleGroupData.value.groups[0];
    if (group.statementNames) {
      group.statementNames.forEach(statementName => {
        tabs.push({
          id: `statement-${statementName.label.toLowerCase().replace(/\s+/g, '-')}`,
          label: statementName.label
        });
      });
    }
  }
  
  return tabs;
});

// Computed property for slowest statements tabs (same structure as timeline tabs)
const slowestStatementsTabs = computed(() => {
  const tabs = [
    {
      id: 'total',
      label: 'Total'
    }
  ];
  
  // Add tabs for each statement name from the groups field (there should be only one group)
  if (singleGroupData.value?.groups && singleGroupData.value.groups.length > 0) {
    const group = singleGroupData.value.groups[0];
    if (group.statementNames) {
      group.statementNames.forEach(statementName => {
        tabs.push({
          id: `statement-${statementName.label.toLowerCase().replace(/\s+/g, '-')}`,
          label: statementName.label
        });
      });
    }
  }
  
  return tabs;
});

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
  // Clear statement timeseries data when leaving group detail
  statementTimeseriesData.value.clear();
  loadingStatements.value.clear();
  // Clear statement slowest data when leaving group detail
  statementSlowestData.value.clear();
  loadingSlowestStatements.value.clear();
  activeTimelineTab.value = 'total';
  activeSlowestTab.value = 'total';
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

const getStatementNames = () => {
  return singleGroupData.value?.groups?.[0]?.statementNames || [];
};

const getStatementSlotName = (label: string) => {
  return `statement-${label.toLowerCase().replace(/\s+/g, '-')}`;
};

// Tab change handler for timeline
const onTabChange = (tabIndex: number, tab: any) => {
  activeTimelineTab.value = tab.id;
  
  // Load timeseries data for statement tabs (not for Total tab)
  if (tab.id !== 'total' && tab.id.startsWith('statement-')) {
    const statementLabel = tab.label;
    loadStatementTimeseries(statementLabel);
  }
};

// Tab change handler for slowest statements
const onSlowestStatementsTabChange = (tabIndex: number, tab: any) => {
  activeSlowestTab.value = tab.id;
  
  // Load slowest statements data for statement tabs (not for Total tab)
  if (tab.id !== 'total' && tab.id.startsWith('statement-')) {
    const statementLabel = tab.label;
    loadStatementSlowestStatements(statementLabel);
  }
};

// Load statement-specific timeseries data
const loadStatementTimeseries = async (statementName: string) => {
  if (!selectedGroupForDetail.value) return;
  
  // Check if data is already loaded
  if (statementTimeseriesData.value.has(statementName)) return;
  
  try {
    loadingStatements.value.add(statementName);
    const series = await client.getTimeseries(selectedGroupForDetail.value, statementName);
    
    // The first serie is Execution Time, the second is Executions
    const executionTimeSerie = series[0]?.data || [];
    const executionsSerie = series[1]?.data || [];
    
    statementTimeseriesData.value.set(statementName, {
      executionTime: executionTimeSerie,
      executions: executionsSerie
    });
  } catch (err) {
    console.error(`Error loading timeseries for statement ${statementName}:`, err);
  } finally {
    loadingStatements.value.delete(statementName);
  }
};

// Helper to get timeseries data for a statement
const getStatementTimeseries = (statementName: string) => {
  return statementTimeseriesData.value.get(statementName);
};

// Helper to check if a statement is loading
const isStatementLoading = (statementName: string) => {
  return loadingStatements.value.has(statementName);
};

// Load statement-specific slowest statements data
const loadStatementSlowestStatements = async (statementName: string) => {
  if (!selectedGroupForDetail.value) return;
  
  // Check if data is already loaded
  if (statementSlowestData.value.has(statementName)) return;
  
  try {
    loadingSlowestStatements.value.add(statementName);
    const slowestStatements = await client.getSlowestStatements(selectedGroupForDetail.value, statementName);
    
    statementSlowestData.value.set(statementName, slowestStatements);
  } catch (err) {
    console.error(`Error loading slowest statements for statement ${statementName}:`, err);
  } finally {
    loadingSlowestStatements.value.delete(statementName);
  }
};

// Helper to get slowest statements data for a statement
const getStatementSlowestStatements = (statementName: string) => {
  return statementSlowestData.value.get(statementName);
};

// Helper to check if slowest statements are loading
const isSlowestStatementsLoading = (statementName: string) => {
  return loadingSlowestStatements.value.has(statementName);
};


// Lifecycle methods
const loadData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    if (selectedGroupForDetail.value) {
      // Clear previous statement data
      statementTimeseriesData.value.clear();
      loadingStatements.value.clear();
      statementSlowestData.value.clear();
      loadingSlowestStatements.value.clear();
      activeTimelineTab.value = 'total';
      activeSlowestTab.value = 'total';
      
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
  // Only reload data when group selection changes if feature is not disabled
  if (!isJdbcStatementsDisabled.value) {
    loadData();
  }
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

.statement-placeholder {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.statement-loading {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

@media (max-width: 768px) {
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
