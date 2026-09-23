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
    <LoadingState v-if="loading" message="Loading slowest spans..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="slowestSpans.length === 0"
      title="No Spans"
      description="No async-profiler spans were recorded in this profile."
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
import type {
  SlowestSpanRow,
  SpanOverview,
  SpanSlowestRow
} from '@/services/api/model/span/SpanModels';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const overview = ref<SpanOverview | null>(null);
const slowestSpans = ref<SpanSlowestRow[]>([]);

const eventsShow = ref(false);
const selected = ref<SlowestSpanRow | null>(null);

function openEvents(span: SlowestSpanRow) {
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
