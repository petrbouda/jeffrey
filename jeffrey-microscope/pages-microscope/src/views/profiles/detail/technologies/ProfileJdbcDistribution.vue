<template>
  <TechnologyDashboard
    :fetch="() => client.getOverview()"
    :disabled="isJdbcStatementsDisabled"
    disabled-title="JDBC Statements Dashboard"
    event-type="JDBC statement"
    no-data-title="No JDBC Data Available"
    no-data-message="No JDBC statement events found for this profile"
  >
    <template #default="{ data }">
      <!-- JDBC Overview Stats -->
      <JdbcOverviewStats :jdbc-header="data.header" />

      <!-- Group Filter Bar -->
      <SearchableFilterBar
        v-if="data.groups.length > 0"
        v-model="selectedGroup"
        :items="groupItems(data)"
        label="Statement Group"
        placeholder="All Groups"
        search-placeholder="Search groups..."
        items-label="groups"
        :total-count="data.header.statementCount"
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
        :operations="currentOperations(data)"
        :second-chart-title="currentSecondChartTitle"
        :second-chart-data="currentSecondChartData(data)"
        :total="currentTotal(data)"
      />
    </template>
  </TechnologyDashboard>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import JdbcOverviewStats from '@/components/jdbc/JdbcOverviewStats.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import SearchableFilterBar from '@/components/form/SearchableFilterBar.vue';
import ProfileJdbcStatementClient from '@/services/api/ProfileJdbcStatementClient.ts';
import JdbcOverviewData from '@/services/api/model/JdbcOverviewData.ts';
import JdbcGroup from '@/services/api/model/JdbcGroup.ts';
import TechnologyDashboard from '@/components/technologies/TechnologyDashboard.vue';
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
const singleGroupData = ref<JdbcOverviewData | null>(null);
const isGroupLoading = ref(false);
const selectedGroup = ref<string | null>(null);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);

// Group items for SearchableFilterBar
const groupItems = (data: JdbcOverviewData) => {
  return data.groups.map((g: JdbcGroup) => ({
    label: g.group,
    count: g.count,
    p99: g.p99ExecutionTime
  }));
};

// Current chart data based on filter
const currentOperations = (data: JdbcOverviewData) => {
  if (selectedGroup.value && singleGroupData.value) {
    return singleGroupData.value.operations;
  }
  return data.operations || [];
};

const currentSecondChartTitle = computed(() => {
  return selectedGroup.value ? 'Statement Names' : 'Statement Groups';
});

const currentSecondChartData = (data: JdbcOverviewData) => {
  if (selectedGroup.value && singleGroupData.value) {
    const group = singleGroupData.value.groups[0];
    return group?.statementNames || [];
  }
  return data.groups.map((g: JdbcGroup) => ({
    label: g.group,
    value: g.count
  }));
};

const currentTotal = (data: JdbcOverviewData) => {
  if (selectedGroup.value && singleGroupData.value) {
    return singleGroupData.value.header.statementCount;
  }
  return data.header.statementCount || 0;
};

// Watch group selection
watch(selectedGroup, async newGroup => {
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
</script>
