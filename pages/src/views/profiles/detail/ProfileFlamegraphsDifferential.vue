<template>
  <div v-if="!loaded" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading flamegraph data...</p>
  </div>

  <div v-else class="flamegraphs-differential-container">
    <DashboardHeader 
      title="Differential Flamegraphs"
      description="Compare performance data between primary and secondary profiles using Flamegraphs"
      icon="file-diff"
    />

    <div class="flamegraph-grid">
      <FlamegraphCard v-if="loaded" v-for="(event, index) in executionSampleEvents" :key="index"
                      title="Execution Samples"
                      color="blue"
                      icon="sprint"
                      :thread-mode-opt="false"
                      :thread-mode-selected="false"
                      :weight-desc="null"
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
                      :enabled="loaded"/>

      <FlamegraphCard v-if="loaded" v-for="(event, index) in methodTracesEvents" :key="index"
                      title="Method Traces"
                      color="blue"
                      icon="sprint"
                      :thread-mode-opt="false"
                      :thread-mode-selected="false"
                      weight-desc="Total Time"
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
                      :enabled="loaded"/>

      <FlamegraphCard v-if="loaded" v-for="(event, index) in wallClockEvents" :key="index"
                      title="Wall-Clock Samples"
                      color="purple"
                      icon="alarm"
                      :thread-mode-opt="false"
                      :thread-mode-selected="true"
                      :weight-desc="null"
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
                      :enabled="loaded"/>

      <FlamegraphCard v-if="loaded" v-for="(event, index) in objectAllocationEvents" :key="index"
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
                      :enabled="loaded"/>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onBeforeMount, ref} from "vue";
import FormattingService from "@/services/FormattingService";
import FlamegraphCard from "@/components/FlamegraphCard.vue";
import GraphType from "@/services/flamegraphs/GraphType";
import {useRoute} from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import EventSummary from "@/services/flamegraphs/model/EventSummary";
import EventSummariesClient from "@/services/flamegraphs/client/EventSummariesClient";
import EventTypes from "@/services/EventTypes.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService";
import DashboardHeader from '@/components/DashboardHeader.vue';

const objectAllocationEvents = ref<EventSummary[]>([])
const executionSampleEvents = ref<EventSummary[]>([])
const methodTracesEvents = ref<EventSummary[]>([])
const wallClockEvents = ref<EventSummary[]>([])

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const loaded = ref(false)

const profileSelector = ref(false)

onBeforeMount(() => {
  if (SecondaryProfileService.id() != null) {
    EventSummariesClient.differential(
        workspaceId.value!, projectId.value!, route.params.profileId as string, SecondaryProfileService.id() as string)
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
    } else if (EventTypes.isMethodTraceEventType(event.code)) {
      methodTracesEvents.value.push(event)
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

/* Modern responsive grid for flamegraph cards */
.flamegraph-grid {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  max-width: 1400px;
}

@media (min-width: 768px) {
  .flamegraph-grid {
    gap: 1.5rem;
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .flamegraph-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 2rem;
  }
}

@media (min-width: 1440px) {
  .flamegraph-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 2rem;
  }
}
</style>
