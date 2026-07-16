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
      <GrpcOverviewStats :header="data.header" />
      <GrpcTimeseries
        :response-time-data="data.responseTimeSerie.data || []"
        :call-count-data="data.callCountSerie.data || []"
      />
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import GrpcTimeseries from '@/components/grpc/GrpcTimeseries.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import GrpcOverviewStats from '@/components/grpc/GrpcOverviewStats.vue';
import TechnologyDashboard from '@/components/technologies/TechnologyDashboard.vue';
import FeatureType from '@/services/api/model/FeatureType';

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
</script>
