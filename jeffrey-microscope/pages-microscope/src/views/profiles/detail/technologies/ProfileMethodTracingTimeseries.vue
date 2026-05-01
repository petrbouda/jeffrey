<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div>
    <!-- Feature Disabled State -->
    <TracingDisabledFeatureAlert v-if="isTracingDisabled" />

    <div v-else>
      <!-- Loading State -->
      <LoadingState v-if="loading" message="Loading method tracing data..." />

      <!-- Error State -->
      <ErrorState v-else-if="error" :message="error" @retry="loadData" />

      <!-- Empty State -->
      <EmptyState
        v-else-if="!overviewData || overviewData.header.totalInvocations === 0"
        title="No Method Tracing Data"
        message="No method tracing events were recorded in this profile."
        icon="bi-speedometer2"
      />

      <!-- Dashboard content -->
      <div v-else class="dashboard-container">
        <!-- Stats Cards -->
        <MethodTracingOverviewStats :header="overviewData.header" />

        <!-- Timeseries Chart -->
        <ChartSection
          v-if="durationTimeseries && countTimeseries"
          title="Method Tracing Timeline"
          icon="graph-up"
          :full-width="true"
          container-class="apex-chart-container"
        >
          <TimeSeriesChart
            :primary-data="durationTimeseries"
            primary-title="Total Duration"
            :secondary-data="countTimeseries"
            secondary-title="Invocation Count"
            :visible-minutes="60"
            :independentSecondaryAxis="true"
            :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
            :secondary-axis-type="AxisFormatType.NUMBER"
          />
        </ChartSection>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import ChartSection from '@/components/ChartSection.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import TracingDisabledFeatureAlert from '@/components/alerts/TracingDisabledFeatureAlert.vue';
import MethodTracingOverviewStats from '@/components/method-tracing/MethodTracingOverviewStats.vue';
import ProfileMethodTracingClient from '@/services/api/ProfileMethodTracingClient';
import type MethodTracingOverviewData from '@/services/api/model/MethodTracingOverviewData';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';
import FeatureType from '@/services/api/model/FeatureType';
import { useTechnologyData } from '@/composables/useTechnologyData';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

// Route and navigation
const route = useRoute();

const profileId = route.params.profileId as string;

// Check if tracing dashboard is disabled
const isTracingDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.TRACING_DASHBOARD);
});

const client = new ProfileMethodTracingClient(profileId);

const {
  data: overviewData,
  isLoading: loading,
  error,
  reload: loadData
} = useTechnologyData<MethodTracingOverviewData>(() => client.getOverview(), isTracingDisabled);

// Computed properties
const durationTimeseries = computed(() => {
  if (!overviewData.value?.durationTimeseries) return null;
  return overviewData.value.durationTimeseries.data;
});

const countTimeseries = computed(() => {
  if (!overviewData.value?.countTimeseries) return null;
  return overviewData.value.countTimeseries.data;
});
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
