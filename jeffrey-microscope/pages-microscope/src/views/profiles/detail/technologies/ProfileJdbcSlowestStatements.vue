<template>
  <div>
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

        <!-- Slowest Statements -->
        <JdbcSlowestStatements :statements="data.slowStatements" @sql-button-click="showSqlModal" />
      </template>
    </TechnologyDashboard>

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
import JdbcSlowStatement from '@/services/api/model/JdbcSlowStatement.ts';
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
const selectedStatement = ref<JdbcSlowStatement | null>(null);
const showModal = ref(false);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);

const showSqlModal = (statement: JdbcSlowStatement) => {
  selectedStatement.value = statement;
  showModal.value = true;
};
</script>
