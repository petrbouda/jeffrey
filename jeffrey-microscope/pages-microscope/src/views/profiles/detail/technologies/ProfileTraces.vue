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

    <LoadingState v-else-if="loading" message="Loading traces..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="traces.length === 0"
      title="No Traces"
      message="No trace-carrying events were recorded in this profile."
      icon="bi-diagram-3"
    />

    <div v-else class="dashboard-container">
      <DetailBreadcrumb
        v-if="operationFilter"
        root-label="Traces"
        icon="bi-diagram-3"
        @back="clearOperation"
      >
        {{ operationFilter }}
      </DetailBreadcrumb>

      <TraceOverviewStats v-if="overview" :overview="overview" />

      <EmptyState
        v-if="filtered.length === 0"
        title="Not a root operation"
        message="No trace starts with this operation — it only ever appears as a child span."
        icon="bi-diagram-2"
      />

      <TraceSlowestList
        v-else
        :traces="filtered"
        :total="listTotal"
        :note="listNote"
        @row-click="openTrace"
      />

      <TraceSpansModal
        v-model:show="spansShow"
        :profile-id="profileId"
        :trace-id="selectedTrace?.traceId ?? ''"
        :root-name="selectedTrace?.rootName ?? ''"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DetailBreadcrumb from '@shared/components/DetailBreadcrumb.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TraceOverviewStats from '@/components/trace/TraceOverviewStats.vue';
import TraceSlowestList from '@/components/trace/TraceSlowestList.vue';
import TraceSpansModal from '@/components/trace/TraceSpansModal.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import type { TraceOverview, TraceRow } from '@/services/api/model/trace/TraceModels';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

/** Mirrors TracesController's DEFAULT_TRACES_LIMIT, which caps what `getTraces()` returns. */
const TRACE_FETCH_LIMIT = 100;

const traces = ref<TraceRow[]>([]);
const overview = ref<TraceOverview | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);

const selectedTrace = ref<TraceRow | null>(null);
const spansShow = ref(false);

const profileId = computed(() => route.params.profileId as string);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

/** Set by the Operations view, which links here rather than growing a drill-down of its own. */
const operationFilter = computed(() => (route.query.operation as string) ?? '');

const filtered = computed(() => {
  if (operationFilter.value === '') {
    return traces.value;
  }
  return traces.value.filter(trace => trace.rootName === operationFilter.value);
});

/**
 * The profile-wide count, so the header agrees with the overview card instead of reporting the
 * fetch cap as if it were the total. Left undefined for a per-operation subset, which is not
 * measured against the profile total.
 */
const listTotal = computed<number | undefined>(() => {
  if (operationFilter.value !== '' || !overview.value) {
    return undefined;
  }
  return overview.value.totalTraces;
});

/**
 * With the true total shown, the header would otherwise imply every trace is reachable. The
 * backend returns the slowest `TRACE_FETCH_LIMIT`, so say so when it actually withheld some.
 */
const listNote = computed<string | undefined>(() => {
  if (listTotal.value === undefined || listTotal.value <= TRACE_FETCH_LIMIT) {
    return undefined;
  }
  return `slowest ${TRACE_FETCH_LIMIT} fetched · sorted by duration`;
});

function openTrace(trace: TraceRow): void {
  selectedTrace.value = trace;
  spansShow.value = true;
  // The id lives in the URL while the modal is open, so a trace can still be linked to and returned
  // to -- the one thing the routed detail page gave that a modal on its own would not.
  router.replace({ query: { ...route.query, trace: trace.traceId } });
}

function clearOperation(): void {
  const query = { ...route.query };
  delete query.operation;
  router.replace({ query });
}

// Closing the modal takes the trace back out of the URL, so a reload does not reopen it.
watch(spansShow, open => {
  if (!open && route.query.trace) {
    const query = { ...route.query };
    delete query.trace;
    router.replace({ query });
  }
});

/**
 * Reopens the trace named in the URL. A bookmark can point at a trace outside the list's cap, in
 * which case the row is unknown here and the modal fetches everything it shows anyway.
 */
watch([() => route.query.trace, traces], ([traceId]) => {
  if (!traceId) {
    spansShow.value = false;
    return;
  }
  const id = traceId as string;
  selectedTrace.value = traces.value.find(trace => trace.traceId === id) ?? null;
  spansShow.value = true;
});

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileTracesClient(profileId.value);
    const [loadedOverview, loadedTraces] = await Promise.all([
      client.getOverview(),
      client.getTraces()
    ]);
    overview.value = loadedOverview;
    traces.value = loadedTraces;
  } catch {
    error.value = 'Failed to load the traces for this profile.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (!featureDisabled.value) {
    loadData();
  } else {
    loading.value = false;
  }
});
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
