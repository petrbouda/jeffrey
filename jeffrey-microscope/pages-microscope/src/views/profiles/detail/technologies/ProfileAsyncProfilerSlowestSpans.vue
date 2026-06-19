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
    <LoadingState v-if="loading" message="Loading slowest spans..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="slowestSpans.length === 0"
      title="No Spans"
      message="No async-profiler spans were recorded in this profile."
      icon="bi-bounding-box"
    />

    <div v-else class="dashboard-container">
      <!-- Overview stats header -->
      <SpanOverviewStats v-if="overview" :overview="overview" />

      <!-- Slowest spans across all tags -->
      <SpanSlowestList :spans="slowestSpans" @row-click="openEvents" />

      <SpanEventsModal
        v-model:show="eventsShow"
        :profile-id="profileId"
        :thread-hash="selected?.threadHash ?? ''"
        :start-epoch-millis="selected?.startEpochMillis ?? 0"
        :duration-nanos="selected?.durationNanos ?? 0"
        :thread-name="selected?.threadName ?? ''"
        :is-virtual="selected?.isVirtual ?? false"
        :tag="selected?.tag"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import SpanOverviewStats from '@/components/span/SpanOverviewStats.vue';
import SpanSlowestList from '@/components/span/SpanSlowestList.vue';
import SpanEventsModal from '@/components/span/SpanEventsModal.vue';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import type { SpanOverview, SpanSlowestRow } from '@/services/api/model/span/SpanModels';

// Matches the row payload emitted by SpanSlowestList (tag optional).
interface ClickedSpan {
  startEpochMillis: number;
  durationNanos: number;
  threadHash: string;
  threadName: string;
  isVirtual: boolean;
  tag?: string;
}

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const overview = ref<SpanOverview | null>(null);
const slowestSpans = ref<SpanSlowestRow[]>([]);

const eventsShow = ref(false);
const selected = ref<ClickedSpan | null>(null);

function openEvents(span: ClickedSpan) {
  selected.value = span;
  eventsShow.value = true;
}

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileAsyncProfilerClient(profileId);
    const [overviewData, slowest] = await Promise.all([
      client.getOverview(),
      client.getSlowestSpans()
    ]);
    overview.value = overviewData;
    slowestSpans.value = slowest;
  } catch (e: unknown) {
    console.error('Failed to load slowest spans:', e);
    error.value = 'Failed to load slowest spans. Please try again.';
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
