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
      <GrpcDistributionCharts
        title="gRPC Distribution"
        icon="pie-chart"
        :status-codes="data.statusCodes || []"
        :services="data.services || []"
        :total-calls="data.header.callCount || 0"
      />
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import GrpcDistributionCharts from '@/components/grpc/GrpcDistributionCharts.vue';
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
