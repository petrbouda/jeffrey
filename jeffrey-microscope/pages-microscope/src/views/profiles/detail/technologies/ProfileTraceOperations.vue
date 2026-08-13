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
      <TraceOperationStats :operations="operations" />
      <TraceOperationList :operations="operations" @operation-click="openTraces" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TraceOperationStats from '@/components/trace/TraceOperationStats.vue';
import TraceOperationList from '@/components/trace/TraceOperationList.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import type { TraceOperationRow } from '@/services/api/model/trace/TraceModels';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

const operations = ref<TraceOperationRow[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);

const profileId = computed(() => route.params.profileId as string);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

/**
 * This view answers "which operation is slow in general"; the traces rooted at one answer "which
 * run was slow". Rather than growing a second list here, the click hands the question to the view
 * that already owns it.
 */
function openTraces(name: string): void {
  router.push({
    name: 'profile-traces',
    params: { profileId: profileId.value },
    query: { operation: name }
  });
}

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    operations.value = await new ProfileTracesClient(profileId.value).getOperations();
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
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
</style>
