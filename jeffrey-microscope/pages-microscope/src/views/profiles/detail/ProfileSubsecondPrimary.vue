<template>
  <LoadingState v-if="!loaded" message="Loading SubSecond data..." />

  <div v-else>
    <PageHeader
      title="Primary SubSecond Graphs"
      description="View and analyze time-based performance data at sub-second intervals"
      icon="bi-bar-chart"
    />

    <FlamegraphCardGrid
      :graph-mode="GraphType.PRIMARY"
      :execution-sample-events="executionSampleEvents"
      :cpu-time-sample-events="cpuTimeSampleEvents"
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      route-name="subsecond"
      button-text="Show SubSecond Graph"
      :suppress-empty-placeholders="hasCategorizedEvents"
    />
  </div>
</template>

<script setup lang="ts">
import GraphType from '@/services/flamegraphs/GraphType';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import { useFlamegraphEvents } from '@/composables/useFlamegraphEvents';

// The generic /events endpoint is format-aware: formats with server-resolved categories (e.g.
// pprof) arrive pre-categorized, and the grid suppresses the greyed placeholder cards for
// categories such formats never produce.
const {
  loaded,
  hasCategorizedEvents,
  executionSampleEvents,
  cpuTimeSampleEvents,
  methodTraceEvents,
  objectAllocationEvents,
  wallClockEvents
} = useFlamegraphEvents(GraphType.PRIMARY);
</script>
