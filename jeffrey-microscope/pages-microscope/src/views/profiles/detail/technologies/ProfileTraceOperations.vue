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
      v-else-if="untraced"
      title="No Operations"
      description="No trace-carrying events were recorded in this profile."
      icon="bi-bar-chart-steps"
    />

    <div v-else class="dashboard-container">
      <template v-if="selectedOperation === null">
        <TraceOperationStats v-if="overview" :overview="overview" />

        <TableToolbar v-model="search" search-placeholder="Filter by operation name...">
          <template #filters>
            <button
              type="button"
              class="btn btn-sm"
              :class="errorsOnly ? 'btn-danger' : 'btn-outline-secondary'"
              @click="errorsOnly = !errorsOnly"
            >
              <i class="bi bi-exclamation-triangle"></i> Errors only
            </button>
          </template>
        </TableToolbar>

        <LoadingState
          v-if="listLoading && operations.length === 0"
          message="Loading operations..."
        />

        <ErrorState v-else-if="listError" :message="listError" @retry="loadPage(0)" />

        <EmptyState
          v-else-if="operations.length === 0"
          title="No matching operations"
          description="No operation matches the current filter."
          icon="bi-search"
        />

        <template v-else>
          <TraceOperationList
            :operations="operations"
            :sort-key="sortKey"
            @operation-click="openOperation"
            @sort-change="changeSort"
          />
          <LoadMoreFooter
            :shown="operations.length"
            :total="totalMatching"
            noun="operations"
            :loading="listLoading"
            @load-more="loadMore"
          />
        </template>
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
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DetailBreadcrumb from '@shared/components/DetailBreadcrumb.vue';
import LoadMoreFooter from '@shared/components/LoadMoreFooter.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TraceOperationStats from '@/components/trace/TraceOperationStats.vue';
import TraceOperationList from '@/components/trace/TraceOperationList.vue';
import type { TraceOperationSortKey } from '@/components/trace/TraceOperationList.vue';
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

/** One page of operations. Ranked by whatever the sort says, so the cut is always the tail of it. */
const PAGE_SIZE = 50;
/** How long to wait for typing to settle before asking the server again. */
const SEARCH_DEBOUNCE_MILLIS = 250;

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

const operations = ref<TraceOperationRow[]>([]);
const totalMatching = ref(0);
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

const SORT_KEYS = new Set<string>(['TOTAL_TIME', 'P95', 'P99', 'MAX', 'COUNT', 'ERRORS']);
const DEFAULT_SORT: TraceOperationSortKey = 'TOTAL_TIME';

/*
 * The filter lives in the URL, so a filtered list is shareable — same contract as the sibling
 * Slowest Traces page. Seeded here, mirrored back by the watcher below.
 */
const initialQuery = route.query;
const search = ref((initialQuery.q as string | undefined) ?? '');
const errorsOnly = ref(initialQuery.errors === '1');
const sortKey = ref<TraceOperationSortKey>(
  SORT_KEYS.has(initialQuery.sort as string)
    ? (initialQuery.sort as TraceOperationSortKey)
    : DEFAULT_SORT
);

/**
 * Whether the profile holds no traces at all, as opposed to none matching the filter — only the
 * first of those means the page has nothing to offer.
 */
const untraced = computed(() => (overview.value?.distinctOperations ?? 0) === 0);

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
  // Any lingering ?tab= belongs to a previously viewed operation, not this one.
  const query = { ...route.query };
  delete query.tab;
  router.push({
    query: {
      ...query,
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
  delete query.tab;
  router.push({ query });
}

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    overview.value = await new ProfileTracesClient(profileId.value).getOverview();
    await loadPage(0);
  } catch {
    error.value = 'Failed to load the trace operations for this profile.';
  } finally {
    loading.value = false;
  }
}

/** One page of the current filter; an offset of zero replaces the list, anything else appends. */
async function loadPage(offset: number): Promise<void> {
  listLoading.value = true;
  listError.value = null;
  try {
    const page = await new ProfileTracesClient(profileId.value).getOperations({
      search: search.value,
      errorsOnly: errorsOnly.value,
      sort: sortKey.value,
      limit: PAGE_SIZE,
      offset
    });
    operations.value = offset === 0 ? page.operations : [...operations.value, ...page.operations];
    totalMatching.value = page.totalMatching;
  } catch {
    listError.value = 'Failed to load the operations for this filter.';
  } finally {
    listLoading.value = false;
  }
}

function loadMore(): void {
  loadPage(operations.value.length);
}

function changeSort(key: TraceOperationSortKey): void {
  sortKey.value = key;
}

// Debounced because the search box changes on every keystroke; the sort and the toggle do not, but
// sharing one watcher keeps the refetch in a single place.
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch([search, errorsOnly, sortKey], () => {
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
