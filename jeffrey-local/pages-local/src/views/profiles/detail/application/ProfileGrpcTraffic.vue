<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
        v-if="isGrpcDashboardDisabled"
        :title="mode === 'client' ? 'gRPC Client Dashboard' : 'gRPC Server Dashboard'"
        eventType="gRPC exchange"
    />

    <div v-else>
      <PageHeader :title="mode === 'client' ? 'gRPC Client Traffic' : 'gRPC Server Traffic'" icon="bi-bar-chart-line"/>

      <!-- Loading state -->
      <div v-if="isLoading" class="p-4 text-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <!-- Error state -->
      <div v-else-if="error" class="p-4 text-center">
        <div class="alert alert-danger" role="alert">
          Error loading gRPC traffic data: {{ error }}
        </div>
      </div>

      <!-- Dashboard content -->
      <div v-if="trafficData" class="dashboard-container">
        <ChartSectionWithTabs
            :tabs="tabs"
            :full-width="true"
            id-prefix="grpc-traffic-"
            @tab-change="onTabChange"
        >
          <template #stats>
            <StatsTable :metrics="metricsData"/>
          </template>

          <template #size-over-time>
            <GrpcSizeTimeseries
                :request-size-data="trafficData?.requestSizeSerie.data || []"
                :response-size-data="trafficData?.responseSizeSerie.data || []"/>
          </template>

          <template #size-distribution>
            <div ref="histogramChartRef" class="apex-chart"></div>
          </template>

          <template #largest>
            <GrpcLargestCalls
                :calls="getSortedLargestCalls()"
                :total-call-count="trafficData?.header.callCount || 0"/>
          </template>
        </ChartSectionWithTabs>
      </div>

      <!-- No data state -->
      <div v-else class="p-4 text-center">
        <h3 class="text-muted">No gRPC Traffic Data Available</h3>
        <p class="text-muted">No gRPC traffic data found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import ApexCharts from 'apexcharts';
import PageHeader from '@/components/layout/PageHeader.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import GrpcSizeTimeseries from '@/components/grpc/GrpcSizeTimeseries.vue';
import GrpcLargestCalls from '@/components/grpc/GrpcLargestCalls.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type {GrpcTrafficData} from '@/services/api/ProfileGrpcClient';
import StatsTable from '@/components/StatsTable.vue';
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

// Tab definitions
const tabs = [
  {id: 'stats', label: 'Overview', icon: 'grid-3x3-gap'},
  {id: 'size-over-time', label: 'Size Over Time', icon: 'graph-up'},
  {id: 'size-distribution', label: 'Size Distribution', icon: 'bar-chart'},
  {id: 'largest', label: 'Largest Calls', icon: 'box-seam'}
];

// Reactive state
const trafficData = ref<GrpcTrafficData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const histogramChartRef = ref<HTMLElement | null>(null);
let histogramChart: ApexCharts | null = null;

// Get mode from query parameter, default to 'server'
const mode = (route.query.mode as 'client' | 'server') || 'server';

// Check if gRPC dashboard is disabled
const isGrpcDashboardDisabled = computed(() => {
  const featureType = mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

// Computed metrics for StatsTable
const metricsData = computed(() => {
  if (!trafficData.value?.header) return [];

  const header = trafficData.value.header;

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
          value: header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent),
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
          value: header.totalBytesReceived < 0 ? '?' : FormattingService.formatBytes(header.totalBytesReceived),
          color: '#FBBC05'
        }
      ]
    }
  ];
});

// Client initialization
const client = new ProfileGrpcClient(mode, route.params.profileId as string);


// Helper functions
const getSortedLargestCalls = () => {
  if (!trafficData.value) return [];
  return [...trafficData.value.largestCalls].sort((a, b) => b.totalSize - a.totalSize);
};

// Handle tab change — render histogram when switching to the distribution tab
const onTabChange = async (tabIndex: number) => {
  if (tabIndex === 1 && trafficData.value?.sizeBuckets?.length) {
    await nextTick();
    setTimeout(async () => {
      await createHistogramChart();
    }, 100);
  }
};

// Create histogram chart for size distribution
const createHistogramChart = async () => {
  if (!histogramChartRef.value || !trafficData.value?.sizeBuckets?.length) return;

  // Destroy existing chart if it exists
  if (histogramChart) {
    histogramChart.destroy();
    histogramChart = null;
  }

  const buckets = trafficData.value.sizeBuckets;
  const categories = buckets.map(b => b.label);
  const counts = buckets.map(b => b.count);

  const options = {
    series: [{
      name: 'Call Count',
      data: counts
    }],
    chart: {
      type: 'bar',
      height: 350,
      toolbar: {
        show: false
      },
      animations: {
        enabled: true
      }
    },
    plotOptions: {
      bar: {
        borderRadius: 4,
        columnWidth: '60%'
      }
    },
    colors: ['#5e64ff'],
    dataLabels: {
      enabled: false
    },
    xaxis: {
      categories: categories,
      labels: {
        style: {
          fontSize: '12px',
          fontWeight: 500
        }
      },
      title: {
        text: 'Message Size',
        style: {
          fontSize: '13px',
          fontWeight: 600,
          color: '#6b7280'
        }
      }
    },
    yaxis: {
      title: {
        text: 'Call Count',
        style: {
          fontSize: '13px',
          fontWeight: 600,
          color: '#6b7280'
        }
      },
      labels: {
        formatter: (val: number) => Math.round(val).toString()
      }
    },
    tooltip: {
      y: {
        formatter: (val: number) => `${val} calls`
      }
    },
    grid: {
      borderColor: '#f0f0f0',
      strokeDashArray: 4
    },
    responsive: [{
      breakpoint: 480,
      options: {
        chart: {
          height: 300
        }
      }
    }]
  };

  try {
    histogramChart = new ApexCharts(histogramChartRef.value, options);
    await histogramChart.render();
  } catch (err) {
    console.error('Error creating histogram chart:', err);
  }
};

// Watch for traffic data changes to recreate histogram
watch(
    () => trafficData.value?.sizeBuckets,
    async () => {
      await nextTick();
      setTimeout(async () => {
        await createHistogramChart();
      }, 100);
    },
    {deep: true}
);

// Lifecycle methods
const loadTrafficData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    // Load data from API
    trafficData.value = await client.getTraffic();

    // Wait for DOM updates
    await nextTick();

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading gRPC traffic data:', err);
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  // Only load data if the feature is not disabled
  if (!isGrpcDashboardDisabled.value) {
    loadTrafficData();
  }
});

onUnmounted(() => {
  if (histogramChart) {
    histogramChart.destroy();
  }
});

</script>

<style scoped>
.apex-chart {
  height: 100%;
  padding: 1rem;
}
</style>
