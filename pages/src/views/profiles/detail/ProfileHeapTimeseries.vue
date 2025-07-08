<template>
  <!-- Loading State -->
  <div v-if="loading" class="loading-overlay">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading heap memory data...</span>
    </div>
    <p class="mt-2">Loading heap memory data...</p>
  </div>

  <div v-else-if="error" class="error-state">
    <div class="alert alert-danger d-flex align-items-center">
      <i class="bi bi-exclamation-triangle-fill me-2"></i>
      Failed to load heap memory data
    </div>
  </div>

  <div v-else>
    <!-- Header Section -->
    <DashboardHeader
      title="Heap Memory Timeseries"
      description="Time-series analysis of heap memory usage, allocation patterns, and GC impact"
      icon="graph-up-arrow">
      <template #actions>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-outline-primary" @click="refreshData">
            <i class="bi bi-arrow-clockwise"></i>
          </button>
        </div>
      </template>
    </DashboardHeader>

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
        <ApexTimeSeriesChart
          :primary-data="heapMemoryData"
          primary-title="Before/After GC"
          primary-axis-type="bytes"
          :visible-minutes="60"
          primary-color="#007bff"
          time-unit="milliseconds"
        />
      </template>

      <!-- Allocation Tab -->
      <template #allocation>
        <ApexTimeSeriesChart
          :primary-data="allocationData"
          primary-title="Allocation Rate"
          primary-axis-type="bytes"
          :visible-minutes="60"
          primary-color="#28a745"
          time-unit="seconds"
        />
      </template>
    </ChartSectionWithTabs>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import DashboardHeader from '@/components/DashboardHeader.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import ProfileHeapMemoryClient from '@/services/profile/heap/ProfileHeapMemoryClient';
import HeapMemoryTimeseriesType from '@/services/profile/heap/HeapMemoryTimeseriesType';

const route = useRoute();
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
  {id: 'allocation', label: 'Allocation Rate', icon: 'plus-circle', type: HeapMemoryTimeseriesType.ALLOCATION}
];

// Heap timeseries data
const heapMemoryData = ref<number[][]>([]);
const allocationData = ref<number[][]>([]);
const currentTimeseriesType = ref<HeapMemoryTimeseriesType>(HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC);

// Client initialization
const client = new ProfileHeapMemoryClient(route.params.projectId as string, route.params.profileId as string);

// Handle heap memory tab change
const onHeapMemoryTabChange = async (_tabIndex: number, tab: any) => {
  heapMemoryData.value = [];
  allocationData.value = [];

  if (tab.type) {
    currentTimeseriesType.value = tab.type;
    try {
      // Always load data when switching tabs and clear inactive tab data
      if (tab.type === HeapMemoryTimeseriesType.ALLOCATION) {
        const timeseriesData = await client.getTimeseries(HeapMemoryTimeseriesType.ALLOCATION);
        allocationData.value = timeseriesData.data;
      } else if (tab.type === HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC) {
        const timeseriesData = await client.getTimeseries(HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC);
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
    loading.value = true;
    error.value = null;

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
