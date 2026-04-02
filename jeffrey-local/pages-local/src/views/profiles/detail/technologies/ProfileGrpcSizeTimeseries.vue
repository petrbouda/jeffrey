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

      <div v-if="trafficData" class="dashboard-container">
        <GrpcTrafficStats :header="trafficData.header" />
        <GrpcSizeTimeseries
          :request-size-data="trafficData.requestSizeSerie.data || []"
          :response-size-data="trafficData.responseSizeSerie.data || []"
        />
      </div>

      <div v-else-if="!isLoading && !error" class="p-4 text-center">
        <h3 class="text-muted">No gRPC Traffic Data Available</h3>
        <p class="text-muted">No gRPC traffic data found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import GrpcSizeTimeseries from '@/components/grpc/GrpcSizeTimeseries.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcTrafficData } from '@/services/api/ProfileGrpcClient';
import GrpcTrafficStats from '@/components/grpc/GrpcTrafficStats.vue';
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

const mode = (route.query.mode as 'client' | 'server') || 'server';

const isGrpcDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

const client = new ProfileGrpcClient(mode, route.params.profileId as string);

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
</script>
