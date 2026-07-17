<template>
  <LoadingState v-if="!loaded" message="Loading flamegraph data..." />

  <div v-else>
    <PageHeader
      title="Differential Flamegraphs"
      description="Compare performance data between primary and secondary profiles using Flamegraphs"
      icon="bi-file-diff"
    />

    <FlamegraphCardGrid
      :graph-mode="GraphType.DIFFERENTIAL"
      :execution-sample-events="executionSampleEvents"
      :cpu-time-sample-events="cpuTimeSampleEvents"
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
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
} = useFlamegraphEvents(GraphType.DIFFERENTIAL);
</script>
