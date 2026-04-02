<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
      v-if="isGrpcDashboardDisabled"
      :title="mode === 'client' ? 'gRPC Client Dashboard' : 'gRPC Server Dashboard'"
      eventType="gRPC exchange"
    />

    <div v-else>
      <!-- Loading state -->
      <div v-if="isLoading" class="p-4 text-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <!-- Error state -->
      <div v-else-if="error" class="p-4 text-center">
        <div class="alert alert-danger" role="alert">
          Error loading gRPC traffic data: {{ error }}
        </div>
      </div>

      <!-- Dashboard content -->
      <div v-if="trafficData" class="dashboard-container">
        <StatsTable :metrics="metricsData" />
      </div>

      <!-- No data state -->
      <div v-else class="p-4 text-center">
        <h3 class="text-muted">No gRPC Traffic Data Available</h3>
        <p class="text-muted">No gRPC traffic data found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcTrafficData } from '@/services/api/ProfileGrpcClient';
import StatsTable from '@/components/StatsTable.vue';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';
import FormattingService from '@/services/FormattingService';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();

// Reactive state
const trafficData = ref<GrpcTrafficData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Check if gRPC dashboard is disabled
const isGrpcDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Computed metrics for StatsTable
const metricsData = computed(() => {
  if (!trafficData.value?.header) return [];

  const header = trafficData.value.header;

  return [
    {
      icon: 'box-arrow-up',
      title: 'Avg Request Size',
      value: FormattingService.formatBytes(header.avgRequestSize),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Max',
          value: FormattingService.formatBytes(header.maxRequestSize),
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'box-arrow-down',
      title: 'Avg Response Size',
      value: FormattingService.formatBytes(header.avgResponseSize),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Max',
          value: FormattingService.formatBytes(header.maxResponseSize),
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'box-arrow-up',
      title: 'Max Request Size',
      value: FormattingService.formatBytes(header.maxRequestSize),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Total Sent',
          value:
            header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent),
          color: '#FBBC05'
        }
      ]
    },
    {
      icon: 'box-arrow-down',
      title: 'Max Response Size',
      value: FormattingService.formatBytes(header.maxResponseSize),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Total Received',
          value:
            header.totalBytesReceived < 0
              ? '?'
              : FormattingService.formatBytes(header.totalBytesReceived),
          color: '#FBBC05'
        }
      ]
    }
  ];
});

// Client initialization
const client = new ProfileGrpcClient(mode, route.params.profileId as string);

// Lifecycle methods
const loadTrafficData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    // Load data from API
    trafficData.value = await client.getTraffic();

    // Wait for DOM updates
    await nextTick();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading gRPC traffic data:', err);
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  // Only load data if the feature is not disabled
  if (!isGrpcDashboardDisabled.value) {
    loadTrafficData();
  }
});
</script>

<style scoped></style>
