<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert 
      v-if="isHttpDashboardDisabled"
      :title="mode === 'client' ? 'HTTP Client Dashboard' : 'HTTP Server Dashboard'"
      eventType="HTTP exchange"
    />

    <div v-else>
      <PageHeader title="Endpoint Details" icon="bi-share" />

    <!-- URI Display with Navigation -->
    <div v-if="selectedUriForDetail" class="uri-display-large">
      <div class="uri-content">
        <span class="uri-separator">/</span>
        <span v-for="(part, index) in parseUri(decodeURIComponent(selectedUriForDetail))" :key="index" class="uri-part">
          <span v-if="index > 0" class="uri-separator">/</span>
          <span v-if="part.isVariable" class="uri-variable">{{ part.text }}</span>
          <span v-else class="uri-segment">{{ part.text }}</span>
        </span>
      </div>

      <button @click="clearUriSelection"
          class="btn btn-secondary uri-back-button">
        <i class="bi bi-arrow-left me-2"></i>
        All Endpoints
      </button>
    </div>

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

    <!-- Single URI Dashboard content -->
    <div v-if="selectedUriForDetail && singleUriData" class="dashboard-container">
      <!-- URI Overview Cards -->
      <DashboardSection :http-header="singleUriData.header"/>

      <!-- HTTP Metrics Timeline -->
      <HttpTimeseries
          v-if="singleUriData"
          :response-time-data="singleUriData.responseTimeSerie.data"
          :request-count-data="singleUriData.requestCountSerie.data"/>

      <!-- Status Codes and Methods Distribution for this URI -->
      <HttpDistributionCharts
          v-if="singleUriData?.statusCodes && singleUriData?.methods"
          :status-codes="singleUriData.statusCodes"
          :methods="singleUriData.methods"
          :total-requests="singleUriData.uri.requestCount"/>

      <!-- Slowest HTTP Requests -->
      <HttpSlowestRequests
          :requests="slowestRequests"
          :total-request-count="singleUriData.header.requestCount || 0"
          :max-displayed="20"/>
    </div>

    <!-- Endpoint List -->
    <div v-else-if="httpOverviewData" class="dashboard-container">
      <HttpEndpointList
          :endpoints="httpOverviewData?.uris || []"
          :selected-endpoint="selectedEndpoint"
          @endpoint-click="selectUriForDetail"/>
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
import {computed, ref, watch, withDefaults, defineProps} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import DashboardSection from '@/components/DashboardSection.vue';
import HttpTimeseries from '@/components/http/HttpTimeseries.vue';
import HttpDistributionCharts from '@/components/http/HttpDistributionCharts.vue';
import HttpEndpointList from '@/components/http/HttpEndpointList.vue';
import HttpSlowestRequests from '@/components/http/HttpSlowestRequests.vue';
import ProfileHttpClient from '@/services/api/ProfileHttpClient.ts';
import HttpOverviewData from '@/services/api/model/HttpOverviewData.ts';
import HttpSingleUriData from '@/services/api/model/HttpSingleUriData.ts';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const router = useRouter();

// Reactive state
const httpOverviewData = ref<HttpOverviewData | null>(null);
const singleUriData = ref<HttpSingleUriData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedEndpoint = ref<string | null>(null);
const selectedUriForDetail = ref<string | null>(null);

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Check if HTTP dashboard is disabled
const isHttpDashboardDisabled = computed(() => {
  const featureType = mode === 'client' ? FeatureType.HTTP_CLIENT_DASHBOARD : FeatureType.HTTP_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Client initialization
const client = new ProfileHttpClient(
  mode,
  route.params.workspaceId as string,
  route.params.projectId as string,
  route.params.profileId as string
);

const slowestRequests = computed(() => {
  if (!singleUriData.value || !selectedUriForDetail.value) return [];
  return singleUriData.value.slowRequests.sort((a, b) => b.responseTime - a.responseTime);
});

// Helper functions
const parseUri = (uri: string) => {
  if (!uri) return [];

  const segments = uri.split('/').filter(segment => segment.length > 0);

  return segments.map(segment => ({
    text: segment,
    isVariable: segment.startsWith('{') && segment.endsWith('}')
  }));
};

// URI selection methods
const selectUriForDetail = (uri: string) => {
  selectedUriForDetail.value = uri;
  router.push({
    name: 'profile-application-http-endpoints',
    query: {uri: encodeURIComponent(uri), mode: mode}
  });
};

const clearUriSelection = () => {
  selectedUriForDetail.value = null;
  router.push({
    name: 'profile-application-http-endpoints',
    query: {mode: mode}
  });
};


// Lifecycle methods
const loadHttpData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    if (selectedUriForDetail.value) {
      // Load single URI data from API
      singleUriData.value = await client.getOverviewUri(selectedUriForDetail.value);

      // Check if the URI data was loaded successfully
      if (!singleUriData.value) {
        error.value = `URI not found: ${decodeURIComponent(selectedUriForDetail.value)}`;
      }
    } else {
      // Load overview data when no specific URI is selected
      httpOverviewData.value = await client.getOverview();
    }

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading HTTP data:', err);
  } finally {
    isLoading.value = false;
  }
};

// Watch for route changes to handle direct navigation
watch(() => route.query.uri, (newUri) => {
  if (newUri && typeof newUri === 'string') {
    selectedUriForDetail.value = newUri;
  } else {
    selectedUriForDetail.value = null;
  }
  // Only reload data when URI selection changes if feature is not disabled
  if (!isHttpDashboardDisabled.value) {
    loadHttpData();
  }
}, {immediate: true});
</script>

<style scoped>
.uri-display-large {
  background: #f8f9ff;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  margin: 1.5rem 0;
  padding: 1rem 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.uri-content {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 1.1rem;
  font-weight: 600;
  color: #2c3e50;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.25rem;
  flex: 1;
  min-width: 0;
}

.uri-back-button {
  flex-shrink: 0;
  white-space: nowrap;
  margin-right: 1rem;
}

@media (max-width: 768px) {
  .uri-display-large {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
    padding: 0.75rem 1rem;
  }

  .uri-content {
    font-size: 1rem;
  }

  .uri-back-button {
    align-self: flex-start;
    order: -1;
  }
}
</style>
