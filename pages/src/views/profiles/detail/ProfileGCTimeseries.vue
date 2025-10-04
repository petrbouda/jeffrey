<template>
  <!-- Loading State -->
  <div v-if="loading" class="loading-overlay">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading GC timeseries data...</span>
    </div>
    <p class="mt-2">Loading GC timeseries data...</p>
  </div>

  <div v-else-if="error" class="error-state">
    <div class="alert alert-danger d-flex align-items-center">
      <i class="bi bi-exclamation-triangle-fill me-2"></i>
      Failed to load GC timeseries data
    </div>
  </div>

  <div v-else>
    <!-- Header Section -->
    <DashboardHeader
        title="GC Timeseries Analysis"
        description="Time-series analysis of garbage collection events and performance metrics"
        icon="graph-up-arrow">
      <template #actions>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-outline-primary" @click="refreshData">
            <i class="bi bi-arrow-clockwise"></i>
          </button>
        </div>
      </template>
    </DashboardHeader>

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
        <ApexTimeSeriesChart
          :primary-data="gcTimeseriesData"
          primary-title="GC Count"
          primary-axis-type="number"
          :visible-minutes="60"
          primary-color="#007bff"
        />
      </template>

      <!-- Max Pause Tab -->
      <template #max-pause>
        <ApexTimeSeriesChart
          :primary-data="gcTimeseriesData"
          primary-title="Max Pause Time"
          primary-axis-type="durationInNanos"
          :visible-minutes="60"
          primary-color="#dc3545"
        />
      </template>

      <!-- Sum of Pauses Tab -->
      <template #sum-pauses>
        <ApexTimeSeriesChart
          :primary-data="gcTimeseriesData"
          primary-title="Sum of Pause Times"
          primary-axis-type="durationInNanos"
          :visible-minutes="60"
          primary-color="#ffc107"
        />
      </template>
    </ChartSectionWithTabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import DashboardHeader from '@/components/DashboardHeader.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import ProfileGCClient from '@/services/profile/gc/ProfileGCClient';
import GCTimeseriesType from '@/services/profile/gc/GCTimeseriesType';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const loading = ref(true);
const error = ref<string | null>(null);

// Tabs configuration for GC Timeseries
const gcTimeseriesTabs = [
  { id: 'count', label: 'Count', icon: 'graph-up', type: GCTimeseriesType.COUNT },
  { id: 'max-pause', label: 'Max Pause', icon: 'clock', type: GCTimeseriesType.MAX_PAUSE },
  { id: 'sum-pauses', label: 'Sum of Pauses', icon: 'plus-circle', type: GCTimeseriesType.SUM_OF_PAUSES }
];

// Timeseries data for different GC types
const gcTimeseriesData = ref<number[][]>([]);
const currentTimeseriesType = ref<GCTimeseriesType>(GCTimeseriesType.COUNT);

// Client initialization - will be set after workspace/project IDs are available
let client: ProfileGCClient;

// Handle timeseries tab change
const onTimeseriesTabChange = async (_tabIndex: number, tab: any) => {
  if (tab.type) {
    currentTimeseriesType.value = tab.type;
    try {
      if (!workspaceId.value || !projectId.value) return;

      // Initialize client if needed
      if (!client) {
        client = new ProfileGCClient(workspaceId.value, projectId.value, route.params.profileId as string);
      }

      // Load new timeseries data for the selected type
      const timeseriesData = await client.getTimeseries(tab.type);
      gcTimeseriesData.value = timeseriesData.data;
    } catch (err) {
      console.error('Error loading timeseries data:', err);
      error.value = 'Failed to load timeseries data';
    }
  }
};

// Refresh data
const refreshData = () => {
  loadTimeseriesData();
};

// Load timeseries data from API
const loadTimeseriesData = async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    loading.value = true;
    error.value = null;

    // Initialize client if needed
    if (!client) {
      client = new ProfileGCClient(workspaceId.value, projectId.value, route.params.profileId as string);
    }

    // Load timeline data with default COUNT type
    const timelineData = await client.getTimeseries(GCTimeseriesType.COUNT);
    gcTimeseriesData.value = timelineData.data;
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
.loading-overlay, .error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

/* Responsive Design */
@media (max-width: 768px) {
  .chart-container {
    height: 300px;
  }
}
</style>
