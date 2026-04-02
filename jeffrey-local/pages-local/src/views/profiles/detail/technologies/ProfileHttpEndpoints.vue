<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
      v-if="isHttpDashboardDisabled"
      :title="mode === 'client' ? 'HTTP Client Dashboard' : 'HTTP Server Dashboard'"
      eventType="HTTP exchange"
    />

    <div v-else>
      <HttpOverviewStats v-if="httpOverviewData && !selectedUriForDetail" :header="httpOverviewData.header" />

      <!-- URI Display with Navigation -->
      <DetailBreadcrumb
        v-if="selectedUriForDetail"
        root-label="Endpoints"
        @back="clearUriSelection"
      >
        <span class="uri-path">
          <span class="uri-separator">/</span>
          <span
            v-for="(part, index) in parseUri(decodeURIComponent(selectedUriForDetail))"
            :key="index"
          >
            <span v-if="index > 0" class="uri-separator">/</span>
            <span v-if="part.isVariable" class="uri-variable">{{ part.text }}</span>
            <span v-else>{{ part.text }}</span>
          </span>
        </span>
      </DetailBreadcrumb>

      <!-- Loading state -->
      <div v-if="isLoading" class="p-4 text-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <!-- Error state -->
      <div v-else-if="error" class="p-4 text-center">
        <div class="alert alert-danger" role="alert">Error loading HTTP data: {{ error }}</div>
      </div>

      <!-- Single URI Dashboard content -->
      <div v-if="selectedUriForDetail && singleUriData" class="dashboard-container">
        <!-- URI Stats Row (above tabs, like GCMetricsStatsRow) -->
        <DashboardSection :http-header="singleUriData.header" />

        <!-- Tabbed Analysis -->
        <ChartSectionWithTabs :tabs="httpDetailTabs" :full-width="true" id-prefix="http-detail-">
          <template #timeseries>
            <TimeSeriesChart
              :primary-data="singleUriData.responseTimeSerie.data"
              primary-title="Response Time"
              :secondary-data="singleUriData.requestCountSerie.data"
              secondary-title="Request Count"
              :visible-minutes="60"
              :independentSecondaryAxis="true"
              :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
              :secondary-axis-type="AxisFormatType.NUMBER"
            />
          </template>

          <template #distribution>
            <HttpDistributionCharts
              v-if="singleUriData?.statusCodes && singleUriData?.methods"
              :status-codes="singleUriData.statusCodes"
              :methods="singleUriData.methods"
              :total-requests="singleUriData.uri.requestCount"
              embedded
            />
          </template>

          <template #slowest>
            <EmptyState
              v-if="slowestRequests.length === 0"
              icon="bi-clock-history"
              title="No slow HTTP requests"
            />
            <HttpSlowestRequests
              v-else
              :requests="slowestRequests"
              :total-request-count="singleUriData.header.requestCount || 0"
              :max-displayed="20"
            />
          </template>
        </ChartSectionWithTabs>
      </div>

      <!-- Endpoint List -->
      <div v-else-if="httpOverviewData" class="dashboard-container">
        <HttpEndpointList
          :endpoints="httpOverviewData?.uris || []"
          :selected-endpoint="selectedEndpoint"
          @endpoint-click="selectUriForDetail"
        />
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
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DetailBreadcrumb from '@/components/DetailBreadcrumb.vue';
import HttpOverviewStats from '@/components/http/HttpOverviewStats.vue';
import DashboardSection from '@/components/DashboardSection.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import HttpDistributionCharts from '@/components/http/HttpDistributionCharts.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import HttpEndpointList from '@/components/http/HttpEndpointList.vue';
import HttpSlowestRequests from '@/components/http/HttpSlowestRequests.vue';
import EmptyState from '@/components/EmptyState.vue';
import ProfileHttpClient from '@/services/api/ProfileHttpClient.ts';
import HttpOverviewData from '@/services/api/model/HttpOverviewData.ts';
import HttpSingleUriData from '@/services/api/model/HttpSingleUriData.ts';
import HttpSlowRequest from '@/services/api/model/HttpSlowRequest.ts';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

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

// Tab definitions for URI detail view
const httpDetailTabs = [
  { id: 'timeseries', label: 'Timeseries', icon: 'graph-up' },
  { id: 'distribution', label: 'Distribution', icon: 'pie-chart' },
  { id: 'slowest', label: 'Slowest Requests', icon: 'clock-history' }
];

// Check if HTTP dashboard is disabled
const isHttpDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.HTTP_CLIENT_DASHBOARD : FeatureType.HTTP_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Client initialization
const client = new ProfileHttpClient(mode, route.params.profileId as string);

const slowestRequests = computed(() => {
  if (!singleUriData.value || !selectedUriForDetail.value) return [];
  return singleUriData.value.slowRequests.sort(
    (a: HttpSlowRequest, b: HttpSlowRequest) => b.responseTime - a.responseTime
  );
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
    name: 'profile-technologies-http-endpoints',
    query: { uri: encodeURIComponent(uri), mode: mode }
  });
};

const clearUriSelection = () => {
  selectedUriForDetail.value = null;
  router.push({
    name: 'profile-technologies-http-endpoints',
    query: { mode: mode }
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
watch(
  () => route.query.uri,
  newUri => {
    if (newUri && typeof newUri === 'string') {
      selectedUriForDetail.value = newUri;
    } else {
      selectedUriForDetail.value = null;
    }
    // Only reload data when URI selection changes if feature is not disabled
    if (!isHttpDashboardDisabled.value) {
      loadHttpData();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.uri-separator {
  color: var(--color-text-muted);
  font-weight: 400;
}

.uri-variable {
  color: var(--color-primary);
  font-style: italic;
}
</style>
