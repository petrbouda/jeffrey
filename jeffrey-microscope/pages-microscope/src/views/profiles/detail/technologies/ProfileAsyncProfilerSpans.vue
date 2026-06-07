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
    <LoadingState v-if="loading" message="Loading spans..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="stats.length === 0"
      title="No Spans"
      message="No async-profiler spans were recorded in this profile."
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
import { useRoute } from 'vue-router';

import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import DetailBreadcrumb from '@/components/DetailBreadcrumb.vue';
import SpanOverviewStats from '@/components/span/SpanOverviewStats.vue';
import SpanTagStats from '@/components/span/SpanTagStats.vue';
import SpanTagList from '@/components/span/SpanTagList.vue';
import SpanTagDetail from '@/components/span/SpanTagDetail.vue';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import type { SpanOverview, SpanTagStat } from '@/services/api/model/span/SpanModels';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const overview = ref<SpanOverview | null>(null);
const stats = ref<SpanTagStat[]>([]);
const selectedTag = ref<string | null>(null);

const selectedStat = computed<SpanTagStat | null>(
  () => stats.value.find(s => s.tag === selectedTag.value) ?? null
);

function openDetail(tag: string) {
  selectedTag.value = tag;
}

function clearSelection() {
  selectedTag.value = null;
}

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileAsyncProfilerClient(profileId);
    const [overviewData, tagStats] = await Promise.all([client.getOverview(), client.getTagStats()]);
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

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
