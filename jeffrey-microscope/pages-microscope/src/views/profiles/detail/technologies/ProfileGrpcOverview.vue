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
      <LoadingState v-if="isLoading" />

      <!-- Error state -->
      <ErrorState v-else-if="error" :message="error" />

      <!-- Dashboard content -->
      <div v-if="grpcOverviewData" class="dashboard-container">
        <div class="mb-4">
          <StatsTable :metrics="metricsData" />
        </div>
        <GrpcServiceList
          :services="grpcOverviewData?.services || []"
          :selected-service="selectedService"
          @service-click="navigateToService"
        />
      </div>

      <!-- No data state -->
      <div v-else class="p-4 text-center">
        <h3 class="text-muted">No gRPC Data Available</h3>
        <p class="text-muted">No gRPC exchange events found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import GrpcServiceList from '@/components/grpc/GrpcServiceList.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcOverviewData } from '@/services/api/ProfileGrpcClient';
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
const selectedService = ref<string | null>(null);

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
  if (!grpcOverviewData.value?.header) return [];

  const header = grpcOverviewData.value.header;

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
});

// Client initialization
const client = new ProfileGrpcClient(mode, route.params.profileId as string);

const {
  data: grpcOverviewData,
  isLoading,
  error
} = useTechnologyData<GrpcOverviewData>(() => client.getOverview(), isGrpcDashboardDisabled);

// Navigation method
const navigateToService = (service: string) => {
  router.push({
    name: 'profile-technologies-grpc-services',
    query: { service: encodeURIComponent(service), mode: mode }
  });
};
</script>

<style scoped></style>
