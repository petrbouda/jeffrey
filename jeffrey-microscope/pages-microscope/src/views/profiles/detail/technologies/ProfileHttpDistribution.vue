<template>
  <TechnologyDashboard
    :fetch="() => client.getOverview()"
    :disabled="isHttpDashboardDisabled"
    :disabled-title="mode === 'client' ? 'HTTP Client Dashboard' : 'HTTP Server Dashboard'"
    event-type="HTTP exchange"
    no-data-title="No HTTP Data Available"
    no-data-message="No HTTP exchange events found for this profile"
  >
    <template #default="{ data }">
      <HttpOverviewStats :header="data.header" />
      <HttpDistributionCharts
        title="HTTP Distribution"
        icon="pie-chart"
        :status-codes="data.statusCodes || []"
        :methods="data.methods || []"
        :total-requests="data.header.requestCount || 0"
      />
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import HttpDistributionCharts from '@/components/http/HttpDistributionCharts.vue';
import ProfileHttpClient from '@/services/api/ProfileHttpClient';
import HttpOverviewStats from '@/components/http/HttpOverviewStats.vue';
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

const isHttpDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.HTTP_CLIENT_DASHBOARD : FeatureType.HTTP_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

const client = new ProfileHttpClient(mode, route.params.profileId as string);
</script>
