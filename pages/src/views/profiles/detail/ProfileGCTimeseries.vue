<template>
  <LoadingState v-if="loading" message="Loading GC timeseries data..." />

  <ErrorState v-else-if="error" message="Failed to load GC timeseries data" />

  <div v-else>
    <!-- Header Section -->
    <PageHeader
      title="GC Timeseries Analysis"
      description="Time-series analysis of garbage collection events and performance metrics"
      icon="bi-graph-up-arrow"
    />


    <!-- Key Metrics Row -->
    <GCMetricsStatsRow :profile-id="route.params.profileId as string" />

    <!-- GC Timeseries Section -->
    <ChartSectionWithTabs
      icon="graph-up"
      :tabs="gcTimeseriesTabs"
      :full-width="true"
      id-prefix="gc-timeseries-"
      @tab-change="onTimeseriesTabChange"
      class="mb-4"
    >
      <!-- Count Tab -->
      <template #count>
        <div class="chart-description">
          <span class="chart-description-label">Shows:</span> Number of GC events per second
          <span class="chart-description-separator">|</span>
          <span class="chart-description-label">Use case:</span> Identify periods of high GC activity and frequency patterns
        </div>
        <TimeSeriesChart
          :primary-data="youngGCData"
          :secondary-data="oldGCData"
          :tertiary-data="fullGCData"
          primary-title="Young GC"
          secondary-title="Old GC"
          tertiary-title="Full GC"
          :primary-axis-type="AxisFormatType.NUMBER"
          :visible-minutes="60"
          primary-color="#4285F4"
          secondary-color="#FBBC04"
          tertiary-color="#EA4335"
          :stacked="true"
        />
      </template>

      <!-- Max Pause Tab -->
      <template #max-pause>
        <div class="chart-description">
          <span class="chart-description-label">Shows:</span> Longest single pause within each second
          <span class="chart-description-separator">|</span>
          <span class="chart-description-label">Use case:</span> Find worst-case latency spikes that may affect user experience
        </div>
        <TimeSeriesChart
          :primary-data="youngGCData"
          :secondary-data="oldGCData"
          :tertiary-data="fullGCData"
          primary-title="Young GC"
          secondary-title="Old GC"
          tertiary-title="Full GC"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :tertiary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :visible-minutes="60"
          primary-color="#4285F4"
          secondary-color="#FBBC04"
          tertiary-color="#EA4335"
          :stacked="true"
        />
      </template>

      <!-- Sum of Pauses Tab -->
      <template #sum-pauses>
        <div class="chart-description">
          <span class="chart-description-label">Shows:</span> Total pause time per second
          <span class="chart-description-separator">|</span>
          <span class="chart-description-label">Use case:</span> Measure overall GC overhead and throughput impact
        </div>
        <TimeSeriesChart
          :primary-data="youngGCData"
          :secondary-data="oldGCData"
          :tertiary-data="fullGCData"
          primary-title="Young GC"
          secondary-title="Old GC"
          tertiary-title="Full GC"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :tertiary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :visible-minutes="60"
          primary-color="#4285F4"
          secondary-color="#FBBC04"
          tertiary-color="#EA4335"
          :stacked="true"
        />
      </template>
    </ChartSectionWithTabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import GCMetricsStatsRow from '@/components/gc/GCMetricsStatsRow.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import GCTimeseriesType from '@/services/api/model/GCTimeseriesType';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const loading = ref(true);
const error = ref<string | null>(null);

// Tabs configuration for GC Timeseries
const gcTimeseriesTabs = [
  { id: 'count', label: 'Count', icon: 'graph-up', type: GCTimeseriesType.COUNT },
  { id: 'max-pause', label: 'Max Pause', icon: 'clock', type: GCTimeseriesType.MAX_PAUSE },
  {
    id: 'sum-pauses',
    label: 'Sum of Pauses',
    icon: 'plus-circle',
    type: GCTimeseriesType.SUM_OF_PAUSES
  }
];

// Timeseries data for Young, Old, and Full GC generations
const youngGCData = ref<number[][]>([]);
const oldGCData = ref<number[][]>([]);
const fullGCData = ref<number[][]>([]);
const currentTimeseriesType = ref<GCTimeseriesType>(GCTimeseriesType.COUNT);

// Client initialization - will be set after workspace/project IDs are available
let client: ProfileGCClient;

// Handle timeseries tab change
const onTimeseriesTabChange = async (_tabIndex: number, tab: any) => {
  if (tab.type) {
    currentTimeseriesType.value = tab.type;
    try {
      // Initialize client if needed
      if (!client) {
        client = new ProfileGCClient(route.params.profileId as string);
      }

      // Load new timeseries data for the selected type
      const timeseriesData = await client.getTimeseries(tab.type);
      // Extract Young GC (first series), Old GC (second series), and Full GC (third series)
      youngGCData.value = timeseriesData.series?.[0]?.data ?? [];
      oldGCData.value = timeseriesData.series?.[1]?.data ?? [];
      fullGCData.value = timeseriesData.series?.[2]?.data ?? [];
    } catch (err) {
      console.error('Error loading timeseries data:', err);
      error.value = 'Failed to load timeseries data';
    }
  }
};

// Load timeseries data from API
const loadTimeseriesData = async () => {
  try {
    loading.value = true;
    error.value = null;

    // Initialize client if needed
    if (!client) {
      client = new ProfileGCClient(route.params.profileId as string);
    }

    // Load timeline data with default COUNT type
    const timeseriesData = await client.getTimeseries(GCTimeseriesType.COUNT);
    // Extract Young GC (first series), Old GC (second series), and Full GC (third series)
    youngGCData.value = timeseriesData.series?.[0]?.data ?? [];
    oldGCData.value = timeseriesData.series?.[1]?.data ?? [];
    fullGCData.value = timeseriesData.series?.[2]?.data ?? [];
    currentTimeseriesType.value = GCTimeseriesType.COUNT;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading GC timeseries data:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadTimeseriesData();
});
</script>

<style scoped>
.loading-overlay,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.chart-description {
  padding: 0.5rem 1rem;
  margin-bottom: 0.5rem;
  font-size: 0.85rem;
  color: #6c757d;
  background-color: #f8f9fa;
  border-radius: 4px;
}

.chart-description-label {
  font-weight: 600;
  color: #495057;
}

.chart-description-separator {
  margin: 0 0.75rem;
  color: #dee2e6;
}

/* Responsive Design */
@media (max-width: 768px) {
  .chart-container {
    height: 300px;
  }
}
</style>
