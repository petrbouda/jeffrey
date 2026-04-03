<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
      v-if="isHttpDashboardDisabled"
      :title="mode === 'client' ? 'HTTP Client Dashboard' : 'HTTP Server Dashboard'"
      eventType="HTTP exchange"
    />

    <div v-else>
      <!-- Loading state -->
      <LoadingState v-if="isLoading" />

      <!-- Error state -->
      <ErrorState v-else-if="error" :message="error" />

      <!-- Dashboard content -->
      <div v-if="httpOverviewData" class="dashboard-container">
        <!-- HTTP Overview Cards -->
        <div class="mb-4">
          <StatsTable :metrics="metricsData" />
        </div>

        <!-- HTTP Metrics Timeline -->
        <HttpTimeseries
          :response-time-data="httpOverviewData?.responseTimeSerie.data || []"
          :request-count-data="httpOverviewData?.requestCountSerie.data || []"
        />

        <!-- HTTP Endpoints List -->
        <HttpEndpointList
          :endpoints="httpOverviewData?.uris || []"
          :selected-endpoint="selectedEndpoint"
          @endpoint-click="navigateToUri"
        />

        <!-- Status Codes and Methods Distribution -->
        <HttpDistributionCharts
          title="HTTP Distribution"
          icon="pie-chart"
          :status-codes="httpOverviewData?.statusCodes || []"
          :methods="httpOverviewData?.methods || []"
          :total-requests="httpOverviewData?.header.requestCount || 0"
        />

        <!-- Slowest HTTP Requests -->
        <HttpSlowestRequests
          :requests="getSortedSlowRequests()"
          :total-request-count="httpOverviewData?.header.requestCount || 0"
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
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import HttpTimeseries from '@/components/http/HttpTimeseries.vue';
import HttpDistributionCharts from '@/components/http/HttpDistributionCharts.vue';
import HttpEndpointList from '@/components/http/HttpEndpointList.vue';
import HttpSlowestRequests from '@/components/http/HttpSlowestRequests.vue';
import ProfileHttpClient from '@/services/api/ProfileHttpClient.ts';
import HttpOverviewData from '@/services/api/model/HttpOverviewData.ts';
import StatsTable from '@/components/StatsTable.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';
import FormattingService from '@/services/FormattingService';
import { useTechnologyData } from '@/composables/useTechnologyData';

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
const selectedEndpoint = ref<string | null>(null);

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Check if HTTP dashboard is disabled
const isHttpDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.HTTP_CLIENT_DASHBOARD : FeatureType.HTTP_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Computed metrics for StatsTable
const metricsData = computed(() => {
  if (!httpOverviewData.value?.header) return [];

  const header = httpOverviewData.value.header;

  return [
    {
      icon: 'globe',
      title: 'Total Requests',
      value: header.requestCount || 0,
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Requests',
          value: header.requestCount || 0,
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Response Time',
      value: FormattingService.formatDuration2Units(header.maxResponseTime),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'P99',
          value: FormattingService.formatDuration2Units(header.p99ResponseTime)
        },
        {
          label: 'P95',
          value: FormattingService.formatDuration2Units(header.p95ResponseTime)
        }
      ]
    },
    {
      icon: 'check-circle-fill',
      title: 'Success Rate',
      value: `${((header.successRate || 0) * 100).toFixed(1)}%`,
      variant:
        (header.successRate || 0) === 1
          ? ('success' as const)
          : header.count5xx > 0
            ? ('danger' as const)
            : ('warning' as const),
      breakdown: [
        {
          label: '4xx Errors',
          value: header.count4xx || 0,
          color: '#EA4335'
        },
        {
          label: '5xx Errors',
          value: header.count5xx || 0,
          color: '#EA4335'
        }
      ]
    },
    {
      icon: 'arrow-left-right',
      title: 'Data Transferred',
      value:
        header.totalBytesTransferred < 0
          ? '?'
          : FormattingService.formatBytes(header.totalBytesTransferred),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Received',
          value:
            header.totalBytesReceived < 0
              ? '?'
              : FormattingService.formatBytes(header.totalBytesReceived),
          color: '#34A853'
        },
        {
          label: 'Sent',
          value:
            header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent),
          color: '#34A853'
        }
      ]
    }
  ];
});

// Client initialization
const client = new ProfileHttpClient(mode, route.params.profileId as string);

const {
  data: httpOverviewData,
  isLoading,
  error
} = useTechnologyData<HttpOverviewData>(() => client.getOverview(), isHttpDashboardDisabled);

// Helper functions
const getSortedSlowRequests = () => {
  if (!httpOverviewData.value) return [];

  // Sort slow requests by response time in descending order (slowest first)
  return [...httpOverviewData.value.slowRequests].sort((a, b) => b.responseTime - a.responseTime);
};

// Navigation method
const navigateToUri = (uri: string) => {
  router.push({
    name: 'profile-technologies-http-endpoints',
    query: { uri: encodeURIComponent(uri), mode: mode }
  });
};
</script>

<style scoped></style>
