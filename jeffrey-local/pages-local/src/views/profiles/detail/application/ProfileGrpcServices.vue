<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
        v-if="isGrpcDashboardDisabled"
        :title="mode === 'client' ? 'gRPC Client Dashboard' : 'gRPC Server Dashboard'"
        eventType="gRPC exchange"
    />

    <div v-else>
      <PageHeader title="Service Details" icon="bi-diagram-3"/>

      <!-- Service Display with Navigation -->
      <div v-if="selectedServiceForDetail" class="service-display-large">
        <div class="service-content">
          <i class="bi bi-hdd-network me-2"></i>
          <span class="service-name">{{ decodeURIComponent(selectedServiceForDetail) }}</span>
        </div>

        <button @click="clearServiceSelection"
                class="btn btn-secondary service-back-button">
          <i class="bi bi-arrow-left me-2"></i>
          All Services
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
          Error loading gRPC data: {{ error }}
        </div>
      </div>

      <!-- Single Service Dashboard content -->
      <div v-if="selectedServiceForDetail && serviceDetailData" class="dashboard-container">
        <!-- Service Overview Cards -->
        <div class="mb-4">
          <StatsTable :metrics="serviceMetricsData"/>
        </div>

        <!-- Method Breakdown -->
        <GrpcMethodList
            :methods="serviceDetailData.methods || []"/>

        <!-- gRPC Metrics Timeline -->
        <GrpcTimeseries
            v-if="serviceDetailData"
            :response-time-data="serviceDetailData.responseTimeSerie.data"
            :call-count-data="serviceDetailData.callCountSerie.data"/>

        <!-- Status Code Distribution for this Service -->
        <div class="distribution-container">
          <PieChart
              title="Status Code Distribution"
              icon="pie-chart"
              :data="serviceStatusCodeData"
              :total="serviceDetailData.header.callCount || 0"
              :color-mapping="statusCodeColorMapping"
              :value-formatter="(val: number) => val + ' calls'"
          />
        </div>

        <!-- Slowest gRPC Calls -->
        <GrpcSlowestCalls
            :calls="slowestCalls"
            :total-call-count="serviceDetailData.header.callCount || 0"
            :max-displayed="20"/>
      </div>

      <!-- Service List -->
      <div v-else-if="grpcOverviewData" class="dashboard-container">
        <GrpcServiceList
            :services="grpcOverviewData?.services || []"
            :selected-service="selectedService"
            @service-click="selectServiceForDetail"/>
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
import {computed, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import GrpcTimeseries from '@/components/grpc/GrpcTimeseries.vue';
import GrpcServiceList from '@/components/grpc/GrpcServiceList.vue';
import GrpcMethodList from '@/components/grpc/GrpcMethodList.vue';
import GrpcSlowestCalls from '@/components/grpc/GrpcSlowestCalls.vue';
import PieChart from '@/components/PieChart.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type {GrpcOverviewData, GrpcServiceDetailData} from '@/services/api/ProfileGrpcClient';
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
const router = useRouter();

// Reactive state
const grpcOverviewData = ref<GrpcOverviewData | null>(null);
const serviceDetailData = ref<GrpcServiceDetailData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedService = ref<string | null>(null);
const selectedServiceForDetail = ref<string | null>(null);

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Check if gRPC dashboard is disabled
const isGrpcDashboardDisabled = computed(() => {
  const featureType = mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Client initialization
const client = new ProfileGrpcClient(mode, route.params.profileId as string);

// Computed metrics for service detail StatsTable
const serviceMetricsData = computed(() => {
  if (!serviceDetailData.value?.header) return [];

  const header = serviceDetailData.value.header;

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
      icon: 'check-circle',
      title: 'Success Rate',
      value: `${((header.successRate || 0) * 100).toFixed(1)}%`,
      variant: ((header.successRate || 0) === 1 ? 'success' : header.errorCount > 0 ? 'danger' : 'warning') as const,
      breakdown: [
        {
          label: 'Errors',
          value: header.errorCount || 0,
          color: header.errorCount > 0 ? '#EA4335' : '#28a745'
        }
      ]
    },
    {
      icon: 'arrow-down-up',
      title: 'Data Transferred',
      value: FormattingService.formatBytes((header.totalBytesSent || 0) + (header.totalBytesReceived || 0)),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Sent',
          value: header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent)
        },
        {
          label: 'Received',
          value: header.totalBytesReceived < 0 ? '?' : FormattingService.formatBytes(header.totalBytesReceived)
        }
      ]
    }
  ];
});

const slowestCalls = computed(() => {
  if (!serviceDetailData.value || !selectedServiceForDetail.value) return [];
  return [...serviceDetailData.value.slowCalls].sort((a, b) => b.responseTime - a.responseTime);
});

// Status code distribution data for the selected service
const serviceStatusCodeData = computed(() =>
    (serviceDetailData.value?.statusCodes || []).map(status => ({
      label: status.status,
      value: status.count
    }))
);

// Status code color mapping
const statusCodeColorMapping = (label: string): string => {
  switch (label) {
    case 'OK':
      return '#5cb85c';
    case 'CANCELLED':
      return '#f0ad4e';
    case 'INVALID_ARGUMENT':
    case 'NOT_FOUND':
    case 'ALREADY_EXISTS':
    case 'PERMISSION_DENIED':
    case 'FAILED_PRECONDITION':
    case 'OUT_OF_RANGE':
    case 'UNAUTHENTICATED':
      return '#f0ad4e';
    case 'UNKNOWN':
    case 'DEADLINE_EXCEEDED':
    case 'RESOURCE_EXHAUSTED':
    case 'ABORTED':
    case 'UNIMPLEMENTED':
    case 'INTERNAL':
    case 'UNAVAILABLE':
    case 'DATA_LOSS':
      return '#d9534f';
    default:
      return '#6c757d';
  }
};

// Service selection methods
const selectServiceForDetail = (service: string) => {
  selectedServiceForDetail.value = service;
  router.push({
    name: 'profile-application-grpc-services',
    query: {service: encodeURIComponent(service), mode: mode}
  });
};

const clearServiceSelection = () => {
  selectedServiceForDetail.value = null;
  router.push({
    name: 'profile-application-grpc-services',
    query: {mode: mode}
  });
};


// Lifecycle methods
const loadGrpcData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    if (selectedServiceForDetail.value) {
      // Load single service data from API
      serviceDetailData.value = await client.getServiceDetail(selectedServiceForDetail.value);

      // Check if the service data was loaded successfully
      if (!serviceDetailData.value) {
        error.value = `Service not found: ${decodeURIComponent(selectedServiceForDetail.value)}`;
      }
    } else {
      // Load overview data when no specific service is selected
      grpcOverviewData.value = await client.getOverview();
    }

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading gRPC data:', err);
  } finally {
    isLoading.value = false;
  }
};

// Watch for route changes to handle direct navigation
watch(() => route.query.service, (newService) => {
  if (newService && typeof newService === 'string') {
    selectedServiceForDetail.value = newService;
  } else {
    selectedServiceForDetail.value = null;
  }
  // Only reload data when service selection changes if feature is not disabled
  if (!isGrpcDashboardDisabled.value) {
    loadGrpcData();
  }
}, {immediate: true});
</script>

<style scoped>
.service-display-large {
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

.service-content {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 1.1rem;
  font-weight: 600;
  color: #2c3e50;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-width: 0;
  padding-left: 1rem;
}

.service-name {
  font-family: 'Courier New', monospace;
  font-size: 1rem;
  font-weight: 500;
}

.service-back-button {
  flex-shrink: 0;
  white-space: nowrap;
  margin-right: 1rem;
}

.distribution-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

@media (max-width: 768px) {
  .service-display-large {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
    padding: 0.75rem 1rem;
  }

  .service-content {
    font-size: 1rem;
  }

  .service-back-button {
    align-self: flex-start;
    order: -1;
  }

  .distribution-container {
    grid-template-columns: 1fr;
  }
}
</style>
