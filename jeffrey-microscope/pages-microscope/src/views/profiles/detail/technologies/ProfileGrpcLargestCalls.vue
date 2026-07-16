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
      <GrpcTrafficStats :header="data.header" />
      <GrpcLargestCalls
        :calls="sortedLargestCalls(data)"
        :total-call-count="data.header.callCount || 0"
      />
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import GrpcLargestCalls from '@/components/grpc/GrpcLargestCalls.vue';
import ProfileGrpcClient from '@/services/api/ProfileGrpcClient';
import type { GrpcTrafficData } from '@/services/api/ProfileGrpcClient';
import GrpcTrafficStats from '@/components/grpc/GrpcTrafficStats.vue';
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

const sortedLargestCalls = (data: GrpcTrafficData) => {
  return [...data.largestCalls].sort((a, b) => b.totalSize - a.totalSize);
};
</script>
