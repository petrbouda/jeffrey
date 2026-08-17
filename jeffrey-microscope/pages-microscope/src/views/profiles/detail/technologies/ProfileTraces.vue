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
      v-else-if="untraced"
      title="No Traces"
      description="No trace-carrying events were recorded in this profile."
      icon="bi-diagram-3"
    />

    <div v-else class="dashboard-container">
      <TraceOverviewStats v-if="overview" :overview="overview" />

      <MainCard v-if="timeline.length > 0">
        <template #header>
          <MainCardHeader icon="bi-graph-up" title="Traces over the recording" />
        </template>
        <TimeSeriesChart
          :primary-data="timelineDurations"
          primary-title="Slowest Trace"
          :secondary-data="timelineCounts"
          secondary-title="Traces"
          time-unit="milliseconds"
          :visible-minutes="60"
          :independent-secondary-axis="true"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.NUMBER"
        />
      </MainCard>

      <MainCard>
        <template #header>
          <MainCardHeader icon="bi-diagram-3" title="Traces" />
        </template>

        <TableToolbar v-model="search" search-placeholder="Filter by operation name...">
          <template #filters>
            <!--
              The exact-operation filter has no control of its own on this page — it only ever
              arrives in the URL — so the chip is how the reader learns the list is narrowed, and
              the × is the way back to everything.
            -->
            <span v-if="operationFilter" class="op-filter-chip" :title="operationFilterTitle">
              <i class="bi bi-bar-chart-steps"></i> {{ operationFilter.name }}
              <button
                type="button"
                class="op-filter-clear"
                title="Show every operation's traces"
                @click="operationFilter = null"
              >
                <i class="bi bi-x-lg"></i>
              </button>
            </span>
            <button
              type="button"
              class="btn btn-sm"
              :class="errorsOnly ? 'btn-danger' : 'btn-outline-secondary'"
              @click="errorsOnly = !errorsOnly"
            >
              <i class="bi bi-exclamation-triangle"></i> Errors only
            </button>
            <select v-model="sort" class="form-select form-select-sm sort-select">
              <option value="DURATION">Slowest first</option>
              <option value="START">Most recent first</option>
              <option value="SPAN_COUNT">Most spans first</option>
              <option value="ERROR_COUNT">Most errors first</option>
            </select>
          </template>
        </TableToolbar>

        <LoadingState v-if="listLoading && traces.length === 0" message="Loading traces..." />

        <ErrorState v-else-if="listError" :message="listError" @retry="loadPage(0)" />

        <EmptyState
          v-else-if="traces.length === 0"
          title="No matching traces"
          description="No trace matches the current filter."
          icon="bi-search"
        />

        <template v-else>
          <!-- Ordered and paged by the server, so the list must not re-sort or re-slice it. -->
          <TraceSlowestList
            :traces="traces"
            :total="totalMatching"
            :note="listNote"
            server-ordered
            @row-click="openTrace"
          />
          <LoadMoreFooter
            :shown="traces.length"
            :total="totalMatching"
            noun="traces"
            :loading="listLoading"
            @load-more="loadMore"
          />
        </template>
      </MainCard>

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
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import LoadMoreFooter from '@shared/components/LoadMoreFooter.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TraceOverviewStats from '@/components/trace/TraceOverviewStats.vue';
import TraceSlowestList from '@/components/trace/TraceSlowestList.vue';
import TraceSpansModal from '@/components/trace/TraceSpansModal.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import type {
  SpanKind,
  TraceOperationId,
  TraceOverview,
  TraceRow,
  TraceSortField,
  TraceTimelineBucket
} from '@/services/api/model/trace/TraceModels';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

/** One page of traces. Small enough that a filter feels immediate, large enough to scroll. */
const PAGE_SIZE = 50;
/**
 * How the recording is sliced for the density strip. Enough points to see where the profile got
 * busy, few enough that each one still covers a meaningful stretch.
 */
const TIMELINE_BUCKETS = 60;
/** How long to wait for typing to settle before asking the server again. */
const SEARCH_DEBOUNCE_MILLIS = 250;

const traces = ref<TraceRow[]>([]);
const totalMatching = ref(0);
const timeline = ref<TraceTimelineBucket[]>([]);
const overview = ref<TraceOverview | null>(null);
const loading = ref(true);
const listLoading = ref(false);
/**
 * Failures of the incremental list fetches stay inside the list region. They used to write the
 * page-level `error`, so one failed "Load more" or filter refetch blanked the stats, the timeline
 * and every row already on screen, replacing a page of good data with a whole-page error.
 */
const listError = ref<string | null>(null);
const error = ref<string | null>(null);

const SORT_FIELDS = new Set<string>(['DURATION', 'START', 'SPAN_COUNT', 'ERROR_COUNT']);
const DEFAULT_SORT: TraceSortField = 'DURATION';

/*
 * The filter lives in the URL, so a filtered list is shareable — "look at the failed traces" is a
 * link, not a set of instructions to reproduce. Seeded here, mirrored back by the watcher below.
 */
const initialQuery = route.query;
const search = ref((initialQuery.q as string | undefined) ?? '');
const errorsOnly = ref(initialQuery.errors === '1');
const sort = ref<TraceSortField>(
  SORT_FIELDS.has(initialQuery.sort as string)
    ? (initialQuery.sort as TraceSortField)
    : DEFAULT_SORT
);

/**
 * Narrows the list to one exact operation — the edge in from an operation's detail page. The whole
 * triple travels or none of it does, since the name alone conflates an inbound and an outbound
 * call of the same name.
 */
const operationFilter = ref<TraceOperationId | null>(readOperationFilter());

function readOperationFilter(): TraceOperationId | null {
  const name = initialQuery.operation as string | undefined;
  const kind = initialQuery.kind as SpanKind | undefined;
  const eventType = initialQuery.eventType as string | undefined;
  if (!name || !kind || !eventType) {
    return null;
  }
  return { name, kind, eventType };
}

const operationFilterTitle = computed(() => {
  const operation = operationFilter.value;
  return operation === null
    ? ''
    : `Only ${operation.kind} ${operation.name} traces (${operation.eventType})`;
});

const selectedTrace = ref<TraceRow | null>(null);
const spansShow = ref(false);

const profileId = computed(() => route.params.profileId as string);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

/**
 * Whether the profile holds no traces at all, as opposed to none matching the filter. Taken from the
 * overview rather than from the list, which a filter can empty at any time — the two say very
 * different things and only one of them means the feature has nothing to show.
 */
const untraced = computed(() => (overview.value?.totalTraces ?? 0) === 0);

/** What the list is ordered by, said in words, since the rows no longer imply it. */
const listNote = computed(() => {
  const direction = {
    DURATION: 'sorted by duration',
    START: 'most recent first',
    SPAN_COUNT: 'most spans first',
    ERROR_COUNT: 'most errors first'
  }[sort.value];
  return errorsOnly.value ? `${direction} · failed traces only` : direction;
});

/** Bucket start against slowest trace, the pair that makes a latency spike visible. */
const timelineDurations = computed(() =>
  timeline.value.map(bucket => [bucket.fromMillisFromBeginning, bucket.maxDurationNanos])
);

const timelineCounts = computed(() =>
  timeline.value.map(bucket => [bucket.fromMillisFromBeginning, bucket.count])
);

function openTrace(trace: TraceRow): void {
  selectedTrace.value = trace;
  spansShow.value = true;
  // The id lives in the URL while the modal is open, so a trace can still be linked to and returned
  // to -- the one thing the routed detail page gave that a modal on its own would not. Pushed, not
  // replaced: opening a trace is a navigation, and Back is how a dialog is expected to close --
  // the sibling operations page already pushes its drill-down, and Back behaving differently on
  // two pages that look the same is a trap.
  router.push({ query: { ...route.query, trace: trace.traceId } });
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
    const [loadedOverview, loadedTimeline] = await Promise.all([
      client.getOverview(),
      client.getTimeline(TIMELINE_BUCKETS)
    ]);
    overview.value = loadedOverview;
    timeline.value = loadedTimeline;
    await loadPage(0);
  } catch {
    error.value = 'Failed to load the traces for this profile.';
  } finally {
    loading.value = false;
  }
}

/**
 * Fetches one page of the current filter. An offset of zero replaces the list; anything else appends
 * to it, which is what makes "load more" a continuation rather than a jump.
 */
async function loadPage(offset: number): Promise<void> {
  listLoading.value = true;
  listError.value = null;
  try {
    const page = await new ProfileTracesClient(profileId.value).getTraces({
      search: search.value,
      errorsOnly: errorsOnly.value,
      ...(operationFilter.value ?? {}),
      sort: sort.value,
      limit: PAGE_SIZE,
      offset
    });
    traces.value = offset === 0 ? page.traces : [...traces.value, ...page.traces];
    totalMatching.value = page.totalMatching;
  } catch {
    listError.value = 'Failed to load the traces for this filter.';
  } finally {
    listLoading.value = false;
  }
}

function loadMore(): void {
  loadPage(traces.value.length);
}

/**
 * Refetches from the top whenever the filter changes. Debounced because the search box changes on
 * every keystroke, and each one would otherwise be a round trip whose answer is thrown away by the
 * next letter.
 */
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch([search, errorsOnly, sort, operationFilter], () => {
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
  if (sort.value !== DEFAULT_SORT) {
    next.sort = sort.value;
  } else {
    delete next.sort;
  }
  const operation = operationFilter.value;
  if (operation !== null) {
    next.operation = operation.name;
    next.kind = operation.kind;
    next.eventType = operation.eventType;
  } else {
    delete next.operation;
    delete next.kind;
    delete next.eventType;
  }
  router.replace({ query: next });
}

onUnmounted(() => clearTimeout(searchTimer));

onMounted(() => {
  if (!featureDisabled.value) {
    loadData();
  } else {
    loading.value = false;
  }
});
</script>

<style scoped>
/* Narrow enough to sit beside the search box rather than crowding it off the toolbar. */
.sort-select {
  width: auto;
}

/* The narrowed-to-one-operation state, worn where the other filters live. */
.op-filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  max-width: 22rem;
  padding: 0.25rem 0.35rem 0.25rem 0.6rem;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-pill);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.op-filter-clear {
  display: inline-flex;
  align-items: center;
  border: 0;
  padding: 0.1rem;
  border-radius: var(--radius-pill);
  background: transparent;
  color: inherit;
  font-size: 0.65rem;
  cursor: pointer;
}

.op-filter-clear:hover {
  background: var(--color-primary);
  color: var(--color-white);
}

.op-filter-clear:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
</style>
