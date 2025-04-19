<template>
  <div v-if="!loaded" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading flamegraph data...</p>
  </div>

  <div v-else class="flamegraphs-differential-container">
    <!-- Header Section -->
    <div class="mb-4">
      <h2 class="flamegraphs-title">
        <i class="bi bi-file-diff me-2"></i>
        Differential Flamegraphs
      </h2>
      <p class="text-muted fs-6">Compare performance data between primary and secondary profiles using Flamegraphs</p>
    </div>

    <div class="card-grid">
      <SectionCard v-for="(event, index) in executionSampleEvents" :key="index"
                   button-title="Show Flamegraph"
                   router-forward="flamegraph"
                   title="Execution Samples"
                   color="blue"
                   icon="sprint"
                   :thread-mode-opt="false"
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
                   :graph-mode="GraphType.DIFFERENTIAL"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in wallClockEvents" :key="index"
                   button-title="Show Flamegraph"
                   router-forward="flamegraph"
                   title="Wall-Clock Samples"
                   color="purple"
                   icon="alarm"
                   :thread-mode-opt="false"
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
                   :graph-mode="GraphType.DIFFERENTIAL"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in objectAllocationEvents" :key="index"
                   button-title="Show Flamegraph"
                   router-forward="flamegraph"
                   title="Allocation Samples"
                   color="green"
                   icon="memory"
                   :thread-mode-opt="false"
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
                   :graph-mode="GraphType.DIFFERENTIAL"
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
import SecondaryProfileService from "@/services/SecondaryProfileService";

const objectAllocationEvents = ref<EventSummary[]>([])
const executionSampleEvents = ref<EventSummary[]>([])
const wallClockEvents = ref<EventSummary[]>([])

const route = useRoute()
const loaded = ref(false)

const profileSelector = ref(false)

onBeforeMount(() => {
  if (SecondaryProfileService.id() != null) {
    EventSummariesClient.differential(
        route.params.projectId as string, route.params.profileId as string, SecondaryProfileService.id() as string)
        .then((data) => {
          categorizeEventTypes(data)
          loaded.value = true
        })
  } else {
    profileSelector.value = true
  }
});

function categorizeEventTypes(eventTypes: EventSummary[]) {
  for (const event of eventTypes) {
    if (EventTypes.isExecutionEventType(event.code)) {
      executionSampleEvents.value.push(event)
    } else if (EventTypes.isAllocationEventType(event.code)) {
      objectAllocationEvents.value.push(event)
    } else if (EventTypes.isWallClock(event.code)) {
      wallClockEvents.value.push(event)
    }
  }
}
</script>

<style scoped>
.flamegraphs-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

.flamegraphs-differential-container {
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
