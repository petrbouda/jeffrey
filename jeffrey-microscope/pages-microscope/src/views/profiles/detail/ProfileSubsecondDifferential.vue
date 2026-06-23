<template>
  <LoadingState v-if="!loaded" message="Loading subsecond data..." />

  <div v-else>
    <PageHeader
      title="Differential SubSecond Graphs"
      description="Compare time-based performance data between primary and secondary profiles at sub-second intervals"
      icon="bi-file-bar-graph"
    />

    <FlamegraphCardGrid
      :graph-mode="GraphType.DIFFERENTIAL"
      :execution-sample-events="executionSampleEvents"
      :cpu-time-sample-events="cpuTimeSampleEvents"
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      route-name="subsecond"
      button-text="Show SubSecond Graph"
    />
  </div>
</template>

<script setup lang="ts">
import GraphType from '@/services/flamegraphs/GraphType';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import { useFlamegraphEvents } from '@/composables/useFlamegraphEvents';

const {
  loaded,
  executionSampleEvents,
  cpuTimeSampleEvents,
  methodTraceEvents,
  objectAllocationEvents,
  wallClockEvents
} = useFlamegraphEvents(GraphType.DIFFERENTIAL);
</script>
