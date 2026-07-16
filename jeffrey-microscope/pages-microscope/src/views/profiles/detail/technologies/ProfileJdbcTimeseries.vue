<template>
  <TechnologyDashboard
    :fetch="() => client.getOverview()"
    :disabled="isJdbcStatementsDisabled"
    disabled-title="JDBC Statements Dashboard"
    event-type="JDBC statement"
    no-data-title="No JDBC Data Available"
    no-data-message="No JDBC statement events found for this profile"
  >
    <template #default="{ data }">
      <!-- JDBC Overview Stats -->
      <JdbcOverviewStats :jdbc-header="data.header" />

      <!-- JDBC Metrics Timeline -->
      <ChartSection
        title="JDBC Metrics Timeline"
        icon="graph-up"
        :full-width="true"
        container-class="apex-chart-container"
      >
        <TimeSeriesChart
          :primary-data="data.executionTimeSerie.data || []"
          primary-title="Execution Time"
          :secondary-data="data.statementCountSerie.data || []"
          secondary-title="Executions"
          :visible-minutes="60"
          :independentSecondaryAxis="true"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.NUMBER"
        />
      </ChartSection>
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import JdbcOverviewStats from '@/components/jdbc/JdbcOverviewStats.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import ProfileJdbcStatementClient from '@/services/api/ProfileJdbcStatementClient.ts';
import TechnologyDashboard from '@/components/technologies/TechnologyDashboard.vue';
import FeatureType from '@/services/api/model/FeatureType';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);
</script>
