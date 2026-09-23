<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

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
        <!-- JDBC Overview Cards -->
        <JdbcDashboardSection :jdbc-header="data.header" />

        <!-- JDBC Metrics Timeline -->
        <ChartSection
          title="JDBC Metrics Timeline"
          icon="graph-up"
          :full-width="true"
          container-class="apex-chart-container"
        >
          <TimeSeriesChart
            :primary-data="data.executionTimeSerie.data || []"
            primary-title="Execution Time"
            :secondary-data="data.statementCountSerie.data || []"
            secondary-title="Executions"
            :visible-minutes="60"
            :independent-secondary-axis="true"
            :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
            :secondary-axis-type="AxisFormatType.NUMBER"
          />
        </ChartSection>

        <!-- Statement Groups Section -->
        <JdbcGroupList
          :groups="data.groups"
          :selected-group="null"
          @group-click="handleGroupClick"
        />

        <!-- JDBC Distribution Charts -->
        <JdbcDistributionCharts
          :operations="data.operations || []"
          second-chart-title="Statement Groups Distribution"
          :second-chart-data="getStatementGroupsData(data)"
          :total="data.header.statementCount"
        />

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
import { useRoute, useRouter } from 'vue-router';
import JdbcDashboardSection from '@/components/jdbc/JdbcDashboardSection.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import JdbcStatementModal from '@/components/jdbc/JdbcStatementModal.vue';
import JdbcDistributionCharts from '@/components/jdbc/JdbcDistributionCharts.vue';
import JdbcGroupList from '@/components/jdbc/JdbcGroupList.vue';
import JdbcSlowestStatements from '@/components/jdbc/JdbcSlowestStatements.vue';
import ProfileJdbcStatementClient from '@/services/api/ProfileJdbcStatementClient.ts';
import JdbcOverviewData from '@/services/api/model/JdbcOverviewData.ts';
import JdbcSlowStatement from '@/services/api/model/JdbcSlowStatement.ts';
import JdbcGroup from '@/services/api/model/JdbcGroup.ts';
import TechnologyDashboard from '@/components/technologies/TechnologyDashboard.vue';
import FeatureType from '@/services/api/model/FeatureType';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const router = useRouter();

// Reactive state
const selectedStatement = ref<JdbcSlowStatement | null>(null);
const showModal = ref(false);

// Check if JDBC statements dashboard is disabled
const isJdbcStatementsDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_STATEMENTS_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcStatementClient(route.params.profileId as string);

const getStatementGroupsData = (data: JdbcOverviewData) => {
  return data.groups.map((group: JdbcGroup) => ({
    label: group.group,
    value: group.count
  }));
};

const showSqlModal = (statement: JdbcSlowStatement) => {
  selectedStatement.value = statement;
  showModal.value = true;
};

const handleGroupClick = (group: string) => {
  router.push({
    name: 'profile-technologies-jdbc-statement-groups',
    query: { group: encodeURIComponent(group) }
  });
};
</script>
