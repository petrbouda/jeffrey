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
      description="No trace-carrying events were recorded in this profile."
      icon="bi-bar-chart-steps"
    />

    <div v-else class="dashboard-container">
      <template v-if="selectedOperation === null">
        <TraceOperationStats v-if="overview" :operations="operations" :overview="overview" />
        <!--
          The tile above says how many operations the profile has; this says how many of them are
          listed. Without it a capped list reads as the whole set, and the two numbers disagree with
          no explanation.
        -->
        <p v-if="listNote" class="list-note">{{ listNote }}</p>
        <TraceOperationList :operations="operations" @operation-click="openOperation" />
      </template>

      <template v-else>
        <DetailBreadcrumb
          root-label="Trace Operations"
          icon="bi-bar-chart-steps"
          @back="clearSelection"
        >
          {{ selectedOperation.name }}
        </DetailBreadcrumb>

        <!--
          Keyed on the whole operation identity: TraceOperationFlamegraphs' panel count loads only on
          mount (useFlamegraphPanels), so a query-only change (browser back/forward between two deep
          links) needs a fresh instance rather than a prop update to pick up the new panels.
        -->
        <TraceOperationDetail
          :key="operationKey(selectedOperation)"
          :profile-id="profileId"
          :operation="selectedOperation"
          :totals="selectedTotals"
          :overview="overview"
        />
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
import type {
  SpanKind,
  TraceOperationId,
  TraceOperationRow,
  TraceOverview
} from '@/services/api/model/trace/TraceModels';
import { operationKey } from '@/services/trace/traceLabels';
import FeatureType from '@/services/api/model/FeatureType';

/** Ranked by total time, so the cut is the tail rather than an arbitrary slice. */
const OPERATIONS_LIMIT = 100;

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

const operations = ref<TraceOperationRow[]>([]);
const overview = ref<TraceOverview | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);
const truncated = ref(false);

const listNote = computed<string | null>(() => {
  if (!truncated.value) {
    return null;
  }
  return `Showing the ${OPERATIONS_LIMIT} operations with the most total time`;
});

const profileId = computed(() => route.params.profileId as string);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

/**
 * The selection lives in the URL so the detail is linkable and Back steps out of it, not off it.
 *
 * All three parts travel, because all three identify the operation — a link carrying only the name
 * would resolve to whichever of an inbound and an outbound call of that name came first.
 */
const selectedOperation = computed<TraceOperationId | null>(() => {
  const name = route.query.operation as string | undefined;
  const kind = route.query.kind as SpanKind | undefined;
  const eventType = route.query.eventType as string | undefined;
  if (!name || !kind || !eventType) {
    return null;
  }
  return { name, kind, eventType };
});

/**
 * The server-side aggregates for the selected operation, when it is one of the listed rows. Absent
 * for a deep link into an operation past the row cap, which the detail then falls back from.
 */
const selectedTotals = computed<TraceOperationRow | null>(() => {
  const selected = selectedOperation.value;
  if (selected === null) {
    return null;
  }
  const key = operationKey(selected);
  return operations.value.find(row => operationKey(row) === key) ?? null;
});

function openOperation(operation: TraceOperationRow): void {
  router.push({
    query: {
      ...route.query,
      operation: operation.name,
      kind: operation.kind,
      eventType: operation.eventType
    }
  });
}

function clearSelection(): void {
  const query = { ...route.query };
  delete query.operation;
  delete query.kind;
  delete query.eventType;
  router.push({ query });
}

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileTracesClient(profileId.value);
    const [operationRows, overviewData] = await Promise.all([
      // One past the cap, so a list that merely filled it can be told apart from one that was cut.
      client.getOperations(OPERATIONS_LIMIT + 1),
      client.getOverview()
    ]);
    truncated.value = operationRows.length > OPERATIONS_LIMIT;
    operations.value = truncated.value ? operationRows.slice(0, OPERATIONS_LIMIT) : operationRows;
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

.list-note {
  margin: 0 0 var(--space-2, 0.5rem);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}
</style>
