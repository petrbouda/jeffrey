<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert 
      v-if="isHttpDashboardDisabled"
      :title="mode === 'client' ? 'HTTP Client Dashboard' : 'HTTP Server Dashboard'"
      eventType="HTTP exchange"
    />

    <div v-else>
      <DashboardHeader :title="mode === 'client' ? 'HTTP Client Exchange' : 'HTTP Server Exchange'" icon="globe"/>

      <!-- Loading state -->
      <div v-if="isLoading" class="p-4 text-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="p-4 text-center">
      <div class="alert alert-danger" role="alert">
        Error loading HTTP data: {{ error }}
      </div>
    </div>

    <!-- Dashboard content -->
    <div v-if="httpOverviewData" class="dashboard-container">
      <!-- HTTP Overview Cards -->
      <DashboardSection :http-header="httpOverviewData.header"/>

      <!-- HTTP Metrics Timeline -->
      <HttpTimeseries
          :response-time-data="httpOverviewData?.responseTimeSerie.data || []"
          :request-count-data="httpOverviewData?.requestCountSerie.data || []"/>

      <!-- HTTP Endpoints List -->
      <HttpEndpointList
          :endpoints="httpOverviewData?.uris || []"
          :selected-endpoint="selectedEndpoint"
          @endpoint-click="navigateToUri"/>

      <!-- Status Codes and Methods Distribution -->
      <HttpDistributionCharts
          :status-codes="httpOverviewData?.statusCodes || []"
          :methods="httpOverviewData?.methods || []"
          :total-requests="httpOverviewData?.header.requestCount || 0"/>

      <!-- Slowest HTTP Requests -->
      <HttpSlowestRequests
          :requests="getSortedSlowRequests()"
          :total-request-count="httpOverviewData?.header.requestCount || 0"/>
    </div>

      <!-- No data state -->
      <div v-else class="p-4 text-center">
        <h3 class="text-muted">No HTTP Data Available</h3>
        <p class="text-muted">No HTTP exchange events found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {nextTick, onMounted, ref, computed, withDefaults, defineProps} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import DashboardHeader from '@/components/DashboardHeader.vue';
import HttpTimeseries from '@/components/http/HttpTimeseries.vue';
import HttpDistributionCharts from '@/components/http/HttpDistributionCharts.vue';
import HttpEndpointList from '@/components/http/HttpEndpointList.vue';
import HttpSlowestRequests from '@/components/http/HttpSlowestRequests.vue';
import ProfileHttpClient from '@/services/profile/custom/jdbc/ProfileHttpClient.ts';
import HttpOverviewData from '@/services/profile/custom/http/HttpOverviewData.ts';
import DashboardSection from "@/components/DashboardSection.vue";
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/profile/features/FeatureType';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const router = useRouter();
const { workspaceId, projectId } = useNavigation();

// Reactive state
const httpOverviewData = ref<HttpOverviewData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedEndpoint = ref<string | null>(null);

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Check if HTTP dashboard is disabled
const isHttpDashboardDisabled = computed(() => {
  const featureType = mode === 'client' ? FeatureType.HTTP_CLIENT_DASHBOARD : FeatureType.HTTP_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Client initialization - will be set after workspace/project IDs are available
let client: ProfileHttpClient;


// Helper functions
const getSortedSlowRequests = () => {
  if (!httpOverviewData.value) return [];

  // Sort slow requests by response time in descending order (slowest first)
  return [...httpOverviewData.value.slowRequests].sort((a, b) => b.responseTime - a.responseTime);
};

// Navigation method
const navigateToUri = (uri: string) => {
  router.push({
    name: 'profile-application-http-endpoints',
    query: {uri: encodeURIComponent(uri), mode: mode}
  });
};


// Lifecycle methods
const loadHttpData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    // Load data from API
    httpOverviewData.value = await client.getOverview();

    // Wait for DOM updates
    await nextTick();

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading HTTP data:', err);
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  // Only load data if the feature is not disabled
  if (!isHttpDashboardDisabled.value) {
    loadHttpData();
  }
});

</script>

<style scoped>
.dashboard-container {
  padding: 1.5rem;
}

@media (max-width: 768px) {
  .dashboard-container {
    padding: 1rem;
  }
}
</style>
