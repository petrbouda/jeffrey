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
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
    />
  </div>
</template>

<script setup lang="ts">
import GraphType from "@/services/flamegraphs/GraphType";
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import { useFlamegraphEvents } from '@/composables/useFlamegraphEvents';

const {
  loaded,
  executionSampleEvents,
  methodTraceEvents,
  objectAllocationEvents,
  wallClockEvents
} = useFlamegraphEvents(GraphType.DIFFERENTIAL);
</script>
