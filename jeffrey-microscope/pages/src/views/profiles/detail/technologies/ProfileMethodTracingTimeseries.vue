<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
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
        description="No method tracing events were recorded in this profile."
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
            :independent-secondary-axis="true"
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
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
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
  return props.disabledFeatures.includes(FeatureType.METHOD_TRACING_DASHBOARD);
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
  if (!overviewData.value?.durationTimeseries) {
    return null;
  }
  return overviewData.value.durationTimeseries.data;
});

const countTimeseries = computed(() => {
  if (!overviewData.value?.countTimeseries) {
    return null;
  }
  return overviewData.value.countTimeseries.data;
});
</script>
