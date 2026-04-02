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

        <!-- Group Filter Bar -->
        <SearchableFilterBar
          v-if="jdbcOverviewData.groups.length > 0"
          v-model="selectedGroup"
          :items="groupItems"
          label="Statement Group"
          placeholder="All Groups"
          search-placeholder="Search groups..."
          items-label="groups"
          :total-count="jdbcOverviewData.header.statementCount"
        />

        <!-- Loading state for group data -->
        <div v-if="isGroupLoading" class="p-4 text-center">
          <div class="spinner-border spinner-border-sm text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>

        <!-- JDBC Distribution Charts -->
        <JdbcDistributionCharts
          v-else
          :operations="currentOperations"
          :second-chart-title="currentSecondChartTitle"
          :second-chart-data="currentSecondChartData"
          :total="currentTotal"
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
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import JdbcOverviewStats from '@/components/jdbc/JdbcOverviewStats.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import SearchableFilterBar from '@/components/form/SearchableFilterBar.vue';
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
const singleGroupData = ref<JdbcOverviewData | null>(null);
const isLoading = ref(true);
const isGroupLoading = ref(false);
const error = ref<string | null>(null);
const selectedGroup = ref<string | null>(null);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);

// Group items for SearchableFilterBar
const groupItems = computed(() => {
  if (!jdbcOverviewData.value) return [];
  return jdbcOverviewData.value.groups.map((g: JdbcGroup) => ({
    label: g.group,
    count: g.count,
    p99: g.p99ExecutionTime
  }));
});

// Current chart data based on filter
const currentOperations = computed(() => {
  if (selectedGroup.value && singleGroupData.value) {
    return singleGroupData.value.operations;
  }
  return jdbcOverviewData.value?.operations || [];
});

const currentSecondChartTitle = computed(() => {
  return selectedGroup.value ? 'Statement Names' : 'Statement Groups';
});

const currentSecondChartData = computed(() => {
  if (selectedGroup.value && singleGroupData.value) {
    const group = singleGroupData.value.groups[0];
    return group?.statementNames || [];
  }
  if (!jdbcOverviewData.value) return [];
  return jdbcOverviewData.value.groups.map((g: JdbcGroup) => ({
    label: g.group,
    value: g.count
  }));
});

const currentTotal = computed(() => {
  if (selectedGroup.value && singleGroupData.value) {
    return singleGroupData.value.header.statementCount;
  }
  return jdbcOverviewData.value?.header.statementCount || 0;
});

// Watch group selection
watch(selectedGroup, async (newGroup) => {
  if (newGroup) {
    try {
      isGroupLoading.value = true;
      singleGroupData.value = await client.getOverviewGroup(newGroup);
    } catch (err) {
      console.error('Error loading group data:', err);
      singleGroupData.value = null;
    } finally {
      isGroupLoading.value = false;
    }
  } else {
    singleGroupData.value = null;
  }
});

// Lifecycle methods
const loadJdbcData = async () => {
  try {
    isLoading.value = true;
    error.value = null;
    jdbcOverviewData.value = await client.getOverview();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading JDBC data:', err);
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
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
