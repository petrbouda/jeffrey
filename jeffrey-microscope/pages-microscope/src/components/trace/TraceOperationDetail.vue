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
    <ErrorState v-else-if="error" :message="error" @retry="load" />

    <template v-else>
      <div class="op-actions">
        <AiExportButton
          :build-source="buildAiExportSource"
          tooltip="Export this operation for AI analysis"
        />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <div v-show="activeTab === 'summary'">
        <TraceOperationSummary
          :profile-id="profileId"
          :operation="operation"
          :totals="totals"
          :traces="traces"
          :truncated="truncated"
          :overview="overview"
          @open-trace="openTrace"
          @show-all-traces="activeTab = 'slowest'"
        />
      </div>

      <div v-show="activeTab === 'flames'">
        <TraceOperationFlamegraphs
          v-if="flamesOpened && traces.length > 0"
          :profile-id="profileId"
          :operation="operation"
          :virtual-thread-only="!samplesAreReachable"
        />
      </div>

      <div v-show="activeTab === 'timeline'">
        <TimeSeriesChart
          :primary-data="primaryData"
          primary-title="Trace Duration"
          :secondary-data="secondaryData"
          secondary-title="Traces"
          time-unit="milliseconds"
          :visible-minutes="60"
          :independent-secondary-axis="true"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.NUMBER"
        />
      </div>

      <div v-show="activeTab === 'slowest'">
        <!--
          server-ordered is load-bearing: the rows arrive slowest-first from the server, and without
          it the shared list re-sorts and silently slices to its own default of 50 — a tab named
          "Slowest Traces" quietly showing 5% of what was fetched. The denominator is the
          operation's real call count, not the capped sample, so "Showing X / Total Y" tells the
          truth for operations past the cap.
        -->
        <TraceSlowestList
          :traces="traces"
          :total="totals?.count ?? traces.length"
          :note="capNote"
          server-ordered
          @row-click="openTrace"
        />
      </div>

      <!--
        A sibling of the tab panels, not a child of one: the Summary tab opens the waterfall too, and
        GenericModal renders in place rather than teleporting. Nested inside a hidden `v-show` it
        would mount invisibly, lock body scroll, and put its own dismiss handlers out of reach.
      -->
      <TraceSpansModal
        v-model:show="spansShow"
        :profile-id="profileId"
        :trace-id="selectedTrace?.traceId ?? ''"
        :root-name="selectedTrace?.rootName ?? ''"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import TabBar from '@shared/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TraceSlowestList from '@/components/trace/TraceSlowestList.vue';
import TraceOperationSummary from '@/components/trace/TraceOperationSummary.vue';
import TraceSpansModal from '@/components/trace/TraceSpansModal.vue';
import TraceOperationFlamegraphs from '@/components/trace/TraceOperationFlamegraphs.vue';
import AiExportButton from '@/components/ai-analysis/AiExportButton.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import TraceAiExportClient from '@/services/api/TraceAiExportClient';
import type { AiExportSource } from '@/composables/useAiExport';
import { profileStore } from '@/stores/profileStore';
import { timelineBuckets } from '@/services/trace/traceTimelineBuckets';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import type {
  TraceOperationId,
  TraceOperationRow,
  TraceOverview,
  TraceRow
} from '@/services/api/model/trace/TraceModels';

const TIMELINE_BUCKETS = 40;
/** Matches the backend's default; a type with more traces than this is summarised, not listed. */
const TRACE_LIMIT = 1000;

const props = defineProps<{
  profileId: string;
  operation: TraceOperationId;
  /**
   * The operation's row from the list, whose aggregates are computed over *every* trace of the type.
   * The trace list below is capped, so the summary reads its headline numbers from here rather than
   * folding a truncated sample — those two answers disagreed for any operation past the cap.
   *
   * Null for a deep link into an operation the capped list did not contain.
   */
  totals: TraceOperationRow | null;
  /** Profile-wide totals, already fetched by the page above — not requested a second time here. */
  overview: TraceOverview | null;
}>();

/**
 * Always available: the operation's identity comes from the route, not from a loaded response, so
 * the export works even on a deep link whose capped list never contained this operation.
 */
function buildAiExportSource(): AiExportSource | null {
  const client = new TraceAiExportClient(props.profileId);
  const operation = props.operation;
  return {
    fetch: () => client.generateOperation(operation.name, operation.kind, operation.eventType),
    label: 'Operation',
    filenameStem: `operation-${operation.name}`
  };
}

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const error = ref<string | null>(null);
const traces = ref<TraceRow[]>([]);
const truncated = ref(false);

// Null before the profile loads, and for a recording that never reported its bounds; the buckets
// then fall back to the range the traces themselves cover.
const recordingSpan = computed(() => {
  const window = profileStore.recordingWindow.value;
  return window === null ? undefined : { from: 0, to: window.durationMillis };
});
const activeTab = ref('summary');

/*
 * The Flamegraphs tab fetches its panels on mount, and it is the only tab that fetches anything the
 * drill-down does not already have. Mounted on first visit rather than up front, and left mounted
 * afterwards so returning to it does not re-query.
 */
const flamesOpened = ref(false);
watch(activeTab, tab => {
  if (tab === 'flames') {
    flamesOpened.value = true;
  }
});

// The waterfall is opened here rather than by navigating to Slowest Traces: that page resolves a
// trace from its own capped list, which need not contain this operation's traces.
const spansShow = ref(false);
const selectedTrace = ref<TraceRow | null>(null);

function openTrace(trace: TraceRow): void {
  selectedTrace.value = trace;
  spansShow.value = true;
  // The id joins the operation already in the URL, so a waterfall reached through an operation is
  // as linkable as one reached from the trace list -- it is the same modal either way, and it used
  // to be shareable from only one of the two places it opens.
  router.replace({ query: { ...route.query, trace: trace.traceId } });
}

// Closing takes the trace back out again, so returning to the link does not reopen it.
watch(spansShow, open => {
  if (!open && route.query.trace) {
    const query = { ...route.query };
    delete query.trace;
    router.replace({ query });
  }
});

/**
 * Reopens the trace named in the URL once this operation's traces are in. A link can point at a
 * trace beyond the fetched page, in which case no row is found here — the modal fetches everything
 * it draws from the id alone, so it still opens, just without the row's own header values.
 */
watch([() => route.query.trace, traces], ([traceId]) => {
  if (!traceId) {
    spansShow.value = false;
    return;
  }
  const id = traceId as string;
  selectedTrace.value = traces.value.find(trace => trace.traceId === id) ?? null;
  spansShow.value = true;
});

/*
 * Whether a sample can be attributed to this operation at all. A span is matched to samples by
 * (thread, window) and the profiler attributes them to the carrier, so an operation that never left
 * its virtual threads has nothing to draw — the tab stays and explains that, rather than vanishing.
 */
const samplesAreReachable = computed(() => traces.value.some(trace => trace.hasPlatformSpan));

const tabs: TabBarItem[] = [
  { id: 'summary', label: 'Summary', icon: 'grid-1x2' },
  { id: 'flames', label: 'Flamegraphs', icon: 'fire' },
  { id: 'timeline', label: 'Metrics Timeline', icon: 'graph-up' },
  { id: 'slowest', label: 'Slowest Traces', icon: 'hourglass-split' }
];

/*
 * Plotted against the recording's own clock, like every other timeline in the profile: the x values
 * are milliseconds from the recording's start, and the chart spans the whole recording rather than
 * the minute this operation happens to occupy. A burst then reads as a burst, in the place it
 * happened, instead of being stretched to fill the axis.
 */
const buckets = computed(() =>
  timelineBuckets(
    traces.value,
    trace => trace.startMillisFromBeginning,
    trace => trace.durationNanos,
    TIMELINE_BUCKETS,
    recordingSpan.value
  )
);

const primaryData = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.maxDuration]));
const secondaryData = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.count]));

// Silence about a cap reads as "this is all of them", which it would not be.
const capNote = computed<string | undefined>(() => {
  if (!truncated.value) {
    return undefined;
  }
  return `First ${TRACE_LIMIT} traces of this operation`;
});

/*
 * Guards against an out-of-order response. Each load claims a number; a response whose number is no
 * longer the current one is discarded rather than assigned, so a slow earlier request cannot land
 * on top of the answer the reader is actually looking at.
 */
let loadGeneration = 0;

async function load(): Promise<void> {
  const generation = ++loadGeneration;
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileTracesClient(props.profileId);
    // One past the cap, so a full page can be told apart from a page that merely filled it: an
    // operation with exactly TRACE_LIMIT traces must not claim it was truncated.
    const operationTraces = await client.getOperationTraces(props.operation, TRACE_LIMIT + 1);
    if (generation !== loadGeneration) {
      return;
    }
    truncated.value = operationTraces.length > TRACE_LIMIT;
    traces.value = truncated.value ? operationTraces.slice(0, TRACE_LIMIT) : operationTraces;
  } catch (e: unknown) {
    if (generation !== loadGeneration) {
      return;
    }
    console.error('Failed to load traces for operation:', e);
    error.value = 'Failed to load this operation.';
    traces.value = [];
    truncated.value = false;
  } finally {
    if (generation === loadGeneration) {
      loading.value = false;
    }
  }
}

// No watch on `props.name`: the parent keys this component on the selected operation, so a different
// operation remounts it. `useFlamegraphPanels` only fetches on mount, which is why the key is there.
onMounted(load);
</script>
