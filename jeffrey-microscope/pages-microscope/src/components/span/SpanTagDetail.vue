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
          :visible-minutes="60"
          :independentSecondaryAxis="true"
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
import TabBar from '@/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import SpanSlowestList from '@/components/span/SpanSlowestList.vue';
import SpanEventsModal from '@/components/span/SpanEventsModal.vue';
import SpanTagFlamegraphs from '@/components/span/SpanTagFlamegraphs.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import type { TabBarItem } from '@/components/TabBar.vue';
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

interface Bucket {
  mid: number;
  maxDuration: number;
  count: number;
}

const buckets = computed<Bucket[]>(() => {
  const items = spans.value;
  if (items.length === 0) {
    return [];
  }
  let min = Infinity;
  let max = -Infinity;
  for (const span of items) {
    if (span.startEpochMillis < min) {
      min = span.startEpochMillis;
    }
    if (span.startEpochMillis > max) {
      max = span.startEpochMillis;
    }
  }
  const span = Math.max(1, max - min);
  const width = Math.max(1, Math.ceil(span / TIMELINE_BUCKETS));
  const result: Bucket[] = [];
  for (let i = 0; i < TIMELINE_BUCKETS; i++) {
    result.push({ mid: min + i * width + width / 2, maxDuration: 0, count: 0 });
  }
  for (const s of items) {
    const index = Math.min(TIMELINE_BUCKETS - 1, Math.floor((s.startEpochMillis - min) / width));
    const bucket = result[index];
    bucket.count++;
    if (s.durationNanos > bucket.maxDuration) {
      bucket.maxDuration = s.durationNanos;
    }
  }
  return result;
});

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

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
