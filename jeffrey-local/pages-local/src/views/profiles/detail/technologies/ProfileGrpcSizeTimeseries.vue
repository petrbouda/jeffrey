<template>
  <div>
    <CustomDisabledFeatureAlert
      v-if="isGrpcDashboardDisabled"
      :title="mode === 'client' ? 'gRPC Client Dashboard' : 'gRPC Server Dashboard'"
      eventType="gRPC exchange"
    />

    <div v-else>
      <LoadingState v-if="isLoading" />

      <ErrorState v-else-if="error" :message="error" />

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
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import GrpcSizeTimeseries from '@/components/grpc/GrpcSizeTimeseries.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcTrafficData } from '@/services/api/ProfileGrpcClient';
import GrpcTrafficStats from '@/components/grpc/GrpcTrafficStats.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';
import { useTechnologyData } from '@/composables/useTechnologyData';

interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();

const mode = (route.query.mode as 'client' | 'server') || 'server';

const isGrpcDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.GRPC_CLIENT_DASHBOARD : FeatureType.GRPC_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

const client = new ProfileGrpcClient(mode, route.params.profileId as string);

const {
  data: trafficData,
  isLoading,
  error
} = useTechnologyData<GrpcTrafficData>(() => client.getTraffic(), isGrpcDashboardDisabled);
</script>
