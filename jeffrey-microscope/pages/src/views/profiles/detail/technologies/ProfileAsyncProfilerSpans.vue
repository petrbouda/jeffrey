<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<template>
  <div>
    <LoadingState v-if="loading" message="Loading spans..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="stats.length === 0"
      title="No Spans"
      description="No async-profiler spans were recorded in this profile."
      icon="bi-bounding-box"
    />

    <div v-else class="dashboard-container">
      <!-- Tag list (no tag selected) -->
      <template v-if="selectedTag === null">
        <SpanOverviewStats v-if="overview" :overview="overview" />
        <SpanTagList :tags="stats" @tag-click="openDetail" />
      </template>

      <!-- Tag detail (a tag is selected) -->
      <template v-else>
        <DetailBreadcrumb root-label="Tags" icon="bi-tag-fill" @back="clearSelection">
          {{ selectedTag }}
        </DetailBreadcrumb>

        <SpanTagStats v-if="selectedStat" :stat="selectedStat" />

        <SpanTagDetail :profile-id="profileId" :tag="selectedTag" />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DetailBreadcrumb from '@shared/components/DetailBreadcrumb.vue';
import SpanOverviewStats from '@/components/span/SpanOverviewStats.vue';
import SpanTagStats from '@/components/span/SpanTagStats.vue';
import SpanTagList from '@/components/span/SpanTagList.vue';
import SpanTagDetail from '@/components/span/SpanTagDetail.vue';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import type { SpanOverview, SpanTagStat } from '@/services/api/model/span/SpanModels';

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const overview = ref<SpanOverview | null>(null);
const stats = ref<SpanTagStat[]>([]);

/**
 * The selection lives in the URL, so a tag's detail can be linked to and Back steps out of it rather
 * than off the page — the same treatment the trace operations get, which this view is the sibling of.
 */
const selectedTag = computed<string | null>(() => (route.query.tag as string | undefined) ?? null);

const selectedStat = computed<SpanTagStat | null>(
  () => stats.value.find(s => s.tag === selectedTag.value) ?? null
);

function openDetail(tag: string) {
  router.push({ query: { ...route.query, tag } });
}

function clearSelection() {
  const query = { ...route.query };
  delete query.tag;
  router.push({ query });
}

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileAsyncProfilerClient(profileId);
    const [overviewData, tagStats] = await Promise.all([
      client.getOverview(),
      client.getTagStats()
    ]);
    overview.value = overviewData;
    stats.value = tagStats;
  } catch (e: unknown) {
    console.error('Failed to load span data:', e);
    error.value = 'Failed to load span statistics. Please try again.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>
