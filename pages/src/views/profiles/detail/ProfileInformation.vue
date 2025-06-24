<template>
  <div v-if="!profile" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading profile information...</p>
  </div>

  <div v-else class="profile-information">
    <DashboardHeader
        title="Information"
        description="View profile information and metrics"
        icon="info-circle"
    />

    <div class="card mt-4">
      <div class="card-header">
        <h5 class="card-title mb-0">Profile Information</h5>
      </div>
      <div class="card-body">
        <div class="chart-container">
          <ApexTimeSeriesChart
              :primaryData="timeSeriesData"
              :secondaryData="secondaryTimeSeriesData"
              primaryTitle="CPU Usage (%)"
              secondaryTitle="Memory Usage"
              :independentSecondaryAxis="true"
              :visibleMinutes="15"
              primaryAxisType="number"
              secondaryAxisType="bytes"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import Profile from '@/services/model/Profile';
import DashboardHeader from '@/components/DashboardHeader.vue';

// Define props
defineProps<{
  profile?: Profile | null;
}>();

// Chart loading state (removed since ApexTimeSeriesChart handles loading internally)
// const chartLoading = ref<boolean>(true);

// Time series data for both series
const timeSeriesData = ref<number[][]>([]);
const secondaryTimeSeriesData = ref<number[][]>([]);

// Generate mock time series data
onMounted(() => {
  // Generate data immediately for 12 hours (720 minutes)
  generateMockedData(720); // 12 hours of data
  generateSecondaryMockedData(720); // 12 hours of secondary data
});

// Generate mocked CPU usage data (0-100%)
const generateMockedData = (durationInMinutes: number): void => {
  const data: number[][] = [];
  const secondsTotal = durationInMinutes * 60;

  // Base CPU usage around 45%
  let value = 45;
  const hourInSeconds = 3600;
  const dayPattern = 12 * hourInSeconds;

  // Create CPU usage data points
  for (let i = 0; i <= secondsTotal; i++) {
    // Time of day variations - CPU usage patterns throughout the day
    const hourOfDay = (i % dayPattern) / hourInSeconds;
    const timeOfDayComponent = 25 * Math.sin((hourOfDay / 12) * Math.PI);

    // Medium frequency variations - simulating workload cycles
    const mediumComponent = 15 * Math.sin(i / 1800) + 8 * Math.cos(i / 900);

    // Higher frequency variations - representing short bursts
    const shortComponent = 5 * Math.sin(i / 300) + 3 * Math.cos(i / 150);

    // Random noise
    const randomComponent = Math.floor(Math.random() * 6) - 3;

    // Combine components
    value += randomComponent + (timeOfDayComponent / 50) + (mediumComponent / 25) + (shortComponent / 10);

    // Keep within CPU percentage range (0-100%)
    value = Math.max(5, Math.min(95, value));

    // Add occasional CPU spikes
    if (Math.random() < 0.0005) {
      value = Math.min(98, value + Math.random() * 40);
    }

    data.push([i, Math.round(value * 10) / 10]); // Round to 1 decimal
  }

  timeSeriesData.value = data;
};

// Generate memory usage data (in bytes, much larger scale than CPU %)
const generateSecondaryMockedData = (durationInMinutes: number): void => {
  const data: number[][] = [];
  const secondsTotal = durationInMinutes * 60;

  // Base memory usage around 2.5GB (2.5 * 1024 * 1024 * 1024 bytes)
  let value = 2.5 * 1024 * 1024 * 1024;
  const hourInSeconds = 3600;
  const dayPattern = 12 * hourInSeconds;

  // Create memory usage data points with different scale and pattern
  for (let i = 0; i <= secondsTotal; i++) {
    // Time of day variations - memory usage typically grows during day
    const hourOfDay = (i % dayPattern) / hourInSeconds;
    const timeOfDayComponent = 800 * 1024 * 1024 * Math.sin((hourOfDay / 12) * Math.PI + 0.5); // 800MB variation

    // Medium frequency variations - garbage collection cycles
    const mediumComponent = 300 * 1024 * 1024 * Math.sin(i / 2400 + 1.5) + 150 * 1024 * 1024 * Math.cos(i / 1200 + 0.8); // 300MB + 150MB

    // Higher frequency variations - allocation/deallocation patterns
    const shortComponent = 100 * 1024 * 1024 * Math.sin(i / 400 + 0.3) + 50 * 1024 * 1024 * Math.cos(i / 180 + 0.6); // 100MB + 50MB

    // Random noise (in bytes)
    const randomComponent = Math.floor(Math.random() * 50 * 1024 * 1024) - 25 * 1024 * 1024; // ±25MB

    // Combine components
    value += randomComponent + (timeOfDayComponent / 100) + (mediumComponent / 50) + (shortComponent / 30);

    // Keep within realistic memory range (1-6GB)
    const oneGB = 1024 * 1024 * 1024;
    value = Math.max(oneGB, Math.min(6 * oneGB, value));

    // Add occasional memory pressure events
    if (Math.random() < 0.0003) {
      value = Math.min(5.8 * oneGB, value + Math.random() * 1.2 * oneGB);
    }

    // Add occasional garbage collection drops
    if (Math.random() < 0.0004) {
      value = Math.max(1.2 * oneGB, value - Math.random() * 0.8 * oneGB);
    }

    data.push([i, Math.round(value)]);
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
  min-height: 460px;
  position: relative;
  overflow: hidden;
  resize: horizontal; /* Allow user to resize horizontally for testing */
}
</style>
