<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert
      v-if="isJdbcStatementsDisabled"
      title="JDBC Statements Dashboard"
      eventType="JDBC statement"
    />

    <div v-else>
      <!-- Loading state -->
      <div v-if="isLoading" class="p-4 text-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <!-- Error state -->
      <div v-else-if="error" class="p-4 text-center">
        <div class="alert alert-danger" role="alert">Error loading JDBC data: {{ error }}</div>
      </div>

      <!-- Dashboard content -->
      <div v-if="jdbcOverviewData" class="dashboard-container">
        <!-- JDBC Overview Stats -->
        <JdbcOverviewStats :jdbc-header="jdbcOverviewData.header" />

        <!-- JDBC Distribution Charts -->
        <JdbcDistributionCharts
          :operations="jdbcOverviewData?.operations || []"
          second-chart-title="Statement Groups Distribution"
          :second-chart-data="getStatementGroupsData()"
          :total="jdbcOverviewData.header.statementCount"
        />
      </div>

      <!-- No data state -->
      <div v-else-if="!isLoading" class="p-4 text-center">
        <h3 class="text-muted">No JDBC Data Available</h3>
        <p class="text-muted">No JDBC statement events found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import JdbcOverviewStats from '@/components/jdbc/JdbcOverviewStats.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import ProfileJdbcStatementClient from '@/services/api/ProfileJdbcStatementClient.ts';
import JdbcOverviewData from '@/services/api/model/JdbcOverviewData.ts';
import JdbcGroup from '@/services/api/model/JdbcGroup.ts';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();

// Reactive state
const jdbcOverviewData = ref<JdbcOverviewData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);

const getStatementGroupsData = () => {
  if (!jdbcOverviewData.value) return [];
  return jdbcOverviewData.value.groups.map((group: JdbcGroup) => ({
    label: group.group,
    value: group.count
  }));
};

// Lifecycle methods
const loadJdbcData = async () => {
  try {
    isLoading.value = true;
    error.value = null;

    // Load data from API
    jdbcOverviewData.value = await client.getOverview();

    // Wait for DOM updates
    await nextTick();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading JDBC data:', err);
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  // Only load data if the feature is not disabled
  if (!isJdbcStatementsDisabled.value) {
    loadJdbcData();
  }
});
</script>

<style scoped>
@media (max-width: 768px) {
  .dashboard-container {
    padding: 1rem;
  }
}
</style>
