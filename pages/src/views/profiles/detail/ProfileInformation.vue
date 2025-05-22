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
              :secondaryData="secondaryTimeSeriesData"
              title="12-Hour Performance Metrics"
              yAxisTitle="CPU Usage (%)"
              secondaryYAxisTitle="Memory Usage (MB)"
              :loading="chartLoading"
              :visibleMinutes="60"
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

// Time series data for both series
const timeSeriesData = ref<TimeSeriesDataPoint[]>([]);
const secondaryTimeSeriesData = ref<TimeSeriesDataPoint[]>([]);

// Generate mock time series data
onMounted(() => {
  // Show loading indicator
  chartLoading.value = true;
  
  // Generate data immediately for 12 hours (720 minutes)
  generateMockedData(720); // 12 hours of data
  generateSecondaryMockedData(720); // 12 hours of secondary data
  
  // Simulate loading delay for UI feedback
  setTimeout(() => {
    // Set loading to false to show the chart
    chartLoading.value = false;
  }, 500);
});

// Generate mocked data for the specified duration in minutes
const generateMockedData = (durationInMinutes: number): void => {
  const data: TimeSeriesDataPoint[] = [];
  const secondsTotal = durationInMinutes * 60;
  
  // Base value and parameters for 12-hour variation
  let value = 500;
  const hourInSeconds = 3600;
  const dayPattern = 12 * hourInSeconds; // Pattern repeats over 12 hours
  
  // Create data points with various patterns
  for (let i = 0; i <= secondsTotal; i++) {
    // Time of day variations - simulating usage patterns throughout the day
    // Major cycle over 12 hours with peak in the middle (representing midday)
    const hourOfDay = (i % dayPattern) / hourInSeconds;
    const timeOfDayComponent = 150 * Math.sin((hourOfDay / 12) * Math.PI);
    
    // Medium frequency variations - simulating regular activity cycles
    const mediumComponent = 80 * Math.sin(i / 1800) + 40 * Math.cos(i / 900);
    
    // Higher frequency variations - representing short-term fluctuations
    const shortComponent = 30 * Math.sin(i / 300) + 20 * Math.cos(i / 150);
    
    // Random noise component - representing unpredictable variations
    const randomComponent = Math.floor(Math.random() * 30) - 15;
    
    // Combine all components with appropriate weighting
    value += randomComponent + (timeOfDayComponent / 100) + (mediumComponent / 50) + (shortComponent / 40);
    
    // Keep within a reasonable range
    value = Math.max(100, Math.min(900, value));
    
    // Add occasional spikes to simulate events (approximately once per hour)
    if (Math.random() < 0.0003) { // Probability tuned for 12 hours
      value = Math.min(1000, value + Math.random() * 300);
    }
    
    data.push({
      time: i,
      value: Math.round(value)
    });
  }

  timeSeriesData.value = data;
};

// Generate secondary mocked data with a different pattern
const generateSecondaryMockedData = (durationInMinutes: number): void => {
  const data: TimeSeriesDataPoint[] = [];
  const secondsTotal = durationInMinutes * 60;
  
  // Start with a different value for the secondary series
  let value = 300;
  const hourInSeconds = 3600;
  const dayPattern = 12 * hourInSeconds;
  
  // Create data points with a complementary pattern
  for (let i = 0; i <= secondsTotal; i++) {
    // Time of day variations - inversely related to primary metric
    // This creates an effect where when primary is high, secondary is low
    const hourOfDay = (i % dayPattern) / hourInSeconds;
    const timeOfDayComponent = -120 * Math.sin((hourOfDay / 12) * Math.PI + 0.5);
    
    // Medium frequency variations with different phase
    const mediumComponent = 100 * Math.sin(i / 2400 + 1.5) + 60 * Math.cos(i / 1200 + 0.8);
    
    // Higher frequency variations with unique pattern
    const shortComponent = 40 * Math.sin(i / 400 + 0.3) + 25 * Math.cos(i / 180 + 0.6);
    
    // Less random noise for clearer pattern differentiation
    const randomComponent = Math.floor(Math.random() * 15) - 7;
    
    // Combine all components with appropriate weighting
    value += randomComponent + (timeOfDayComponent / 80) + (mediumComponent / 60) + (shortComponent / 35);
    
    // Different range to show scale handling
    value = Math.max(50, Math.min(750, value));
    
    // Add occasional dips (instead of spikes in primary) to show inverse correlation
    if (Math.random() < 0.0002) {
      value = Math.max(20, value - Math.random() * 200);
    }
    
    data.push({
      time: i,
      value: Math.round(value)
    });
  }

  secondaryTimeSeriesData.value = data;
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
