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
    <LoadingState v-if="loading" message="Loading operation details..." />

    <template v-else>
      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <div v-show="activeTab === 'flames'">
        <TraceOperationFlamegraphs :profile-id="profileId" :name="name" />
      </div>

      <div v-show="activeTab === 'timeline'">
        <TimeSeriesChart
          :primary-data="primaryData"
          primary-title="Trace Duration"
          :secondary-data="secondaryData"
          secondary-title="Traces"
          :visible-minutes="60"
          :independentSecondaryAxis="true"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.NUMBER"
        />
      </div>

      <div v-show="activeTab === 'slowest'">
        <TraceSlowestList
          :traces="slowest"
          :total="traces.length"
          :note="capNote"
          @row-click="openTrace"
        />

        <TraceSpansModal
          v-model:show="spansShow"
          :profile-id="profileId"
          :trace-id="selectedTrace?.traceId ?? ''"
          :root-name="selectedTrace?.rootName ?? ''"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import LoadingState from '@shared/components/LoadingState.vue';
import TabBar from '@shared/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TraceSlowestList from '@/components/trace/TraceSlowestList.vue';
import TraceSpansModal from '@/components/trace/TraceSpansModal.vue';
import TraceOperationFlamegraphs from '@/components/trace/TraceOperationFlamegraphs.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import { timelineBuckets } from '@/services/trace/traceTimelineBuckets';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';

const TIMELINE_BUCKETS = 40;
/** Matches the backend's default; a type with more traces than this is summarised, not listed. */
const TRACE_LIMIT = 1000;

const props = defineProps<{
  profileId: string;
  name: string;
}>();

const loading = ref(true);
const traces = ref<TraceRow[]>([]);
const activeTab = ref('flames');

// The waterfall is opened here rather than by navigating to Slowest Traces: that page resolves a
// trace from its own capped list, which need not contain this operation's traces.
const spansShow = ref(false);
const selectedTrace = ref<TraceRow | null>(null);

function openTrace(trace: TraceRow): void {
  selectedTrace.value = trace;
  spansShow.value = true;
}

const tabs: TabBarItem[] = [
  { id: 'flames', label: 'Flamegraphs', icon: 'fire' },
  { id: 'timeline', label: 'Metrics Timeline', icon: 'graph-up' },
  { id: 'slowest', label: 'Slowest Traces', icon: 'hourglass-split' }
];

const buckets = computed(() =>
  timelineBuckets(
    traces.value,
    (trace) => trace.startEpochMillis,
    (trace) => trace.durationNanos,
    TIMELINE_BUCKETS
  )
);

const primaryData = computed<number[][]>(() => buckets.value.map((b) => [b.mid, b.maxDuration]));
const secondaryData = computed<number[][]>(() => buckets.value.map((b) => [b.mid, b.count]));

/** The list ranks by duration; the fetch is chronological so the timeline stays unbiased. */
const slowest = computed<TraceRow[]>(() =>
  [...traces.value].sort((a, b) => b.durationNanos - a.durationNanos)
);

// Silence about a cap reads as "this is all of them", which it would not be.
const capNote = computed<string | undefined>(() => {
  if (traces.value.length < TRACE_LIMIT) {
    return undefined;
  }
  return `First ${TRACE_LIMIT} traces of this operation`;
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    traces.value = await new ProfileTracesClient(props.profileId).getOperationTraces(
      props.name,
      TRACE_LIMIT
    );
  } catch (e: unknown) {
    console.error('Failed to load traces for operation:', e);
    traces.value = [];
  } finally {
    loading.value = false;
  }
}

watch(() => props.name, load);

onMounted(load);
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
