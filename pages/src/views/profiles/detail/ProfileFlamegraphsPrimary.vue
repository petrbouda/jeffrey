<template>
  <div v-if="!loaded" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading flamegraph data...</p>
  </div>

  <div v-else class="flamegraphs-primary-container">
    <h4 class="mb-4">Primary Flamegraphs</h4>

    <div class="card-grid">
      <SectionCard v-if="loaded" v-for="(event, index) in executionSampleEvents" :key="index"
                   router-forward="flamegraph"
                   button-title="Show Flamegraph"
                   title="Execution Samples"
                   color="blue"
                   icon="sprint"
                   :thread-mode-opt="true"
                   :thread-mode-selected="false"
                   weight-desc="Total Time on CPU"
                   :weight-opt="false"
                   :weight-selected="false"
                   :weight-formatter="FormattingService.formatDuration2Units"
                   :exclude-non-java-samples-opt="false"
                   :exclude-non-java-samples-selected="false"
                   :exclude-idle-samples-opt="false"
                   :exclude-idle-samples-selected="false"
                   :only-unsafe-allocation-samples-opt="false"
                   :only-unsafe-allocation-samples-selected="false"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-if="loaded" v-for="(event, index) in wallClockEvents" :key="index"
                   router-forward="flamegraph"
                   button-title="Show Flamegraph"
                   title="Wall-Clock Samples"
                   color="purple"
                   icon="alarm"
                   :thread-mode-opt="true"
                   :thread-mode-selected="true"
                   weight-desc="Total Time"
                   :weight-opt="false"
                   :weight-selected="false"
                   :weight-formatter="FormattingService.formatDuration2Units"
                   :exclude-non-java-samples-opt="true"
                   :exclude-non-java-samples-selected="true"
                   :exclude-idle-samples-opt="true"
                   :exclude-idle-samples-selected="true"
                   :only-unsafe-allocation-samples-opt="false"
                   :only-unsafe-allocation-samples-selected="false"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-if="loaded" v-for="(event, index) in objectAllocationEvents" :key="index"
                   router-forward="flamegraph"
                   button-title="Show Flamegraph"
                   title="Allocation Samples"
                   color="green"
                   icon="memory"
                   :thread-mode-opt="true"
                   :thread-mode-selected="false"
                   weight-desc="Total Allocation"
                   :weight-opt="true"
                   :weight-selected="true"
                   :weight-formatter="FormattingService.formatBytes"
                   :exclude-non-java-samples-opt="false"
                   :exclude-non-java-samples-selected="false"
                   :exclude-idle-samples-opt="false"
                   :exclude-idle-samples-selected="false"
                   :only-unsafe-allocation-samples-opt="false"
                   :only-unsafe-allocation-samples-selected="false"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-if="loaded" v-for="(event, index) in nativeAllocationEvents" :key="index"
                   router-forward="flamegraph"
                   button-title="Show Flamegraph"
                   title="Native Allocation Samples"
                   color="pink"
                   icon="memory"
                   :thread-mode-opt="true"
                   :thread-mode-selected="false"
                   weight-desc="Total Allocation"
                   :weight-opt="true"
                   :weight-selected="true"
                   :weight-formatter="FormattingService.formatBytes"
                   :exclude-non-java-samples-opt="false"
                   :exclude-non-java-samples-selected="false"
                   :exclude-idle-samples-opt="false"
                   :exclude-idle-samples-selected="false"
                   :only-unsafe-allocation-samples-opt="true"
                   :only-unsafe-allocation-samples-selected="true"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-if="loaded" v-for="(event, index) in nativeLeakEvents" :key="index"
                   router-forward="flamegraph"
                   button-title="Show Flamegraph"
                   title="Native Allocation Leaks"
                   color="pink"
                   icon="memory"
                   :thread-mode-opt="true"
                   :thread-mode-selected="false"
                   weight-desc="Total Allocation"
                   :weight-opt="true"
                   :weight-selected="true"
                   :weight-formatter="FormattingService.formatBytes"
                   :exclude-non-java-samples-opt="false"
                   :exclude-non-java-samples-selected="false"
                   :exclude-idle-samples-opt="false"
                   :exclude-idle-samples-selected="false"
                   :only-unsafe-allocation-samples-opt="true"
                   :only-unsafe-allocation-samples-selected="true"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-if="loaded" v-for="(event, index) in blockingEvents" :key="index"
                   router-forward="flamegraph"
                   button-title="Show Flamegraph"
                   :title="event.label"
                   color="red"
                   icon="lock"
                   :thread-mode-opt="true"
                   :thread-mode-selected="false"
                   :weight-opt="true"
                   :weight-selected="true"
                   weight-desc="Blocked Time"
                   :weight-formatter="FormattingService.formatDuration2Units"
                   :exclude-non-java-samples-opt="false"
                   :exclude-non-java-samples-selected="false"
                   :exclude-idle-samples-opt="false"
                   :exclude-idle-samples-selected="false"
                   :only-unsafe-allocation-samples-opt="false"
                   :only-unsafe-allocation-samples-selected="false"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onBeforeMount, ref} from "vue";
import FormattingService from "@/services/FormattingService";
import SectionCard from "@/components/SectionCard.vue";
import GraphType from "@/services/flamegraphs/GraphType";
import {useRoute} from "vue-router";
import EventSummary from "@/services/flamegraphs/model/EventSummary";
import EventSummariesClient from "@/services/EventSummariesClient";
import EventTypes from "@/services/EventTypes.ts";

const objectAllocationEvents: EventSummary[] = []
const executionSampleEvents: EventSummary[] = []
const blockingEvents: EventSummary[] = []
const wallClockEvents: EventSummary[] = []
const nativeAllocationEvents: EventSummary[] = []
const nativeLeakEvents: EventSummary[] = []

const loaded = ref<boolean>(false)

const route = useRoute()

onBeforeMount(() => {
  EventSummariesClient.primary(route.params.projectId as string, route.params.profileId as string)
      .then((data) => {
        categorizeEventTypes(data)
        loaded.value = true
      })
});

function categorizeEventTypes(eventTypes: EventSummary[]) {
  for (const event of eventTypes) {
    if (EventTypes.isExecutionEventType(event.code)) {
      executionSampleEvents.push(event)
    } else if (EventTypes.isAllocationEventType(event.code)) {
      objectAllocationEvents.push(event)
    } else if (EventTypes.isBlockingEventType(event.code)) {
      blockingEvents.push(event)
    } else if (EventTypes.isWallClock(event.code)) {
      wallClockEvents.push(event)
    } else if (EventTypes.isMallocAllocationEventType(event.code)) {
      nativeAllocationEvents.push(event)
    } else if (EventTypes.isNativeLeakEventType(event.code)) {
      nativeLeakEvents.push(event)
    }
  }
}
</script>

<style scoped>
.flamegraphs-primary-container .card {
  border: none;
  overflow: hidden;
}

/* Card grid for equal height cards */
.card-grid {
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 1.5rem;
}

@media (min-width: 768px) {
  .card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 992px) {
  .card-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
