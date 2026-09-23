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
  <div class="dashboard-container">
    <LoadingState v-if="loading" message="Loading tag details..." />

    <template v-else>
      <!-- Tabbed analysis -->
      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <div v-show="activeTab === 'timeline'">
        <TimeSeriesChart
          :primary-data="primaryData"
          primary-title="Span Duration"
          :secondary-data="secondaryData"
          secondary-title="Spans"
          time-unit="milliseconds"
          :visible-minutes="60"
          :independent-secondary-axis="true"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.NUMBER"
        />
      </div>

      <div v-show="activeTab === 'slowest'">
        <SpanSlowestList :spans="spans" @row-click="openEvents" />
        <SpanEventsModal
          v-model:show="eventsShow"
          :profile-id="profileId"
          :thread-hash="selected?.threadHash ?? ''"
          :start-epoch-millis="selected?.startEpochMillis ?? 0"
          :duration-nanos="selected?.durationNanos ?? 0"
          :thread-name="selected?.threadName ?? ''"
          :is-virtual="selected?.isVirtual ?? false"
          :tag="tag"
        />
      </div>

      <div v-show="activeTab === 'flames'">
        <SpanTagFlamegraphs v-if="spans.length > 0" :profile-id="profileId" :tag="tag" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import TabBar from '@shared/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import SpanSlowestList from '@/components/span/SpanSlowestList.vue';
import SpanEventsModal from '@/components/span/SpanEventsModal.vue';
import SpanTagFlamegraphs from '@/components/span/SpanTagFlamegraphs.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import { profileStore } from '@/stores/profileStore';
import { timelineBuckets } from '@/services/trace/traceTimelineBuckets';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import type { SpanDetailRow } from '@/services/api/model/span/SpanModels';

const TIMELINE_BUCKETS = 40;

const props = defineProps<{
  profileId: string;
  tag: string;
}>();

const client = new ProfileAsyncProfilerClient(props.profileId);

const loading = ref(true);
const spans = ref<SpanDetailRow[]>([]);
const activeTab = ref('flames');

const eventsShow = ref(false);
const selected = ref<SpanDetailRow | null>(null);

function openEvents(span: SpanDetailRow) {
  selected.value = span;
  eventsShow.value = true;
}

const tabs: TabBarItem[] = [
  { id: 'flames', label: 'Flamegraphs', icon: 'fire' },
  { id: 'timeline', label: 'Metrics Timeline', icon: 'graph-up' },
  { id: 'slowest', label: 'Slowest Spans', icon: 'hourglass-split' }
];

/*
 * Same treatment as the trace operation timeline: relative to the recording's start, and spanning
 * the whole recording. A span carries only an absolute start, so it is rebased here.
 */
const recordingSpan = computed(() => {
  const window = profileStore.recordingWindow.value;
  return window === null ? undefined : { from: 0, to: window.durationMillis };
});

const recordingStart = computed(() => profileStore.recordingWindow.value?.startEpochMillis ?? 0);

const buckets = computed(() =>
  timelineBuckets(
    spans.value,
    span => span.startEpochMillis - recordingStart.value,
    span => span.durationNanos,
    TIMELINE_BUCKETS,
    recordingSpan.value
  )
);

const primaryData = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.maxDuration]));
const secondaryData = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.count]));

async function load() {
  loading.value = true;
  try {
    spans.value = await client.getTagSpans(props.tag);
  } catch (e: unknown) {
    console.error('Failed to load spans for tag:', e);
    spans.value = [];
  } finally {
    loading.value = false;
  }
}

watch(() => props.tag, load);

onMounted(load);
</script>
