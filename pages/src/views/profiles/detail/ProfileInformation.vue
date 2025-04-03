<template>
  <div v-if="!profile" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading profile information...</p>
  </div>
  
  <div v-else class="profile-information">
    <div class="card">
      <div class="card-header">
        <h5 class="card-title mb-0">Profile Information</h5>
      </div>
      <div class="card-body">
        <div class="chart-container">
          <TimeSeriesLineGraph
              :data="timeSeriesData"
              title="Performance Over Time"
              yAxisTitle="Value"
              :loading="chartLoading"
              :visibleMinutes="15"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import TimeSeriesLineGraph, { TimeSeriesDataPoint } from '@/components/TimeSeriesLineGraph.vue';
import Profile from '@/services/model/Profile';

// Define props
const props = defineProps<{
  profile?: Profile | null;
}>();

// Format date function
const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleString();
};

// Chart loading state
const chartLoading = ref<boolean>(true);

// Time series data
const timeSeriesData = ref<TimeSeriesDataPoint[]>([]);

// Generate mock time series data
onMounted(() => {
  console.log('ProfileInformation component mounted');
  
  // Show loading indicator
  chartLoading.value = true;
  
  // Generate data immediately
  generateMockedData(180); // 3 hours of data
  
  // Simulate loading delay for UI feedback
  setTimeout(() => {
    // Set loading to false to show the chart
    chartLoading.value = false;
    console.log('Chart data loading complete');
  }, 500);
});

// Generate mocked data for the specified duration in minutes
const generateMockedData = (durationInMinutes: number): void => {
  const data: TimeSeriesDataPoint[] = [];
  const secondsTotal = durationInMinutes * 60;
  
  // Start with a middle value
  let value = 500;
  
  // Create data points at 1-second intervals with random walk
  for (let i = 0; i <= secondsTotal; i++) {
    // Random walk with bounds for more realistic looking data
    // Add some periodic patterns to make it more interesting
    const periodicComponent = 100 * Math.sin(i / 120) + 50 * Math.cos(i / 30);
    const randomComponent = Math.floor(Math.random() * 40) - 20;
    
    value += randomComponent + (periodicComponent / 20);
    value = Math.max(0, Math.min(1000, value)); // Keep within 0-1000 range
    
    data.push({
      time: i,
      value: Math.round(value)
    });
  }
  
  // Log the data size for debugging
  console.log(`Generated ${data.length} data points for ${durationInMinutes} minutes`);
  
  timeSeriesData.value = data;
};
</script>

<style scoped>
.profile-information .card {
  border: none;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

.chart-container {
  width: 100%;
  min-height: 350px;
  position: relative;
  overflow: hidden;
  resize: horizontal; /* Allow user to resize horizontally for testing */
}
</style>
