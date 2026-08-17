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

<!--
  Trace Timeseries: pick one operation on the left, read its latency and call volume over the
  recording on the right. The split replaces the old "In trace list" edge out of the operation
  detail — the picker keeps the ranked list in view while the charts answer "when was it slow".
-->
<template>
  <div>
    <TracesDisabledFeatureAlert v-if="featureDisabled" />

    <LoadingState v-else-if="loading" message="Loading operations..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="untraced"
      title="No Operations"
      description="No trace-carrying events were recorded in this profile."
      icon="bi-graph-up"
    />

    <div v-else class="dashboard-container timeseries-layout">
      <!-- Picker: single-select, ranked by total time so it doubles as a "where did time go" list. -->
      <MainCard class="picker-card">
        <template #header>
          <MainCardHeader icon="bi-bar-chart-steps" title="Operations" :badge="totalMatching" />
        </template>

        <div class="picker">
          <div class="picker-search">
            <i class="bi bi-search"></i>
            <input
              v-model="search"
              type="text"
              class="form-control form-control-sm"
              placeholder="Filter by operation name..."
            />
            <button v-if="search" class="btn-clear" @click="search = ''">
              <i class="bi bi-x-lg"></i>
            </button>
          </div>

          <div v-if="operations.length > 0" class="picker-list">
            <button
              v-for="row in operations"
              :key="operationKey(row)"
              type="button"
              class="pick-row"
              :class="{ selected: isSelected(row) }"
              :title="row.name"
              @click="select(row)"
            >
              <MetricName
                class="pick-name"
                :segments="parseOperationName(row.name, row.eventType)"
                :title="row.name"
              />
              <span class="pick-count">{{ FormattingService.formatNumber(row.count) }}</span>
            </button>
          </div>

          <div v-else class="picker-empty">No operation matches the filter.</div>

          <!-- Silence about a cap reads as "this is all of them", which it would not be. -->
          <div v-if="totalMatching > operations.length" class="picker-note">
            Showing {{ operations.length }} of {{ totalMatching }} — refine the filter
          </div>
        </div>
      </MainCard>

      <!-- Charts for the selected operation -->
      <div class="charts-column">
        <EmptyState
          v-if="selectedOperation === null"
          title="Select an operation"
          description="Pick an operation on the left to see its latency percentiles and call volume over the recording."
          icon="bi-graph-up"
        />

        <template v-else>
          <div class="op-header">
            <MetricName
              :segments="parseOperationName(selectedOperation.name, selectedOperation.eventType)"
              :title="selectedOperation.name"
            />
            <Badge
              :value="selectedOperation.eventType"
              variant="secondary"
              size="s"
              borderless
              :uppercase="false"
            />
            <Badge
              :value="selectedOperation.kind"
              :variant="spanKindVariant(selectedOperation.kind)"
              size="s"
              borderless
            />
            <span class="op-stats">
              {{ FormattingService.formatNumber(headerStats.calls) }} calls
              · total {{ FormattingService.formatDuration2Units(headerStats.totalNanos) }}
            </span>
          </div>

          <LoadingState v-if="tracesLoading" message="Loading traces..." />

          <ErrorState v-else-if="tracesError" :message="tracesError" @retry="loadTraces" />

          <EmptyState
            v-else-if="traces.length === 0"
            title="No traces"
            description="This operation has no traces in the recording."
            icon="bi-graph-up"
          />

          <template v-else>
            <MainCard>
              <template #header>
                <MainCardHeader icon="bi-graph-up" title="Duration" />
              </template>
              <TimeSeriesChart
                :primary-data="p50Data"
                primary-title="P50"
                :secondary-data="p95Data"
                secondary-title="P95"
                :tertiary-data="maxData"
                tertiary-title="Max"
                time-unit="milliseconds"
                :visible-minutes="60"
                :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
                :secondary-axis-type="AxisFormatType.DURATION_IN_NANOS"
                :tertiary-axis-type="AxisFormatType.DURATION_IN_NANOS"
              />
            </MainCard>

            <MainCard>
              <template #header>
                <MainCardHeader icon="bi-bar-chart" title="Invocations" />
              </template>
              <TimeSeriesChart
                :primary-data="callsData"
                primary-title="Invocations"
                chart-type="bar"
                time-unit="milliseconds"
                :visible-minutes="60"
                :primary-axis-type="AxisFormatType.NUMBER"
              />
            </MainCard>

            <div v-if="truncated" class="cap-note">
              Charts cover the first {{ TRACE_LIMIT }} traces of this operation.
            </div>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import FormattingService from '@shared/services/FormattingService';
import MetricName from '@/components/common/MetricName.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import FeatureType from '@/services/api/model/FeatureType';
import { operationKey, parseOperationName, spanKindVariant } from '@/services/trace/traceLabels';
import { percentileTimelineBuckets } from '@/services/trace/traceTimelineBuckets';
import { profileStore } from '@/stores/profileStore';
import type {
  SpanKind,
  TraceOperationId,
  TraceOperationRow,
  TraceOverview,
  TraceRow
} from '@/services/api/model/trace/TraceModels';

/** Columns per chart — the resolution the sibling Metrics Timeline uses. */
const TIMELINE_BUCKETS = 40;
/** Matches the operation detail's cap; an operation with more traces than this is sampled, not drawn whole. */
const TRACE_LIMIT = 1000;
/** Rows the picker holds; past this the caller is told to refine rather than scroll a longer list. */
const PICKER_LIMIT = 100;
/** How long to wait for typing to settle before asking the server again. */
const SEARCH_DEBOUNCE_MILLIS = 250;

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const error = ref<string | null>(null);
const overview = ref<TraceOverview | null>(null);
const operations = ref<TraceOperationRow[]>([]);
const totalMatching = ref(0);
const search = ref((route.query.q as string | undefined) ?? '');

const traces = ref<TraceRow[]>([]);
const truncated = ref(false);
const tracesLoading = ref(false);
const tracesError = ref<string | null>(null);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

/** No traces at all, as opposed to none matching the picker's filter. */
const untraced = computed(() => (overview.value?.distinctOperations ?? 0) === 0);

const profileId = computed(() => route.params.profileId as string);

/**
 * The selection lives in the URL, so a chart worth discussing is a link and the operation pages can
 * deep-link here. All three parts travel because all three identify the operation — see the
 * operations page for why a name alone is ambiguous.
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

function isSelected(row: TraceOperationRow): boolean {
  const selected = selectedOperation.value;
  return selected !== null && operationKey(selected) === operationKey(row);
}

// Replaced, not pushed: switching the selected operation is view state, and putting every pick in
// history would make Back walk through operations before it steps out of the page.
function select(row: TraceOperationRow): void {
  router.replace({
    query: { ...route.query, operation: row.name, kind: row.kind, eventType: row.eventType }
  });
}

/**
 * Header figures come from the listed row when the picker holds it — those aggregates cover every
 * trace of the type — and fall back to the fetched (possibly capped) traces for a deep link whose
 * operation the capped picker list does not contain.
 */
const headerStats = computed(() => {
  const selected = selectedOperation.value;
  if (selected !== null) {
    const key = operationKey(selected);
    const row = operations.value.find(candidate => operationKey(candidate) === key);
    if (row !== undefined) {
      return { calls: row.count, totalNanos: row.totalNanos };
    }
  }
  return {
    calls: traces.value.length,
    totalNanos: traces.value.reduce((sum, trace) => sum + trace.durationNanos, 0)
  };
});

// Null before the profile loads, and for a recording that never reported its bounds; the buckets
// then fall back to the range the traces themselves cover.
const recordingSpan = computed(() => {
  const window = profileStore.recordingWindow.value;
  return window === null ? undefined : { from: 0, to: window.durationMillis };
});

/*
 * Plotted against the recording's own clock, like every other timeline in the profile: a burst
 * reads as a burst, in the place it happened, instead of being stretched to fill the axis.
 */
const buckets = computed(() =>
  percentileTimelineBuckets(
    traces.value,
    trace => trace.startMillisFromBeginning,
    trace => trace.durationNanos,
    TIMELINE_BUCKETS,
    recordingSpan.value
  )
);

const p50Data = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.p50]));
const p95Data = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.p95]));
const maxData = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.max]));
const callsData = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.count]));

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    overview.value = await new ProfileTracesClient(profileId.value).getOverview();
    await loadOperations();
  } catch {
    error.value = 'Failed to load the trace operations for this profile.';
  } finally {
    loading.value = false;
  }
}

async function loadOperations(): Promise<void> {
  const page = await new ProfileTracesClient(profileId.value).getOperations({
    search: search.value,
    limit: PICKER_LIMIT
  });
  operations.value = page.operations;
  totalMatching.value = page.totalMatching;
}

/*
 * Guards against an out-of-order response: a slow fetch for a previously selected operation must
 * not land on top of the charts of the one selected after it.
 */
let tracesGeneration = 0;

async function loadTraces(): Promise<void> {
  const selected = selectedOperation.value;
  if (selected === null) {
    traces.value = [];
    truncated.value = false;
    return;
  }
  const generation = ++tracesGeneration;
  tracesLoading.value = true;
  tracesError.value = null;
  try {
    // One past the cap, so a full page can be told apart from a page that merely filled it.
    const operationTraces = await new ProfileTracesClient(profileId.value).getOperationTraces(
      selected,
      TRACE_LIMIT + 1
    );
    if (generation !== tracesGeneration) {
      return;
    }
    truncated.value = operationTraces.length > TRACE_LIMIT;
    traces.value = truncated.value ? operationTraces.slice(0, TRACE_LIMIT) : operationTraces;
  } catch {
    if (generation !== tracesGeneration) {
      return;
    }
    tracesError.value = 'Failed to load the traces of this operation.';
    traces.value = [];
    truncated.value = false;
  } finally {
    if (generation === tracesGeneration) {
      tracesLoading.value = false;
    }
  }
}

watch(selectedOperation, loadTraces);

// Debounced because the search box changes on every keystroke. The filter is mirrored into the URL
// (replaced, not pushed) so a filtered picker is shareable, same contract as the sibling pages.
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch(search, () => {
  const query = { ...route.query };
  if (search.value) {
    query.q = search.value;
  } else {
    delete query.q;
  }
  router.replace({ query });
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => loadOperations().catch(() => {}), SEARCH_DEBOUNCE_MILLIS);
});

onUnmounted(() => clearTimeout(searchTimer));

onMounted(() => {
  if (featureDisabled.value) {
    loading.value = false;
    return;
  }
  loadData();
  loadTraces();
});
</script>

<style scoped>
.timeseries-layout {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.picker-card {
  width: 320px;
  flex: none;
  /* Stays in reach while the charts scroll — the picker is the page's navigation. */
  position: sticky;
  top: 1rem;
}

.charts-column {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.picker {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.75rem;
}

/* The toolbar's search affordance, in the picker's own column. */
.picker-search {
  position: relative;
}

.picker-search i.bi-search {
  position: absolute;
  left: 0.6rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-light);
  font-size: 0.75rem;
  pointer-events: none;
}

.picker-search .form-control {
  padding-left: 1.8rem;
  padding-right: 1.8rem;
  font-size: 0.8rem;
}

.btn-clear {
  position: absolute;
  right: 0.4rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--color-text-light);
  font-size: 0.6rem;
  cursor: pointer;
  padding: 2px;
  line-height: 1;
}

.btn-clear:hover {
  color: var(--color-text);
}

.picker-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-height: 60vh;
  overflow-y: auto;
}

.pick-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  text-align: left;
  padding: 0.4rem 0.55rem;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
}

.pick-row:hover {
  background: var(--color-primary-lighter);
}

.pick-row:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.pick-row.selected {
  background: var(--color-primary-light);
  border-color: var(--color-primary-border-light);
}

/* Typography comes from MetricName's segment vocabulary; this only slots it into the row. */
.pick-name {
  flex: 1;
  min-width: 0;
}

.pick-count {
  flex: none;
  font-size: var(--font-size-xs);
  color: var(--color-text-light);
}

.picker-empty,
.picker-note {
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
  padding: 0.25rem 0.25rem 0;
}

.op-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.op-stats {
  margin-left: auto;
  flex: none;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.cap-note {
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
}
</style>
