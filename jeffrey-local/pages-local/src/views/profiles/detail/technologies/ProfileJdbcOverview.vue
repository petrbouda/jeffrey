<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
      v-if="isJdbcStatementsDisabled"
      title="JDBC Statements Dashboard"
      eventType="JDBC statement"
    />

    <div v-else>
      <!-- Loading state -->
      <LoadingState v-if="isLoading" />

      <!-- Error state -->
      <ErrorState v-else-if="error" :message="error" />

      <!-- Dashboard content -->
      <div v-if="jdbcOverviewData" class="dashboard-container">
        <!-- JDBC Overview Cards -->
        <JdbcDashboardSection :jdbc-header="jdbcOverviewData.header" />

        <!-- JDBC Metrics Timeline -->
        <ChartSection
          title="JDBC Metrics Timeline"
          icon="graph-up"
          :full-width="true"
          container-class="apex-chart-container"
        >
          <TimeSeriesChart
            :primary-data="jdbcOverviewData?.executionTimeSerie.data || []"
            primary-title="Execution Time"
            :secondary-data="jdbcOverviewData?.statementCountSerie.data || []"
            secondary-title="Executions"
            :visible-minutes="60"
            :independentSecondaryAxis="true"
            :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
            :secondary-axis-type="AxisFormatType.NUMBER"
          />
        </ChartSection>

        <!-- Statement Groups Section -->
        <JdbcGroupList
          :groups="jdbcOverviewData.groups"
          :selected-group="null"
          @group-click="handleGroupClick"
        />

        <!-- JDBC Distribution Charts -->
        <JdbcDistributionCharts
          :operations="jdbcOverviewData?.operations || []"
          second-chart-title="Statement Groups Distribution"
          :second-chart-data="getStatementGroupsData()"
          :total="jdbcOverviewData.header.statementCount"
        />

        <!-- Slowest Statements -->
        <JdbcSlowestStatements
          :statements="jdbcOverviewData.slowStatements"
          @sql-button-click="showSqlModal"
        />
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
      @update:show="showModal = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import JdbcDashboardSection from '@/components/jdbc/JdbcDashboardSection.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import JdbcStatementModal from '@/components/jdbc/JdbcStatementModal.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import JdbcGroupList from '@/components/jdbc/JdbcGroupList.vue';
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
import { useTechnologyData } from '@/composables/useTechnologyData';

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
const selectedStatement = ref<JdbcSlowStatement | null>(null);
const showModal = ref(false);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);

const {
  data: jdbcOverviewData,
  isLoading,
  error
} = useTechnologyData<JdbcOverviewData>(() => client.getOverview(), isJdbcStatementsDisabled);

const getStatementGroupsData = () => {
  if (!jdbcOverviewData.value) return [];
  return jdbcOverviewData.value.groups.map((group: JdbcGroup) => ({
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
    name: 'profile-technologies-jdbc-statement-groups',
    query: { group: encodeURIComponent(group) }
  });
};
</script>

<style scoped>
@media (max-width: 768px) {
  .dashboard-container {
    padding: 1rem;
  }
}
</style>
