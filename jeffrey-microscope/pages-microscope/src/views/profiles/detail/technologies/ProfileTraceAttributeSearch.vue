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
  Search Traces: find traces by what their spans recorded.

  The only one of the three attribute pages with no key rail beside it. Its subject is traces rather
  than a key, it is the one that works with nothing selected, and the condition builder already
  carries the whole catalog — a rail here would open a breakdown on another page, which is what the
  sidebar is for.
-->
<template>
  <div>
    <TracesDisabledFeatureAlert v-if="featureDisabled" />

    <LoadingState v-else-if="loading" message="Loading attributes..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadCatalog" />

    <EmptyState
      v-else-if="keys.length === 0"
      icon="bi-tags"
      title="No Attributes"
      description="No span in this profile recorded an attribute, a declared field or a shape worth querying."
    />

    <div v-else class="dashboard-container search-layout">
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TraceAttributeResults from '@/components/trace/TraceAttributeResults.vue';
import TraceAttributeSearchBar from '@/components/trace/TraceAttributeSearchBar.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import FeatureType from '@/services/api/model/FeatureType';
import {
  decodeCondition,
  encodeCondition,
  type TraceAttributeConditionModel,
  type TraceAttributeKeyRow,
  type TraceAttributeScope,
  type TraceAttributeSearchResult,
  type TraceAttributeStats,
  type TraceAttributeTimelineBucket
} from '@/services/api/model/trace/TraceAttributeModels';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';

/** A page of matches; the load-more button asks for another. */
const PAGE_SIZE = 50;

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;
const client = new ProfileTracesClient(profileId);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

const loading = ref(true);
const error = ref<string | null>(null);
const keys = ref<TraceAttributeKeyRow[]>([]);
const baseline = ref<TraceAttributeStats | null>(null);

const searchLoading = ref(false);
const searchError = ref<string | null>(null);
const search = ref<TraceAttributeSearchResult | null>(null);
const timeline = ref<TraceAttributeTimelineBucket[]>([]);

/*
 * The conditions and the scope live in the URL, so a filter is a link — "the failed traces of this
 * tenant" is something to paste into an issue rather than a set of instructions — and the browser's
 * Back button steps through the investigation instead of off the page.
 */
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

function applyConditions(next: TraceAttributeConditionModel[]): void {
  router.push({ query: { ...route.query, where: next.map(encodeCondition) } });
}

function applyScope(next: TraceAttributeScope): void {
  router.push({ query: { ...route.query, scope: next } });
}

function openTrace(trace: TraceRow): void {
  router.push(`/profiles/${profileId}/technologies/traces?trace=${trace.traceId}`);
}

/**
 * Every key, high-cardinality ones included: the builder is where a search-only key is used, and it
 * marks them rather than hiding them. Only the rail on the breakdown pages leaves them out.
 */
async function loadCatalog(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const [catalog, overview] = await Promise.all([
      client.getAttributeKeys(),
      client.getOverview()
    ]);
    keys.value = catalog;
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

watch(
  () => [route.query.where, route.query.scope],
  () => {
    if (featureDisabled.value || loading.value) {
      return;
    }
    runSearch();
  }
);

onMounted(async () => {
  if (featureDisabled.value) {
    loading.value = false;
    return;
  }
  await loadCatalog();
  runSearch();
});
</script>

<style scoped>
.search-layout {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}
</style>
