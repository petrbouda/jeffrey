<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div>
    <TracesDisabledFeatureAlert v-if="featureDisabled" />

    <LoadingState v-else-if="loading" message="Loading operations..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="operations.length === 0"
      title="No Operations"
      message="No trace-carrying events were recorded in this profile."
      icon="bi-bar-chart-steps"
    />

    <div v-else class="dashboard-container">
      <template v-if="selectedOperation === ''">
        <TraceOperationStats v-if="overview" :operations="operations" :overview="overview" />
        <TraceOperationList :operations="operations" @operation-click="openOperation" />
      </template>

      <template v-else>
        <DetailBreadcrumb root-label="Trace Operations" icon="bi-bar-chart-steps" @back="clearSelection">
          {{ selectedOperation }}
        </DetailBreadcrumb>

        <EmptyState
          v-if="!isKnownOperation"
          title="Unknown Operation"
          message="No trace in this profile is rooted at that operation."
          icon="bi-bar-chart-steps"
        />

        <TraceOperationDetail v-else :profile-id="profileId" :name="selectedOperation" />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DetailBreadcrumb from '@shared/components/DetailBreadcrumb.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TraceOperationStats from '@/components/trace/TraceOperationStats.vue';
import TraceOperationList from '@/components/trace/TraceOperationList.vue';
import TraceOperationDetail from '@/components/trace/TraceOperationDetail.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import type { TraceOperationRow, TraceOverview } from '@/services/api/model/trace/TraceModels';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

const operations = ref<TraceOperationRow[]>([]);
const overview = ref<TraceOverview | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);

const profileId = computed(() => route.params.profileId as string);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

/** The selection lives in the URL so the detail is linkable and Back steps out of it, not off it. */
const selectedOperation = computed(() => (route.query.operation as string) ?? '');

const isKnownOperation = computed(() =>
  operations.value.some(operation => operation.name === selectedOperation.value)
);

function openOperation(name: string): void {
  router.push({ query: { ...route.query, operation: name } });
}

function clearSelection(): void {
  const query = { ...route.query };
  delete query.operation;
  router.push({ query });
}

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileTracesClient(profileId.value);
    const [operationRows, overviewData] = await Promise.all([
      client.getOperations(),
      client.getOverview()
    ]);
    operations.value = operationRows;
    overview.value = overviewData;
  } catch {
    error.value = 'Failed to load the trace operations for this profile.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (featureDisabled.value) {
    loading.value = false;
  } else {
    loadData();
  }
});
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
