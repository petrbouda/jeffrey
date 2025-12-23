<template>
  <LoadingState v-if="loading" message="Loading heap memory data..." />

  <ErrorState v-else-if="error" message="Failed to load heap memory data" />

  <div v-else>
    <!-- Header Section -->
    <PageHeader
      title="Heap Memory Timeseries"
      description="Time-series analysis of heap memory usage, allocation patterns, and GC impact"
      icon="bi-graph-up-arrow"
    >
      <template #actions>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-outline-primary" @click="refreshData">
            <i class="bi bi-arrow-clockwise"></i>
          </button>
        </div>
      </template>
    </PageHeader>

    <!-- Heap Memory Timeseries Section -->
    <ChartSectionWithTabs
      icon="memory"
      :tabs="heapMemoryTabs"
      :full-width="true"
      id-prefix="heap-memory-"
      @tab-change="onHeapMemoryTabChange"
      class="mb-4"
    >
      <!-- Before/After GC Tab -->
      <template #before-after-gc>
        <TimeSeriesChart
          :primary-data="heapMemoryData"
          primary-title="Before/After GC"
          :primary-axis-type="AxisFormatType.BYTES"
          :visible-minutes="60"
          primary-color="#007bff"
          time-unit="seconds"
        />
      </template>

      <!-- Allocation Tab -->
      <template #allocation>
        <TimeSeriesChart
          :primary-data="allocationData"
          primary-title="Allocation Rate"
          :primary-axis-type="AxisFormatType.BYTES"
          :visible-minutes="60"
          primary-color="#28a745"
          time-unit="seconds"
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
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ProfileHeapMemoryClient from '@/services/profile/heap/ProfileHeapMemoryClient';
import HeapMemoryTimeseriesType from '@/services/profile/heap/HeapMemoryTimeseriesType';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const loading = ref(true);
const error = ref<string | null>(null);

// Tabs configuration for Heap Memory
const heapMemoryTabs = [
  {
    id: 'before-after-gc',
    label: 'Before/After GC',
    icon: 'memory',
    type: HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC
  },
  {
    id: 'allocation',
    label: 'Allocation Rate',
    icon: 'plus-circle',
    type: HeapMemoryTimeseriesType.ALLOCATION
  }
];

// Heap timeseries data
const heapMemoryData = ref<number[][]>([]);
const allocationData = ref<number[][]>([]);
const currentTimeseriesType = ref<HeapMemoryTimeseriesType>(
  HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC
);

// Client initialization - will be set after workspace/project IDs are available
let client: ProfileHeapMemoryClient;

// Handle heap memory tab change
const onHeapMemoryTabChange = async (_tabIndex: number, tab: any) => {
  heapMemoryData.value = [];
  allocationData.value = [];

  if (tab.type) {
    currentTimeseriesType.value = tab.type;
    try {
      if (!workspaceId.value || !projectId.value) return;

      // Initialize client if needed
      if (!client) {
        client = new ProfileHeapMemoryClient(
          workspaceId.value,
          projectId.value,
          route.params.profileId as string
        );
      }

      // Always load data when switching tabs and clear inactive tab data
      if (tab.type === HeapMemoryTimeseriesType.ALLOCATION) {
        const timeseriesData = await client.getTimeseries(HeapMemoryTimeseriesType.ALLOCATION);
        allocationData.value = timeseriesData.data;
      } else if (tab.type === HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC) {
        const timeseriesData = await client.getTimeseries(
          HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC
        );
        heapMemoryData.value = timeseriesData.data;
      }
    } catch (err) {
      console.error('Error loading timeseries data:', err);
      error.value = 'Failed to load timeseries data';
    }
  }
};

// Refresh data
const refreshData = () => {
  loadHeapMemoryData();
};

// Load heap memory data
const loadHeapMemoryData = async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    loading.value = true;
    error.value = null;

    // Initialize client if needed
    if (!client) {
      client = new ProfileHeapMemoryClient(
        workspaceId.value,
        projectId.value,
        route.params.profileId as string
      );
    }

    // Load heap memory data with default HEAP_BEFORE_AFTER_GC type
    const heapResult = await client.getTimeseries(HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC);
    heapMemoryData.value = heapResult.data;
    currentTimeseriesType.value = HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading heap memory data:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadHeapMemoryData();
});
</script>

<style scoped>
@media (max-width: 768px) {
  .chart-container {
    height: 300px;
  }
}
</style>
