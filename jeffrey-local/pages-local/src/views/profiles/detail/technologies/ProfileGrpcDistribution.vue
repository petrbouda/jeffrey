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
        <div class="alert alert-danger" role="alert">Error loading gRPC data: {{ error }}</div>
      </div>

      <div v-if="grpcOverviewData" class="dashboard-container">
        <GrpcOverviewStats :header="grpcOverviewData.header" />
        <GrpcDistributionCharts
          title="gRPC Distribution"
          icon="pie-chart"
          :status-codes="grpcOverviewData.statusCodes || []"
          :services="grpcOverviewData.services || []"
          :total-calls="grpcOverviewData.header.callCount || 0"
        />
      </div>

      <div v-else-if="!isLoading && !error" class="p-4 text-center">
        <h3 class="text-muted">No gRPC Data Available</h3>
        <p class="text-muted">No gRPC exchange events found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import GrpcDistributionCharts from '@/components/grpc/GrpcDistributionCharts.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcOverviewData } from '@/services/api/ProfileGrpcClient';
import GrpcOverviewStats from '@/components/grpc/GrpcOverviewStats.vue';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';

interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const grpcOverviewData = ref<GrpcOverviewData | null>(null);
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
    grpcOverviewData.value = await client.getOverview();
    await nextTick();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
  } finally {
    isLoading.value = false;
  }
});
</script>
