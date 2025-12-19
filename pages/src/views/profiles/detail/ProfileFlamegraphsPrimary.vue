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
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      :blocking-events="blockingEvents"
      :native-allocation-events="nativeAllocationEvents"
      :native-leak-events="nativeLeakEvents"
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
