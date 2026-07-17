<template>
  <LoadingState v-if="!loaded" message="Loading flamegraph data..." />

  <div v-else class="flamegraphs-primary-container">
    <PageHeader
      title="Primary Flamegraphs"
      description="View and analyze performance data Flamegraphs"
      icon="bi-fire"
    />

    <FlamegraphCardGrid
      :graph-mode="GraphType.PRIMARY"
      :execution-sample-events="executionSampleEvents"
      :cpu-time-sample-events="cpuTimeSampleEvents"
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      :blocking-events="blockingEvents"
      :native-allocation-events="nativeAllocationEvents"
      :native-leak-events="nativeLeakEvents"
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
  wallClockEvents,
  blockingEvents,
  nativeAllocationEvents,
  nativeLeakEvents
} = useFlamegraphEvents(GraphType.PRIMARY);
</script>

<style scoped>
.flamegraphs-primary-container .card {
  border: none;
  overflow: hidden;
}
</style>
