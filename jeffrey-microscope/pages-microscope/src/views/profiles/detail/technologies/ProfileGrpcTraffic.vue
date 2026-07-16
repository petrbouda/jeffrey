<template>
  <TechnologyDashboard
    :fetch="() => client.getTraffic()"
    :disabled="isGrpcDashboardDisabled"
    :disabled-title="mode === 'client' ? 'gRPC Client Dashboard' : 'gRPC Server Dashboard'"
    event-type="gRPC exchange"
    no-data-title="No gRPC Traffic Data Available"
    no-data-message="No gRPC traffic data found for this profile"
  >
    <template #default="{ data }">
      <StatsTable :metrics="metricsData(data)" />
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcTrafficData } from '@/services/api/ProfileGrpcClient';
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

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Check if gRPC dashboard is disabled
const isGrpcDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Metrics for StatsTable derived from the loaded traffic data
const metricsData = (data: GrpcTrafficData) => {
  const header = data.header;

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
};

// Client initialization
const client = new ProfileGrpcClient(mode, route.params.profileId as string);
</script>
