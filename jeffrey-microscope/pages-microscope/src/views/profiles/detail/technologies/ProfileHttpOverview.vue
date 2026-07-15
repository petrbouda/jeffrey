<template>
  <TechnologyDashboard
    :fetch="() => client.getOverview()"
    :disabled="isHttpDashboardDisabled"
    :disabled-title="mode === 'client' ? 'HTTP Client Dashboard' : 'HTTP Server Dashboard'"
    event-type="HTTP exchange"
    no-data-title="No HTTP Data Available"
    no-data-message="No HTTP exchange events found for this profile"
  >
    <template #default="{ data }">
      <!-- HTTP Overview Cards -->
      <div class="mb-4">
        <StatsTable :metrics="metricsData(data)" />
      </div>

      <!-- HTTP Metrics Timeline -->
      <HttpTimeseries
        :response-time-data="data.responseTimeSerie.data || []"
        :request-count-data="data.requestCountSerie.data || []"
      />

      <!-- HTTP Endpoints List -->
      <HttpEndpointList
        :endpoints="data.uris || []"
        :selected-endpoint="selectedEndpoint"
        @endpoint-click="navigateToUri"
      />

      <!-- Status Codes and Methods Distribution -->
      <HttpDistributionCharts
        title="HTTP Distribution"
        icon="pie-chart"
        :status-codes="data.statusCodes || []"
        :methods="data.methods || []"
        :total-requests="data.header.requestCount || 0"
      />

      <!-- Slowest HTTP Requests -->
      <HttpSlowestRequests
        :requests="getSortedSlowRequests(data)"
        :total-request-count="data.header.requestCount || 0"
      />
    </template>
  </TechnologyDashboard>
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
import StatsTable from '@shared/components/table/StatsTable.vue';
import TechnologyDashboard from '@/components/technologies/TechnologyDashboard.vue';
import FeatureType from '@/services/api/model/FeatureType';
import FormattingService from '@shared/services/FormattingService';

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

// Metrics for StatsTable derived from the loaded overview data
const metricsData = (data: HttpOverviewData) => {
  const header = data.header;

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
      value: FormattingService.formatSuccessRate(header.successRate || 0),
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
};

// Client initialization
const client = new ProfileHttpClient(mode, route.params.profileId as string);

// Helper functions
const getSortedSlowRequests = (data: HttpOverviewData) => {
  // Sort slow requests by response time in descending order (slowest first)
  return [...data.slowRequests].sort((a, b) => b.responseTime - a.responseTime);
};

// Navigation method
const navigateToUri = (uri: string) => {
  router.push({
    name: 'profile-technologies-http-endpoints',
    query: { uri: encodeURIComponent(uri), mode: mode }
  });
};
</script>
