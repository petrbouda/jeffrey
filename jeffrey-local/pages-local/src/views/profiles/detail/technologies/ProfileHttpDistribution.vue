<template>
  <div>
    <CustomDisabledFeatureAlert
      v-if="isHttpDashboardDisabled"
      :title="mode === 'client' ? 'HTTP Client Dashboard' : 'HTTP Server Dashboard'"
      eventType="HTTP exchange"
    />

    <div v-else>
      <LoadingState v-if="isLoading" />

      <ErrorState v-else-if="error" :message="error" />

      <div v-if="httpOverviewData" class="dashboard-container">
        <HttpOverviewStats :header="httpOverviewData.header" />
        <HttpDistributionCharts
          title="HTTP Distribution"
          icon="pie-chart"
          :status-codes="httpOverviewData.statusCodes || []"
          :methods="httpOverviewData.methods || []"
          :total-requests="httpOverviewData.header.requestCount || 0"
        />
      </div>

      <div v-else-if="!isLoading && !error" class="p-4 text-center">
        <h3 class="text-muted">No HTTP Data Available</h3>
        <p class="text-muted">No HTTP exchange events found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import HttpDistributionCharts from '@/components/http/HttpDistributionCharts.vue';
import ProfileHttpClient from '@/services/api/ProfileHttpClient';
import HttpOverviewData from '@/services/api/model/HttpOverviewData';
import HttpOverviewStats from '@/components/http/HttpOverviewStats.vue';
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

const isHttpDashboardDisabled = computed(() => {
  const featureType =
    mode === 'client' ? FeatureType.HTTP_CLIENT_DASHBOARD : FeatureType.HTTP_SERVER_DASHBOARD;
  return props.disabledFeatures.includes(featureType);
});

const client = new ProfileHttpClient(mode, route.params.profileId as string);

const {
  data: httpOverviewData,
  isLoading,
  error
} = useTechnologyData<HttpOverviewData>(() => client.getOverview(), isHttpDashboardDisabled);
</script>
