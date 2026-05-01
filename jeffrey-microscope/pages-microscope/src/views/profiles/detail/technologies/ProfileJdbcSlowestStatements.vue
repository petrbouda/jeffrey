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
      <LoadingState v-if="isLoading" />

      <!-- Error state -->
      <ErrorState v-else-if="error" :message="error" />

      <!-- Dashboard content -->
      <div v-if="jdbcOverviewData" class="dashboard-container">
        <!-- JDBC Overview Stats -->
        <JdbcOverviewStats :jdbc-header="jdbcOverviewData.header" />

        <!-- Slowest Statements -->
        <JdbcSlowestStatements
          :statements="jdbcOverviewData.slowStatements"
          @sql-button-click="showSqlModal"
        />
      </div>

      <!-- No data state -->
      <div v-else-if="!isLoading" class="p-4 text-center">
        <h3 class="text-muted">No JDBC Data Available</h3>
        <p class="text-muted">No JDBC statement events found for this profile</p>
      </div>
    </div>

    <!-- JDBC Statement Modal -->
    <JdbcStatementModal
      :statement="selectedStatement"
      modal-id="jdbcStatementModal"
      :show="showModal"
      @update:show="showModal = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import JdbcOverviewStats from '@/components/jdbc/JdbcOverviewStats.vue';
import JdbcSlowestStatements from '@/components/jdbc/JdbcSlowestStatements.vue';
import JdbcStatementModal from '@/components/jdbc/JdbcStatementModal.vue';
import ProfileJdbcStatementClient from '@/services/api/ProfileJdbcStatementClient.ts';
import JdbcOverviewData from '@/services/api/model/JdbcOverviewData.ts';
import JdbcSlowStatement from '@/services/api/model/JdbcSlowStatement.ts';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/api/model/FeatureType';
import { useTechnologyData } from '@/composables/useTechnologyData';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();

// Reactive state
const selectedStatement = ref<JdbcSlowStatement | null>(null);
const showModal = ref(false);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);

const {
  data: jdbcOverviewData,
  isLoading,
  error
} = useTechnologyData<JdbcOverviewData>(() => client.getOverview(), isJdbcStatementsDisabled);

const showSqlModal = (statement: JdbcSlowStatement) => {
  selectedStatement.value = statement;
  showModal.value = true;
};
</script>

<style scoped>
@media (max-width: 768px) {
  .dashboard-container {
    padding: 1rem;
  }
}
</style>
