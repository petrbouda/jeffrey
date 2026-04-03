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

        <!-- Method Distribution Charts -->
        <DualPanel
          title="Method Distribution"
          icon="pie-chart"
          left-title="Top Methods by Invocations"
          right-title="Top Methods by Duration"
        >
          <template #left>
            <DonutWithLegend
              :data="invocationChartData"
              :tooltip-formatter="
                (val: number) => FormattingService.formatNumber(val) + ' invocations'
              "
            />
          </template>
          <template #right>
            <DonutWithLegend
              :data="durationChartData"
              :tooltip-formatter="(val: number) => FormattingService.formatDuration2Units(val)"
              :value-formatter="
                (val: string) => FormattingService.formatDuration2Units(Number(val))
              "
            />
          </template>
        </DualPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import DualPanel from '@/components/DualPanel.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import type { DonutChartData } from '@/components/DonutWithLegend.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import TracingDisabledFeatureAlert from '@/components/alerts/TracingDisabledFeatureAlert.vue';
import MethodTracingOverviewStats from '@/components/method-tracing/MethodTracingOverviewStats.vue';
import FormattingService from '@/services/FormattingService';
import ProfileMethodTracingClient from '@/services/api/ProfileMethodTracingClient';
import type MethodTracingOverviewData from '@/services/api/model/MethodTracingOverviewData';
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

// Chart colors palette
const CHART_COLORS = ['#5a9fd4', '#5cb85c', '#f0ad4e', '#d9534f', '#9b59b6', '#6c757d'];

// Computed properties
const invocationChartData = computed<DonutChartData>(() => {
  if (!overviewData.value)
    return { series: [], labels: [], colors: [], legendItems: [], totalValue: '0' };
  const items = overviewData.value.topMethodsByCount.slice(0, 6);
  return {
    series: items.map(m => m.invocationCount),
    labels: items.map(m => getShortMethodName(m.className, m.methodName)),
    colors: items.map((_, i) => CHART_COLORS[i % CHART_COLORS.length]),
    totalValue: FormattingService.formatNumber(overviewData.value.header.totalInvocations),
    legendItems: items.map((m, i) => ({
      color: CHART_COLORS[i % CHART_COLORS.length],
      label: getShortMethodName(m.className, m.methodName),
      value: FormattingService.formatNumber(m.invocationCount)
    }))
  };
});

const durationChartData = computed<DonutChartData>(() => {
  if (!overviewData.value)
    return { series: [], labels: [], colors: [], legendItems: [], totalValue: '0' };
  const items = overviewData.value.topMethodsByDuration.slice(0, 6);
  return {
    series: items.map(m => m.totalDuration),
    labels: items.map(m => getShortMethodName(m.className, m.methodName)),
    colors: items.map((_, i) => CHART_COLORS[i % CHART_COLORS.length]),
    totalValue: FormattingService.formatDuration2Units(overviewData.value.header.totalDuration),
    legendItems: items.map((m, i) => ({
      color: CHART_COLORS[i % CHART_COLORS.length],
      label: getShortMethodName(m.className, m.methodName),
      value: FormattingService.formatDuration2Units(m.totalDuration)
    }))
  };
});

// Helper functions
function getShortMethodName(className: string, methodName: string): string {
  const classParts = className.split('.');
  const shortClassName = classParts[classParts.length - 1];
  return methodName ? `${shortClassName}#${methodName}` : shortClassName;
}
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
