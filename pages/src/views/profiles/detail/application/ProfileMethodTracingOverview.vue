<!--
  ~ Jeffrey
  ~ Copyright (C) 2025 Petr Bouda
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
      <PageHeader title="Method Tracing Overview" icon="bi-speedometer2" />

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
        <div class="mb-4">
          <StatsTable :metrics="metricsData" />
        </div>

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

        <!-- Method Distribution Charts -->
        <div class="row">
          <div class="col-md-6">
            <PieChart
              title="Top Methods by Invocations"
              icon="bar-chart-line"
              :data="methodsByCount"
              :total="totalInvocations"
              :value-formatter="formatCount"
            />
          </div>
          <div class="col-md-6">
            <PieChart
              title="Top Methods by Duration"
              icon="clock"
              :data="methodsByDuration"
              :total="totalDuration"
              :value-formatter="formatDuration"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineProps, onMounted, ref, withDefaults } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import ChartSection from '@/components/ChartSection.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import PieChart from '@/components/PieChart.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import TracingDisabledFeatureAlert from '@/components/alerts/TracingDisabledFeatureAlert.vue';
import FormattingService from '@/services/FormattingService';
import ProfileMethodTracingClient from '@/services/api/ProfileMethodTracingClient';
import type MethodTracingOverviewData from '@/services/api/model/MethodTracingOverviewData';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';
import FeatureType from '@/services/api/model/FeatureType';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

// Route and navigation
const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

// Check if tracing dashboard is disabled
const isTracingDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.TRACING_DASHBOARD);
});

// State
const loading = ref(true);
const error = ref<string | null>(null);
const overviewData = ref<MethodTracingOverviewData | null>(null);

// Computed properties
const durationTimeseries = computed(() => {
  if (!overviewData.value?.durationTimeseries) return null;
  return overviewData.value.durationTimeseries.data;
});

const countTimeseries = computed(() => {
  if (!overviewData.value?.countTimeseries) return null;
  return overviewData.value.countTimeseries.data;
});

const methodsByCount = computed(() => {
  if (!overviewData.value) return [];
  return overviewData.value.topMethodsByCount.slice(0, 6).map(m => ({
    label: getShortMethodName(m.className, m.methodName),
    value: m.invocationCount
  }));
});

const methodsByDuration = computed(() => {
  if (!overviewData.value) return [];
  return overviewData.value.topMethodsByDuration.slice(0, 6).map(m => ({
    label: getShortMethodName(m.className, m.methodName),
    value: m.totalDuration
  }));
});

const totalInvocations = computed(() => {
  return overviewData.value?.header.totalInvocations ?? 0;
});

const totalDuration = computed(() => {
  return overviewData.value?.header.totalDuration ?? 0;
});

const metricsData = computed(() => {
  if (!overviewData.value) return [];
  const header = overviewData.value.header;

  return [
    {
      icon: 'play-circle',
      title: 'Total Invocations',
      value: FormattingService.formatNumber(header.totalInvocations),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Unique Methods',
          value: header.uniqueMethodCount,
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'stopwatch',
      title: 'Total Duration',
      value: FormattingService.formatDuration2Units(header.totalDuration),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Avg',
          value: FormattingService.formatDuration2Units(header.avgDuration),
          color: '#FBBC05'
        }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Response Time',
      value: FormattingService.formatDuration2Units(header.maxDuration),
      variant: 'warning' as const,
      breakdown: [
        {
          label: 'P99',
          value: FormattingService.formatDuration2Units(header.p99Duration),
          color: '#EA4335'
        },
        {
          label: 'P95',
          value: FormattingService.formatDuration2Units(header.p95Duration),
          color: '#EA4335'
        }
      ]
    },
    {
      icon: 'collection',
      title: 'Unique Methods',
      value: header.uniqueMethodCount,
      variant: 'success' as const,
      breakdown: []
    }
  ];
});

// Helper functions
function getShortMethodName(className: string, methodName: string): string {
  const classParts = className.split('.');
  const shortClassName = classParts[classParts.length - 1];
  return methodName ? `${shortClassName}#${methodName}` : shortClassName;
}

const formatDuration = (value: number) => FormattingService.formatDuration2Units(value);
const formatCount = (value: number) => FormattingService.formatNumber(value) + ' invocations';

// Load data
async function loadData() {
  loading.value = true;
  error.value = null;

  try {
    const client = new ProfileMethodTracingClient(workspaceId.value, projectId.value, profileId);
    overviewData.value = await client.getOverview();
  } catch (e: unknown) {
    console.error('Failed to load method tracing data:', e);
    error.value = 'Failed to load method tracing data. Please try again.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  // Only load data if the feature is not disabled
  if (!isTracingDisabled.value) {
    loadData();
  }
});
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
