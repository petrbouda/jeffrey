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
  All Traces: every trace in the profile, whatever its operation.

  The one entry point that does not start by choosing something. "Which were the slowest" and "what
  failed" are questions about the profile rather than about a type or an attribute, and answering
  them through the operations page meant first knowing which operation to open — which is the thing
  being asked. The narrowing, the ranking and the paging all happen on the server: this list is a
  page of what can be hundreds of thousands of traces, so ranking what was fetched would rank the
  page rather than the profile.
-->
<template>
  <div>
    <TracesDisabledFeatureAlert v-if="featureDisabled" />

    <LoadingState v-else-if="loading" message="Loading traces..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="untraced"
      title="No Traces"
      description="No trace-carrying events were recorded in this profile."
      icon="bi-list-ul"
    />

    <div v-else class="dashboard-container">
      <TraceOperationStats v-if="overview" :overview="overview" />

      <!--
        The same chart the operation drill-down and the attribute search draw, over every trace
        rather than a subset: three pages plot when traces happened, and they must not do it three
        different ways. Bucketed by the server over the whole recording, so a burst reads as a burst
        where it happened instead of being folded out of the page that happens to be loaded.
      -->
      <MainCard v-if="timeline.length > 0" class="mb-3">
        <template #header>
          <MainCardHeader icon="bi-graph-up" title="When Traces Happened" />
        </template>
        <TimeSeriesChart
          :primary-data="primaryData"
          primary-title="Trace Duration"
          :secondary-data="secondaryData"
          secondary-title="Traces"
          time-unit="milliseconds"
          :visible-minutes="60"
          :independent-secondary-axis="true"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.NUMBER"
        />
      </MainCard>

      <!--
        Rendered above the list and never with it: the controls are how a reader clears a filter, so
        they must not disappear along with the rows they filtered out.
      -->
      <div class="trace-filters">
        <div class="op-search">
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

        <button
          type="button"
          class="btn btn-sm"
          :class="errorsOnly ? 'btn-danger' : 'btn-outline-secondary'"
          @click="errorsOnly = !errorsOnly"
        >
          <i class="bi bi-exclamation-triangle"></i> Errors only
        </button>

        <select v-model="minDurationNanos" class="form-select form-select-sm trace-select">
          <option v-for="option in DURATION_FLOORS" :key="option.nanos" :value="option.nanos">
            {{ option.label }}
          </option>
        </select>

        <select v-model="sortKey" class="form-select form-select-sm trace-select">
          <option v-for="option in SORT_OPTIONS" :key="option.key" :value="option.key">
            {{ option.label }}
          </option>
        </select>
      </div>

      <LoadingState v-if="listLoading && traces.length === 0" message="Loading traces..." />

      <ErrorState v-else-if="listError" :message="listError" @retry="loadPage(0)" />

      <template v-else>
        <!--
          `total` is deliberately not passed: the card list would draw its own "showing X of Y"
          header on top of the footer's, and the two counts would have to be kept in step for no
          gain. `max-displayed` defeats the component's own 50-row default, because the page cut
          already happened on the server.
        -->
        <TraceCardList
          :items="traces"
          :trace="(trace: TraceRow) => trace"
          :p50-nanos="overview?.avgNanos"
          :p95-nanos="overview?.p95Nanos"
          :max-displayed="traces.length"
          empty-description="No trace matches the current filter."
          @open="openTrace"
        />

        <LoadMoreFooter
          v-if="traces.length > 0"
          :shown="traces.length"
          :total="totalMatching"
          noun="traces"
          :loading="listLoading"
          @load-more="loadMore"
        />
      </template>
    </div>

    <!--
      A sibling of the content rather than a child of it: GenericModal renders in place, so nested
      inside a branch that can be hidden it would mount invisibly, lock body scroll, and put its own
      dismiss handlers out of reach.
    -->
    <TraceSpansModal
      v-model:show="spansShow"
      :profile-id="profileId"
      :trace-id="selectedTrace?.traceId ?? ''"
      :root-name="selectedTrace?.rootName ?? ''"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LoadMoreFooter from '@shared/components/LoadMoreFooter.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TraceCardList from '@/components/trace/TraceCardList.vue';
import TraceOperationStats from '@/components/trace/TraceOperationStats.vue';
import TraceSpansModal from '@/components/trace/TraceSpansModal.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import FeatureType from '@/services/api/model/FeatureType';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type {
  TraceOverview,
  TraceRow,
  TraceSortField,
  TraceTimelineBucket
} from '@/services/api/model/trace/TraceModels';

/** One page of traces. Ranked by whatever the sort says, so the cut is always the tail of it. */
const PAGE_SIZE = 50;
/** How long to wait for typing to settle before asking the server again. */
const SEARCH_DEBOUNCE_MILLIS = 250;
/** Over the whole recording, like every other trace timeline. */
const TIMELINE_BUCKETS = 60;

const NANOS_PER_MILLI = 1_000_000;

/**
 * The duration floors offered, as nanoseconds. Coarse on purpose: the filter exists to cut a long
 * tail of fast traces out of the way, not to express an exact threshold — that is what sorting by
 * duration and reading from the top already does.
 */
const DURATION_FLOORS = [
  { nanos: 0, label: 'Any duration' },
  { nanos: 100 * NANOS_PER_MILLI, label: '≥ 100 ms' },
  { nanos: 500 * NANOS_PER_MILLI, label: '≥ 500 ms' },
  { nanos: 1_000 * NANOS_PER_MILLI, label: '≥ 1 s' }
];

/** Keyed by the backend's own `TraceSortField` names, so nothing has to be translated. */
const SORT_OPTIONS: { key: TraceSortField; label: string }[] = [
  { key: 'DURATION', label: 'Slowest first' },
  { key: 'START', label: 'Most recent first' },
  { key: 'SPAN_COUNT', label: 'Most spans' },
  { key: 'ERROR_COUNT', label: 'Most errors' }
];

const SORT_KEYS = new Set<string>(SORT_OPTIONS.map(option => option.key));
const DEFAULT_SORT: TraceSortField = 'DURATION';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

const traces = ref<TraceRow[]>([]);
const totalMatching = ref(0);
const timeline = ref<TraceTimelineBucket[]>([]);
const overview = ref<TraceOverview | null>(null);
const loading = ref(true);
const listLoading = ref(false);
/**
 * A failed page fetch stays inside the list region: the stats and the timeline above it are still
 * the profile's, and blanking the page would take a correct answer away to report a failure that
 * only concerns the rows.
 */
const listError = ref<string | null>(null);
const error = ref<string | null>(null);

/*
 * The filter lives in the URL, so a filtered list is a link — "the failed traces of this recording"
 * is something to paste into an issue rather than a set of instructions. The same contract the
 * sibling trace pages keep. Seeded here, mirrored back by the watcher below.
 */
const initialQuery = route.query;
const search = ref((initialQuery.q as string | undefined) ?? '');
const errorsOnly = ref(initialQuery.errors === '1');
const minDurationNanos = ref(Number(initialQuery.min ?? 0) || 0);
const sortKey = ref<TraceSortField>(
  SORT_KEYS.has(initialQuery.sort as string) ? (initialQuery.sort as TraceSortField) : DEFAULT_SORT
);

const profileId = computed(() => route.params.profileId as string);
const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));
const untraced = computed(() => (overview.value?.totalTraces ?? 0) === 0);

const primaryData = computed<number[][]>(() =>
  timeline.value.map(bucket => [bucket.fromMillisFromBeginning, bucket.maxDurationNanos])
);
const secondaryData = computed<number[][]>(() =>
  timeline.value.map(bucket => [bucket.fromMillisFromBeginning, bucket.count])
);

const spansShow = ref(false);
const selectedTrace = ref<TraceRow | null>(null);

function openTrace(trace: TraceRow): void {
  selectedTrace.value = trace;
  spansShow.value = true;
  // The id joins the filter already in the URL, so a waterfall reached from this list is as
  // linkable as the list itself. Pushed so Back closes the dialog and leaves the reader on their
  // rows, which is how they got here.
  router.push({ query: { ...route.query, trace: trace.traceId } });
}

// Closing takes the trace back out again, so returning to the link does not reopen it.
watch(spansShow, open => {
  if (!open && route.query.trace) {
    const query = { ...route.query };
    delete query.trace;
    router.replace({ query });
  }
});

/**
 * Reopens the trace named in the URL once the rows are in. A link can point at a trace beyond the
 * loaded page, in which case no row is found here — the modal fetches everything it draws from the
 * id alone, so it still opens, just without the row's own header values.
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
    // The profile's totals and its density, neither of which a page of rows can be folded into.
    const [profileOverview, buckets] = await Promise.all([
      client.getOverview(),
      client.getTracesTimeline(TIMELINE_BUCKETS)
    ]);
    overview.value = profileOverview;
    timeline.value = buckets;
    await loadPage(0);
  } catch {
    error.value = 'Failed to load the traces for this profile.';
  } finally {
    loading.value = false;
  }
}

/*
 * Guards against an out-of-order response: a load-more still in flight when a filter change fires a
 * fresh first page must not append its previous-filter rows onto the new list.
 */
let listGeneration = 0;

/** One page of the current filter; an offset of zero replaces the list, anything else appends. */
async function loadPage(offset: number): Promise<void> {
  const current = ++listGeneration;
  listLoading.value = true;
  listError.value = null;
  try {
    const page = await new ProfileTracesClient(profileId.value).getTraces({
      search: search.value,
      errorsOnly: errorsOnly.value,
      minDurationNanos: minDurationNanos.value,
      sort: sortKey.value,
      limit: PAGE_SIZE,
      offset
    });
    if (current !== listGeneration) {
      return;
    }
    traces.value = offset === 0 ? page.traces : [...traces.value, ...page.traces];
    totalMatching.value = page.totalMatching;
  } catch {
    if (current !== listGeneration) {
      return;
    }
    listError.value = 'Failed to load the traces for this filter.';
  } finally {
    if (current === listGeneration) {
      listLoading.value = false;
    }
  }
}

function loadMore(): void {
  loadPage(traces.value.length);
}

// Debounced because the search box changes on every keystroke; the selects and the toggle do not,
// but sharing one watcher keeps the refetch in a single place.
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch([search, errorsOnly, minDurationNanos, sortKey], () => {
  syncFilterQuery();
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => loadPage(0), SEARCH_DEBOUNCE_MILLIS);
});

/**
 * Mirrors the filter into the URL. Replaced, not pushed — a filter is view state, and Back should
 * step out of the page, not backspace through a search term. Defaults are left out so an unfiltered
 * link stays clean.
 */
function syncFilterQuery(): void {
  const next = { ...route.query };
  if (search.value) {
    next.q = search.value;
  } else {
    delete next.q;
  }
  if (errorsOnly.value) {
    next.errors = '1';
  } else {
    delete next.errors;
  }
  if (minDurationNanos.value > 0) {
    next.min = String(minDurationNanos.value);
  } else {
    delete next.min;
  }
  if (sortKey.value !== DEFAULT_SORT) {
    next.sort = sortKey.value;
  } else {
    delete next.sort;
  }
  router.replace({ query: next });
}

onUnmounted(() => clearTimeout(searchTimer));

onMounted(() => {
  if (featureDisabled.value) {
    loading.value = false;
  } else {
    loadData();
  }
});
</script>

<style scoped>
.trace-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-3);
}

.trace-select {
  width: auto;
  font-size: 0.8rem;
}

/* The toolbar's search affordance, matching the operations page's filter row. */
.op-search {
  position: relative;
}

.op-search i.bi-search {
  position: absolute;
  left: 0.6rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-light);
  font-size: 0.75rem;
  pointer-events: none;
}

.op-search .form-control {
  padding-left: 1.8rem;
  padding-right: 1.8rem;
  font-size: 0.8rem;
  min-width: 220px;
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
</style>
