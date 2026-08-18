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

    <LoadingState v-else-if="loading" message="Loading attributes..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadCatalog" />

    <EmptyState
      v-else-if="keys.length === 0"
      title="No Attributes"
      description="No span in this profile recorded an attribute, a declared field or a shape worth querying."
      icon="bi-tags"
    />

    <div v-else class="dashboard-container attributes-layout">
      <TraceAttributeCatalog
        :keys="keys"
        :selected="selectedKey"
        :total-traces="totalTraces"
        @select="openKey"
      />

      <div class="attributes-workspace">
        <TabBar v-model="mode" :tabs="tabs" />

        <!-- 01 — find traces by what their spans recorded -->
        <template v-if="mode === 'search'">
          <TraceAttributeSearchBar
            :keys="keys"
            :conditions="conditions"
            :scope="scope"
            @update:conditions="applyConditions"
            @update:scope="applyScope"
          />

          <LoadingState v-if="searchLoading && search === null" message="Searching traces..." />

          <ErrorState v-else-if="searchError" :message="searchError" @retry="runSearch" />

          <TraceAttributeResults
            v-else-if="search"
            :matches="search.matches"
            :total-matching="search.totalMatching"
            :stats="search.stats"
            :baseline="baseline"
            :timeline="timeline"
            :loading-more="searchLoading"
            @open="openTrace"
            @load-more="loadMore"
          />
        </template>

        <!-- 02 and 03 — one key, broken down and then distributed -->
        <template v-else-if="mode === 'values' || mode === 'latency'">
          <EmptyState
            v-if="selectedKey === null"
            icon="bi-tag"
            title="No key selected"
            description="Pick a key on the left to break it down."
          />

          <template v-else>
            <DetailBreadcrumb root-label="Attributes" icon="bi-tags" @back="clearKey">
              {{ keyLabel(selectedKey) }}
            </DetailBreadcrumb>

            <LoadingState v-if="keyLoading" message="Loading values..." />

            <ErrorState v-else-if="keyError" :message="keyError" @retry="loadKey" />

            <TraceAttributeValues
              v-else-if="mode === 'values' && values"
              :values="values"
              :sort="valueSort"
              :descending="true"
              @sort="applyValueSort"
              @pick="filterByValue"
            />

            <TraceAttributeLatency v-else-if="mode === 'latency' && latency" :latency="latency" />
          </template>
        </template>

        <!-- 04 — no theory yet: rank every key/value by how much it stands out -->
        <template v-else>
          <LoadingState v-if="differencesLoading" message="Comparing traces..." />

          <ErrorState
            v-else-if="differencesError"
            :message="differencesError"
            @retry="loadDifferences"
          />

          <TraceAttributeDifferences
            v-else-if="differences"
            :differences="differences"
            :selection="selection"
            @select="applySelection"
            @filter="filterByDifference"
          />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DetailBreadcrumb from '@shared/components/DetailBreadcrumb.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import TabBar, { type TabBarItem } from '@shared/components/TabBar.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TraceAttributeCatalog from '@/components/trace/TraceAttributeCatalog.vue';
import TraceAttributeDifferences from '@/components/trace/TraceAttributeDifferences.vue';
import type { DifferenceSelection } from '@/components/trace/TraceAttributeDifferences.vue';
import TraceAttributeLatency from '@/components/trace/TraceAttributeLatency.vue';
import TraceAttributeResults from '@/components/trace/TraceAttributeResults.vue';
import TraceAttributeSearchBar from '@/components/trace/TraceAttributeSearchBar.vue';
import TraceAttributeValues from '@/components/trace/TraceAttributeValues.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import {
  decodeCondition,
  encodeCondition,
  keyLabel,
  type TraceAttributeConditionModel,
  type TraceAttributeDifferenceRow,
  type TraceAttributeDifferences as Differences,
  type TraceAttributeKeyId,
  type TraceAttributeKeyRow,
  type TraceAttributeLatency as Latency,
  type TraceAttributeScope,
  type TraceAttributeSearchResult,
  type TraceAttributeStats,
  type TraceAttributeTimelineBucket,
  type TraceAttributeValues as Values,
  type TraceAttributeValueRow,
  type TraceAttributeValueSortField
} from '@/services/api/model/trace/TraceAttributeModels';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';
import FeatureType from '@/services/api/model/FeatureType';

type Mode = 'search' | 'values' | 'latency' | 'differences';

// Resolved by ProfileDetail's router-view before this view is rendered, like every other
// technology page.
const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;
const client = new ProfileTracesClient(profileId);

/** A page of matches; the load-more button asks for another. */
const PAGE_SIZE = 50;
const DEFAULT_SELECTION: DifferenceSelection = { minDurationNanos: 500_000_000, errorsOnly: false };

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

const loading = ref(true);
const error = ref<string | null>(null);
const keys = ref<TraceAttributeKeyRow[]>([]);
const totalTraces = ref(0);
const baseline = ref<TraceAttributeStats | null>(null);

const searchLoading = ref(false);
const searchError = ref<string | null>(null);
const search = ref<TraceAttributeSearchResult | null>(null);
const timeline = ref<TraceAttributeTimelineBucket[]>([]);

const keyLoading = ref(false);
const keyError = ref<string | null>(null);
const values = ref<Values | null>(null);
const latency = ref<Latency | null>(null);
const valueSort = ref<TraceAttributeValueSortField>('TOTAL_TIME');

const differencesLoading = ref(false);
const differencesError = ref<string | null>(null);
const differences = ref<Differences | null>(null);

/*
 * Everything that decides what is on screen lives in the URL: the mode, the conditions, the scope
 * and the selected key. A filter is then a link — "the failed traces of this tenant" is something to
 * paste into an issue rather than a set of instructions — and the browser's Back button steps
 * through the investigation instead of off the page.
 */
const mode = computed<Mode>({
  get: () => ((route.query.view as Mode | undefined) ?? 'search') as Mode,
  set: view => {
    router.push({ query: { ...route.query, view } });
  }
});

const conditions = computed<TraceAttributeConditionModel[]>(() => {
  const raw = route.query.where;
  const encoded = raw === undefined ? [] : Array.isArray(raw) ? raw : [raw];
  return encoded
    .filter((value): value is string => typeof value === 'string')
    .map(decodeCondition)
    .filter((condition): condition is TraceAttributeConditionModel => condition !== null);
});

const scope = computed<TraceAttributeScope>(
  () => ((route.query.scope as TraceAttributeScope | undefined) ?? 'TRACE') as TraceAttributeScope
);

const selectedKey = computed<TraceAttributeKeyId | null>(() => {
  const key = route.query.key as string | undefined;
  if (key === undefined) {
    return null;
  }
  return {
    source: (route.query.source as TraceAttributeKeyId['source'] | undefined) ?? 'ATTRIBUTE',
    owner: (route.query.owner as string | undefined) ?? null,
    key
  };
});

const selection = computed<DifferenceSelection>(() => {
  const minDurationNanos = Number(route.query.minDurationNanos ?? 0);
  const errorsOnly = route.query.errorsOnly === 'true';
  if (minDurationNanos <= 0 && !errorsOnly) {
    return DEFAULT_SELECTION;
  }
  return { minDurationNanos, errorsOnly };
});

const tabs = computed<TabBarItem[]>(() => [
  { id: 'search', label: 'Search', icon: 'search' },
  {
    id: 'values',
    label: 'Values',
    icon: 'tag',
    disabled: selectedKey.value === null
  },
  {
    id: 'latency',
    label: 'Latency',
    icon: 'grid-3x3',
    disabled: selectedKey.value === null
  },
  { id: 'differences', label: "What's different?", icon: 'bar-chart-steps' }
]);

function openKey(key: TraceAttributeKeyRow): void {
  router.push({
    query: {
      ...route.query,
      // A search-only key has no breakdown to open, so clicking it puts it in the search builder
      // instead of opening a screen that would only report that it will not draw anything.
      view: key.searchOnly ? 'search' : 'values',
      source: key.source,
      owner: key.owner ?? undefined,
      key: key.key
    }
  });
}

function clearKey(): void {
  const query = { ...route.query };
  delete query.key;
  delete query.owner;
  delete query.source;
  query.view = 'search';
  router.push({ query });
}

function applyConditions(next: TraceAttributeConditionModel[]): void {
  router.push({
    query: { ...route.query, where: next.map(encodeCondition), view: 'search' }
  });
}

function applyScope(next: TraceAttributeScope): void {
  router.push({ query: { ...route.query, scope: next } });
}

function applyValueSort(field: TraceAttributeValueSortField): void {
  valueSort.value = field;
  loadKey();
}

function applySelection(next: DifferenceSelection): void {
  router.push({
    query: {
      ...route.query,
      view: 'differences',
      minDurationNanos: String(next.minDurationNanos),
      errorsOnly: String(next.errorsOnly)
    }
  });
}

/** Turns a row of one of the breakdowns into the search that produced it. */
function filterByValue(row: TraceAttributeValueRow): void {
  const key = selectedKey.value;
  if (key === null) {
    return;
  }
  applyConditions([...conditions.value, { ...key, operator: 'EQ', value: row.value }]);
}

function filterByDifference(row: TraceAttributeDifferenceRow): void {
  applyConditions([
    ...conditions.value,
    { source: row.source, owner: row.owner, key: row.key, operator: 'EQ', value: row.value }
  ]);
}

function openTrace(trace: TraceRow): void {
  router.push(`/profiles/${profileId}/technologies/traces?trace=${trace.traceId}`);
}

async function loadCatalog(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const [catalog, overview] = await Promise.all([
      client.getAttributeKeys(),
      client.getOverview()
    ]);
    keys.value = catalog;
    totalTraces.value = overview.totalTraces;
    // The profile as the search's own stats would describe it, so a matched percentile can be read
    // against what the profile manages as a whole rather than in isolation.
    baseline.value = {
      traces: overview.totalTraces,
      tracesWithErrors: overview.errorTraces,
      totalNanos: overview.totalNanos,
      p50Nanos: overview.avgNanos,
      p95Nanos: overview.p95Nanos,
      maxNanos: overview.maxNanos
    };
  } catch (e: unknown) {
    console.error('Failed to load the attribute catalog:', e);
    error.value = 'Failed to load attributes. Please try again.';
  } finally {
    loading.value = false;
  }
}

async function runSearch(append = false): Promise<void> {
  searchLoading.value = true;
  searchError.value = null;
  try {
    const offset = append && search.value ? search.value.matches.length : 0;
    const [result, buckets] = await Promise.all([
      client.searchByAttributes({
        conditions: conditions.value,
        scope: scope.value,
        limit: PAGE_SIZE,
        offset
      }),
      // Only with the first page: the strip covers the whole match, so paging does not change it.
      append
        ? Promise.resolve(timeline.value)
        : client.getAttributeTimeline(conditions.value, scope.value)
    ]);

    search.value =
      append && search.value
        ? { ...result, matches: [...search.value.matches, ...result.matches] }
        : result;
    timeline.value = buckets;
  } catch (e: unknown) {
    console.error('Failed to search traces by attributes:', e);
    searchError.value = 'Failed to search traces. Please try again.';
  } finally {
    searchLoading.value = false;
  }
}

function loadMore(): void {
  runSearch(true);
}

async function loadKey(): Promise<void> {
  const key = selectedKey.value;
  if (key === null) {
    return;
  }

  keyLoading.value = true;
  keyError.value = null;
  try {
    // Both at once: the two tabs are two readings of one key, and fetching the other when it is
    // opened makes switching between them feel like a page load rather than a tab.
    const [loadedValues, loadedLatency] = await Promise.all([
      client.getAttributeValues(key, valueSort.value),
      client.getAttributeLatency(key)
    ]);
    values.value = loadedValues;
    latency.value = loadedLatency;
  } catch (e: unknown) {
    console.error('Failed to break down the attribute key:', e);
    keyError.value = 'Failed to load the values of this key. Please try again.';
  } finally {
    keyLoading.value = false;
  }
}

async function loadDifferences(): Promise<void> {
  differencesLoading.value = true;
  differencesError.value = null;
  try {
    differences.value = await client.getAttributeDifferences(selection.value);
  } catch (e: unknown) {
    console.error('Failed to rank attribute differences:', e);
    differencesError.value = 'Failed to compare these traces. Please try again.';
  } finally {
    differencesLoading.value = false;
  }
}

/** What the current URL needs loaded. Every navigation goes through here, including Back. */
function loadForRoute(): void {
  if (featureDisabled.value || loading.value) {
    return;
  }
  if (mode.value === 'differences') {
    loadDifferences();
    return;
  }
  if (mode.value === 'values' || mode.value === 'latency') {
    loadKey();
    return;
  }
  runSearch();
}

watch(
  () => [
    mode.value,
    route.query.where,
    route.query.scope,
    route.query.key,
    route.query.owner,
    route.query.minDurationNanos,
    route.query.errorsOnly
  ],
  () => loadForRoute()
);

onMounted(async () => {
  if (featureDisabled.value) {
    loading.value = false;
    return;
  }
  await loadCatalog();
  loadForRoute();
});
</script>

<style scoped>
.attributes-layout {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-3);
}

.attributes-workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

@media (max-width: 992px) {
  .attributes-layout {
    flex-direction: column;
  }

  .attributes-workspace {
    width: 100%;
  }
}
</style>
