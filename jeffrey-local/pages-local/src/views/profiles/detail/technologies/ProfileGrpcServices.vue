<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
      v-if="isGrpcDashboardDisabled"
      :title="mode === 'client' ? 'gRPC Client Dashboard' : 'gRPC Server Dashboard'"
      eventType="gRPC exchange"
    />

    <div v-else>
      <GrpcOverviewStats
        v-if="grpcOverviewData && !selectedServiceForDetail"
        :header="grpcOverviewData.header"
      />

      <!-- Service Display with Navigation -->
      <DetailBreadcrumb
        v-if="selectedServiceForDetail"
        root-label="Services"
        @back="clearServiceSelection"
      >
        <span class="grpc-service-name">
          <span class="grpc-package">{{
            getPackageName(decodeURIComponent(selectedServiceForDetail))
          }}</span
          >{{ getSimpleName(decodeURIComponent(selectedServiceForDetail)) }}
        </span>
      </DetailBreadcrumb>

      <!-- Loading state -->
      <LoadingState v-if="isLoading" />

      <!-- Error state -->
      <ErrorState v-else-if="error" :message="error" />

      <!-- Single Service Dashboard content -->
      <div v-if="selectedServiceForDetail && serviceDetailData" class="dashboard-container">
        <StatsTable :metrics="serviceMetricsData" class="mb-4" />

        <ChartSectionWithTabs :tabs="serviceTabs" :full-width="true" id-prefix="grpc-service-">
          <template #timeseries>
            <TimeSeriesChart
              :primary-data="serviceDetailData.responseTimeSerie.data"
              primary-title="Response Time"
              :secondary-data="serviceDetailData.callCountSerie.data"
              secondary-title="Call Count"
              :visible-minutes="60"
              :independentSecondaryAxis="true"
              :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
              :secondary-axis-type="AxisFormatType.NUMBER"
            />
          </template>

          <template #distribution>
            <DualPanel left-title="Status Code Distribution" embedded>
              <template #left>
                <DonutWithLegend
                  :data="statusCodeChartData"
                  :tooltip-formatter="(val: number) => val + ' calls'"
                />
              </template>
            </DualPanel>
          </template>

          <template #slowest>
            <GrpcSlowestCalls
              :calls="slowestCalls"
              :total-call-count="serviceDetailData.header.callCount || 0"
              :max-displayed="20"
            />
          </template>
        </ChartSectionWithTabs>
      </div>

      <!-- Service List -->
      <div v-else-if="grpcOverviewData" class="dashboard-container">
        <GrpcServiceList
          :services="grpcOverviewData?.services || []"
          :selected-service="selectedService"
          @service-click="selectServiceForDetail"
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
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DetailBreadcrumb from '@/components/DetailBreadcrumb.vue';
import GrpcOverviewStats from '@/components/grpc/GrpcOverviewStats.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import StatsTable from '@/components/StatsTable.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import GrpcServiceList from '@/components/grpc/GrpcServiceList.vue';

import GrpcSlowestCalls from '@/components/grpc/GrpcSlowestCalls.vue';
import DualPanel from '@/components/DualPanel.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import type { DonutChartData } from '@/components/DonutWithLegend.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcOverviewData, GrpcServiceDetailData } from '@/services/api/ProfileGrpcClient';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
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

// Tab definitions for service detail view
const serviceTabs = [
  { id: 'timeseries', label: 'Timeseries', icon: 'graph-up' },
  { id: 'distribution', label: 'Distribution', icon: 'pie-chart' },
  { id: 'slowest', label: 'Slowest Calls', icon: 'clock-history' }
];

// Reactive state
const grpcOverviewData = ref<GrpcOverviewData | null>(null);
const serviceDetailData = ref<GrpcServiceDetailData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedService = ref<string | null>(null);
const selectedServiceForDetail = ref<string | null>(null);

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Service name helpers
const getPackageName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(0, lastDot + 1) : '';
};

const getSimpleName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
};

// Check if gRPC dashboard is disabled
const isGrpcDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
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
      value: FormattingService.formatSuccessRate(header.successRate || 0),
      variant: ((header.successRate || 0) === 1
        ? 'success'
        : header.errorCount > 0
          ? 'danger'
          : 'warning') as const,
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
      value: FormattingService.formatBytes(
        (header.totalBytesSent || 0) + (header.totalBytesReceived || 0)
      ),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Sent',
          value:
            header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent)
        },
        {
          label: 'Received',
          value:
            header.totalBytesReceived < 0
              ? '?'
              : FormattingService.formatBytes(header.totalBytesReceived)
        }
      ]
    }
  ];
});

const slowestCalls = computed(() => {
  if (!serviceDetailData.value || !selectedServiceForDetail.value) return [];
  return [...serviceDetailData.value.slowCalls].sort((a, b) => b.responseTime - a.responseTime);
});

// Status code color mapping
const grpcStatusColor = (status: string): string => {
  switch (status) {
    case 'OK':
      return '#5cb85c';
    case 'CANCELLED':
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

const statusCodeChartData = computed<DonutChartData>(() => {
  const codes = serviceDetailData.value?.statusCodes || [];
  return {
    series: codes.map(s => s.count),
    labels: codes.map(s => s.status),
    colors: codes.map(s => grpcStatusColor(s.status)),
    totalValue: FormattingService.formatNumber(serviceDetailData.value?.header.callCount || 0),
    legendItems: codes.map(s => ({
      color: grpcStatusColor(s.status),
      label: s.status,
      value: FormattingService.formatNumber(s.count)
    }))
  };
});

// Service selection methods
const selectServiceForDetail = (service: string) => {
  selectedServiceForDetail.value = service;
  router.push({
    name: 'profile-technologies-grpc-services',
    query: { service: encodeURIComponent(service), mode: mode }
  });
};

const clearServiceSelection = () => {
  selectedServiceForDetail.value = null;
  router.push({
    name: 'profile-technologies-grpc-services',
    query: { mode: mode }
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
watch(
  () => route.query.service,
  newService => {
    if (newService && typeof newService === 'string') {
      selectedServiceForDetail.value = newService;
    } else {
      selectedServiceForDetail.value = null;
    }
    // Only reload data when service selection changes if feature is not disabled
    if (!isGrpcDashboardDisabled.value) {
      loadGrpcData();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.grpc-package {
  color: var(--color-text-muted);
  font-weight: 400;
  font-style: italic;
}
</style>
