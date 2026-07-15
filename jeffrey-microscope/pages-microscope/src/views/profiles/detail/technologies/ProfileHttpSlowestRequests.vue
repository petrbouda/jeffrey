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
      <HttpSlowestRequests
        :requests="sortedSlowRequests(data)"
        :total-request-count="data.header.requestCount || 0"
      />
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import HttpSlowestRequests from '@/components/http/HttpSlowestRequests.vue';
import ProfileHttpClient from '@/services/api/ProfileHttpClient';
import HttpOverviewData from '@/services/api/model/HttpOverviewData';
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

const sortedSlowRequests = (data: HttpOverviewData) => {
  return [...data.slowRequests].sort((a, b) => b.responseTime - a.responseTime);
};
</script>
