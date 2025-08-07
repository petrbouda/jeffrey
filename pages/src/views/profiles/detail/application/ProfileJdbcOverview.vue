<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert 
      v-if="isJdbcStatementsDisabled"
      title="JDBC Statements Dashboard"
      eventType="JDBC statement"
    />

    <div v-else>
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
          secondary-title="Executions"
          :visible-minutes="15"
          :independentSecondaryAxis="true"
          primary-axis-type="durationInNanos"
          secondary-axis-type="number"
        />
      </ChartSection>


      <!-- Statement Groups Section -->
      <ChartSection title="Statement Groups" icon="collection" :full-width="true">
        <JdbcGroupList 
          :groups="jdbcOverviewData.groups"
          :selected-group="null"
          @group-click="handleGroupClick" />
      </ChartSection>

      <!-- JDBC Distribution Charts -->
      <JdbcDistributionCharts
          :operations="jdbcOverviewData?.operations || []"
          second-chart-title="Statement Groups Distribution"
          :second-chart-data="getStatementGroupsData()"
          :total="jdbcOverviewData.header.statementCount"/>

      <!-- Slowest Statements -->
      <JdbcSlowestStatements 
        :statements="jdbcOverviewData.slowStatements"
        @sql-button-click="showSqlModal" />

    </div>

      <!-- No data state -->
      <div v-else class="p-4 text-center">
        <h3 class="text-muted">No JDBC Data Available</h3>
        <p class="text-muted">No JDBC statement events found for this profile</p>
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
import {nextTick, onMounted, ref, computed, withDefaults, defineProps} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import DashboardHeader from '@/components/DashboardHeader.vue';
import JdbcDashboardSection from '@/components/jdbc/JdbcDashboardSection.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import JdbcStatementModal from '@/components/jdbc/JdbcStatementModal.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import JdbcGroupList from '@/components/jdbc/JdbcGroupList.vue';
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
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedStatement = ref<JdbcSlowStatement | null>(null);
const showModal = ref(false);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.projectId as string, route.params.profileId as string);

const getStatementGroupsData = () => {
  if (!jdbcOverviewData.value) return [];
  return jdbcOverviewData.value.groups.map(group => ({
    label: group.group,
    value: group.count
  }));
};

const showSqlModal = (statement: JdbcSlowStatement) => {
  selectedStatement.value = statement;
  showModal.value = true;
};

const handleGroupClick = (group: string) => {
  router.push({
    name: 'profile-application-jdbc-statement-groups',
    query: { group: encodeURIComponent(group) }
  });
};

// Lifecycle methods
const loadJdbcData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    // Load data from API
    jdbcOverviewData.value = await client.getOverview();

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
  // Only load data if the feature is not disabled
  if (!isJdbcStatementsDisabled.value) {
    loadJdbcData();
  }
});
</script>

<style scoped>
.dashboard-container {
  padding: 1.5rem;
}

@media (max-width: 768px) {
  .dashboard-container {
    padding: 1rem;
  }
}
</style>
