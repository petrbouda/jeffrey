<template>
  <div>
    <CustomDisabledFeatureAlert
      v-if="isGrpcDashboardDisabled"
      :title="mode === 'client' ? 'gRPC Client Dashboard' : 'gRPC Server Dashboard'"
      eventType="gRPC exchange"
    />

    <div v-else>
      <div v-if="isLoading" class="p-4 text-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <div v-else-if="error" class="p-4 text-center">
        <div class="alert alert-danger" role="alert">
          Error loading gRPC traffic data: {{ error }}
        </div>
      </div>

      <div v-if="trafficData && trafficData.sizeBuckets?.length" class="dashboard-container">
        <GrpcTrafficStats :header="trafficData.header" />
        <ChartSection
          title="Message Size Distribution"
          icon="bar-chart"
          :full-width="true"
          container-class="apex-chart-container"
        >
          <div ref="histogramChartRef" class="apex-chart"></div>
        </ChartSection>
      </div>

      <div v-else-if="!isLoading && !error" class="p-4 text-center">
        <h3 class="text-muted">No Size Distribution Data Available</h3>
        <p class="text-muted">No gRPC size distribution data found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcTrafficData } from '@/services/api/ProfileGrpcClient';
import GrpcTrafficStats from '@/components/grpc/GrpcTrafficStats.vue';
import ChartSection from '@/components/ChartSection.vue';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';

interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const trafficData = ref<GrpcTrafficData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const histogramChartRef = ref<HTMLElement | null>(null);
let histogramChart: ApexCharts | null = null;

const mode = (route.query.mode as 'client' | 'server') || 'server';

const isGrpcDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

const client = new ProfileGrpcClient(mode, route.params.profileId as string);

const createHistogramChart = async () => {
  if (!histogramChartRef.value || !trafficData.value?.sizeBuckets?.length) return;

  if (histogramChart) {
    histogramChart.destroy();
    histogramChart = null;
  }

  const buckets = trafficData.value.sizeBuckets;
  const options = {
    series: [
      {
        name: 'Call Count',
        data: buckets.map(b => b.count)
      }
    ],
    chart: {
      type: 'bar',
      height: 350,
      toolbar: { show: false },
      animations: { enabled: true }
    },
    plotOptions: {
      bar: { borderRadius: 4, columnWidth: '60%' }
    },
    colors: ['#5e64ff'],
    dataLabels: { enabled: false },
    xaxis: {
      categories: buckets.map(b => b.label),
      labels: { style: { fontSize: '12px', fontWeight: 500 } },
      title: {
        text: 'Message Size',
        style: { fontSize: '13px', fontWeight: 600, color: '#6b7280' }
      }
    },
    yaxis: {
      title: { text: 'Call Count', style: { fontSize: '13px', fontWeight: 600, color: '#6b7280' } },
      labels: { formatter: (val: number) => Math.round(val).toString() }
    },
    tooltip: { y: { formatter: (val: number) => `${val} calls` } },
    grid: { borderColor: '#f0f0f0', strokeDashArray: 4 }
  };

  try {
    histogramChart = new ApexCharts(histogramChartRef.value, options);
    await histogramChart.render();
  } catch (err) {
    console.error('Error creating histogram chart:', err);
  }
};

watch(
  () => trafficData.value?.sizeBuckets,
  async () => {
    await nextTick();
    setTimeout(async () => {
      await createHistogramChart();
    }, 100);
  },
  { deep: true }
);

onMounted(async () => {
  if (isGrpcDashboardDisabled.value) return;
  try {
    isLoading.value = true;
    trafficData.value = await client.getTraffic();
    await nextTick();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
  } finally {
    isLoading.value = false;
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
