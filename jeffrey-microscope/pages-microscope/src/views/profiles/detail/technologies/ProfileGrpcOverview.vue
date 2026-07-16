<template>
  <TechnologyDashboard
    :fetch="() => client.getOverview()"
    :disabled="isGrpcDashboardDisabled"
    :disabled-title="mode === 'client' ? 'gRPC Client Dashboard' : 'gRPC Server Dashboard'"
    event-type="gRPC exchange"
    no-data-title="No gRPC Data Available"
    no-data-message="No gRPC exchange events found for this profile"
  >
    <template #default="{ data }">
      <div class="mb-4">
        <StatsTable :metrics="metricsData(data)" />
      </div>
      <GrpcServiceList
        :services="data.services || []"
        :selected-service="selectedService"
        @service-click="navigateToService"
      />
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import GrpcServiceList from '@/components/grpc/GrpcServiceList.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcOverviewData } from '@/services/api/ProfileGrpcClient';
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
const selectedService = ref<string | null>(null);

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Check if gRPC dashboard is disabled
const isGrpcDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Metrics for StatsTable derived from the loaded overview data
const metricsData = (data: GrpcOverviewData) => {
  const header = data.header;

  return [
    {
      icon: 'telephone',
      title: 'Total Calls',
      value: header.callCount || 0,
      variant: 'info' as const
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
          : header.errorCount > 0
            ? ('danger' as const)
            : ('warning' as const),
      breakdown: [
        {
          label: 'Errors',
          value: header.errorCount || 0,
          color: header.errorCount > 0 ? '#EA4335' : '#28a745'
        }
      ]
    },
    {
      icon: 'arrow-left-right',
      title: 'Data Transferred',
      value: FormattingService.formatBytes(
        (header.totalBytesSent || 0) + (header.totalBytesReceived || 0)
      ),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Sent',
          value:
            header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent),
          color: '#34A853'
        },
        {
          label: 'Received',
          value:
            header.totalBytesReceived < 0
              ? '?'
              : FormattingService.formatBytes(header.totalBytesReceived),
          color: '#34A853'
        }
      ]
    }
  ];
};

// Client initialization
const client = new ProfileGrpcClient(mode, route.params.profileId as string);

// Navigation method
const navigateToService = (service: string) => {
  router.push({
    name: 'profile-technologies-grpc-services',
    query: { service: encodeURIComponent(service), mode: mode }
  });
};
</script>
