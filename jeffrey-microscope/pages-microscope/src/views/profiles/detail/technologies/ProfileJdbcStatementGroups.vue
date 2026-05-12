<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
      v-if="isJdbcStatementsDisabled"
      title="JDBC Statements Dashboard"
      eventType="JDBC statement"
    />

    <div v-else>
      <JdbcOverviewStats v-if="jdbcOverviewData" :jdbc-header="jdbcOverviewData.header" />

      <!-- Group Display with Navigation -->
      <DetailBreadcrumb
        v-if="selectedGroupForDetail"
        root-label="Statement Groups"
        @back="clearGroupSelection"
      >
        {{ selectedGroupForDetail }}
      </DetailBreadcrumb>

      <!-- Loading state -->
      <LoadingState v-if="isLoading" />

      <!-- Error state -->
      <ErrorState v-else-if="error" :message="error" />

      <!-- Main content based on state -->
      <div v-if="!isLoading && !error">
        <!-- Single Group Detail content -->
        <div v-if="selectedGroupForDetail && singleGroupData" class="dashboard-container">
          <!-- Statement Name Filter Bar -->
          <SearchableFilterBar
            v-if="statementNameItems.length > 0"
            v-model="selectedStatementName"
            :items="statementNameItems"
            label="Statement Name"
            placeholder="All Statements"
            search-placeholder="Search statement names..."
            items-label="names"
            :total-count="singleGroupData.header.statementCount"
          />

          <!-- Tabbed Analysis -->
          <TabBar v-model="activeTab" :tabs="groupDetailTabs" class="mb-3" />

          <div v-show="activeTab === 'timeseries'">
            <div v-if="isStatementTimelineLoading" class="chart-loading">
              <div class="spinner-border text-primary spinner-border-sm" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
              <span class="text-muted">Loading timeline data...</span>
            </div>

            <TimeSeriesChart
              v-else
              :primary-data="currentTimelinePrimaryData"
              primary-title="Execution Time"
              :secondary-data="currentTimelineSecondaryData"
              secondary-title="Executions"
              :visible-minutes="60"
              :independentSecondaryAxis="true"
              :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
              :secondary-axis-type="AxisFormatType.NUMBER"
            />
          </div>

          <div v-show="activeTab === 'slowest'">
            <div v-if="isSlowestStatementsDataLoading" class="chart-loading">
              <div class="spinner-border text-primary spinner-border-sm" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
              <span class="text-muted">Loading slowest statements...</span>
            </div>

            <JdbcSlowestStatements
              v-else
              :statements="currentSlowestStatements"
              @sql-button-click="showSqlModal"
            />
          </div>
        </div>

        <!-- Group List -->
        <div v-else-if="jdbcOverviewData" class="dashboard-container">
          <JdbcGroupList
            :groups="getStatementGroups()"
            :selected-group="selectedGroup"
            @group-click="selectGroupForDetail"
          />
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
      @update:show="showModal = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DetailBreadcrumb from '@/components/DetailBreadcrumb.vue';
import JdbcGroupList from '@/components/jdbc/JdbcGroupList.vue';
import JdbcOverviewStats from '@/components/jdbc/JdbcOverviewStats.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TabBar from '@/components/TabBar.vue';
import SearchableFilterBar from '@/components/form/SearchableFilterBar.vue';
import JdbcStatementModal from '@/components/jdbc/JdbcStatementModal.vue';
import JdbcSlowestStatements from '@/components/jdbc/JdbcSlowestStatements.vue';
import ProfileJdbcStatementClient from '@/services/api/ProfileJdbcStatementClient.ts';
import JdbcOverviewData from '@/services/api/model/JdbcOverviewData.ts';
import JdbcSlowStatement from '@/services/api/model/JdbcSlowStatement.ts';
import JdbcGroup from '@/services/api/model/JdbcGroup.ts';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';
import type { FilterBarItem } from '@/components/form/SearchableFilterBar.vue';

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

// Statement filter state
const selectedStatementName = ref<string | null>(null);

// Statement-specific timeseries data cache
const statementTimeseriesData = ref<
  Map<string, { executionTime: number[][]; executions: number[][] }>
>(new Map());
const loadingTimelineStatement = ref<string | null>(null);

// Statement-specific slowest statements data cache
const statementSlowestData = ref<Map<string, JdbcSlowStatement[]>>(new Map());
const loadingSlowestStatement = ref<string | null>(null);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);

// Tab definitions for group detail view
const groupDetailTabs = [
  { id: 'timeseries', label: 'Metrics Timeline', icon: 'graph-up' },
  { id: 'slowest', label: 'Slowest Statements', icon: 'stopwatch' }
];
const activeTab = ref(groupDetailTabs[0].id);

// Statement name items normalized for SearchableFilterBar
const statementNameItems = computed<FilterBarItem[]>(() => {
  const names = singleGroupData.value?.groups?.[0]?.statementNames || [];
  return names.map(s => ({
    label: s.label,
    count: s.value,
    p99: s.p99ExecutionTime
  }));
});

// Loading states
const isStatementTimelineLoading = computed(() => {
  return (
    loadingTimelineStatement.value === selectedStatementName.value &&
    selectedStatementName.value != null
  );
});

const isSlowestStatementsDataLoading = computed(() => {
  return (
    loadingSlowestStatement.value === selectedStatementName.value &&
    selectedStatementName.value != null
  );
});

// Current data based on filter
const currentTimelinePrimaryData = computed(() => {
  if (selectedStatementName.value) {
    const data = statementTimeseriesData.value.get(selectedStatementName.value);
    return data?.executionTime || [];
  }
  return singleGroupData.value?.executionTimeSerie.data || [];
});

const currentTimelineSecondaryData = computed(() => {
  if (selectedStatementName.value) {
    const data = statementTimeseriesData.value.get(selectedStatementName.value);
    return data?.executions || [];
  }
  return singleGroupData.value?.statementCountSerie.data || [];
});

const currentSlowestStatements = computed(() => {
  if (selectedStatementName.value) {
    return statementSlowestData.value.get(selectedStatementName.value) || [];
  }
  return getSortedSlowStatements();
});

// Group selection methods
const selectGroupForDetail = (group: string) => {
  selectedGroupForDetail.value = group;
  router.push({
    name: 'profile-technologies-jdbc-statement-groups',
    query: { group: encodeURIComponent(group) }
  });
};

const clearGroupSelection = () => {
  selectedGroupForDetail.value = null;
  clearStatementFilter();
  statementTimeseriesData.value.clear();
  statementSlowestData.value.clear();
  router.push({
    name: 'profile-technologies-jdbc-statement-groups'
  });
};

const clearStatementFilter = () => {
  selectedStatementName.value = null;
};

// Helper functions
const getStatementGroups = () => {
  if (!jdbcOverviewData.value) return [];
  return jdbcOverviewData.value.groups.filter(
    (g: JdbcGroup) => g && !isNaN(g.count) && g.count >= 0
  );
};

const getSortedSlowStatements = () => {
  if (!singleGroupData.value) return [];
  return singleGroupData.value.slowStatements.sort(
    (a: JdbcSlowStatement, b: JdbcSlowStatement) => b.executionTime - a.executionTime
  );
};

const showSqlModal = (statement: JdbcSlowStatement) => {
  selectedStatement.value = statement;
  showModal.value = true;
};

// Load statement-specific timeseries data
const loadStatementTimeseries = async (statementName: string) => {
  if (!selectedGroupForDetail.value) return;
  if (statementTimeseriesData.value.has(statementName)) return;

  try {
    loadingTimelineStatement.value = statementName;
    const series = await client.getTimeseries(selectedGroupForDetail.value, statementName);
    const executionTimeSerie = series[0]?.data || [];
    const executionsSerie = series[1]?.data || [];

    statementTimeseriesData.value.set(statementName, {
      executionTime: executionTimeSerie,
      executions: executionsSerie
    });
  } catch (err) {
    console.error(`Error loading timeseries for statement ${statementName}:`, err);
  } finally {
    if (loadingTimelineStatement.value === statementName) {
      loadingTimelineStatement.value = null;
    }
  }
};

// Load statement-specific slowest statements data
const loadStatementSlowestStatements = async (statementName: string) => {
  if (!selectedGroupForDetail.value) return;
  if (statementSlowestData.value.has(statementName)) return;

  try {
    loadingSlowestStatement.value = statementName;
    const slowestStatements = await client.getSlowestStatements(
      selectedGroupForDetail.value,
      statementName
    );
    statementSlowestData.value.set(statementName, slowestStatements);
  } catch (err) {
    console.error(`Error loading slowest statements for statement ${statementName}:`, err);
  } finally {
    if (loadingSlowestStatement.value === statementName) {
      loadingSlowestStatement.value = null;
    }
  }
};

// Watch statement filter changes
watch(selectedStatementName, newName => {
  if (newName) {
    loadStatementTimeseries(newName);
    loadStatementSlowestStatements(newName);
  }
});

// Lifecycle methods
const loadData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    if (selectedGroupForDetail.value) {
      // Clear previous data
      statementTimeseriesData.value.clear();
      statementSlowestData.value.clear();
      selectedStatementName.value = null;

      // Load group-specific data from API
      singleGroupData.value = await client.getOverviewGroup(selectedGroupForDetail.value);

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
watch(
  () => route.query.group,
  newGroup => {
    if (newGroup && typeof newGroup === 'string') {
      selectedGroupForDetail.value = newGroup;
    } else {
      selectedGroupForDetail.value = null;
    }
    if (!isJdbcStatementsDisabled.value) {
      loadData();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
/* Chart loading state */
.chart-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 200px;
  font-size: 0.8rem;
}
</style>
