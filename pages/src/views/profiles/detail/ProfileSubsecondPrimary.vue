<template>
  <div v-if="!loaded" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading SubSecond data...</p>
  </div>

  <div v-else class="flamegraphs-primary-container">
    <h4 class="mb-4">Primary SubSecond Graphs</h4>

    <div class="card-grid">
      <SectionCard v-for="(event, index) in executionSampleEvents" :key="index"
                   router-forward="subsecond"
                   button-title="Show SubSecond Graph"
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
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in wallClockEvents" :key="index"
                   router-forward="subsecond"
                   button-title="Show SubSecond Graph"
                   title="Wall-Clock Samples"
                   color="purple"
                   icon="alarm"
                   :thread-mode-opt="false"
                   :thread-mode-selected="false"
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

      <SectionCard v-for="(event, index) in objectAllocationEvents" :key="index"
                   router-forward="subsecond"
                   button-title="Show SubSecond Graph"
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
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in blockingEvents" :key="index"
                   router-forward="subsecond"
                   button-title="Show SubSecond Graph"
                   :title="stripLeadingJava(event.label)"
                   color="red"
                   icon="lock"
                   :thread-mode-opt="false"
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

function stripLeadingJava(label: string): string {
  return label.replaceAll('Java', '')
}
</script>

<style scoped>
.flamegraphs-primary-container .card {
  border: none;
  overflow: hidden;
}

.guardian-card {
  position: relative;
  transition: transform 0.2s, box-shadow 0.2s;
  border-width: 1px;
  border-left-width: 4px;
  overflow: hidden;
}

.guardian-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.card-header {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding: 1rem 1.25rem;
}

.status-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
}

.card-footer {
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  padding: 0.75rem 1rem;
}

.form-check-label {
  font-size: 0.875rem;
}

.border-primary {
  border-left-color: #5e64ff !important;
}

.border-info {
  border-left-color: #17a2b8 !important;
}

.border-success {
  border-left-color: #28a745 !important;
}

.bg-primary-subtle {
  background-color: rgba(94, 100, 255, 0.1) !important;
}

.bg-info-subtle {
  background-color: rgba(23, 162, 184, 0.1) !important;
}

.bg-success-subtle {
  background-color: rgba(40, 167, 69, 0.1) !important;
}

.border-danger {
  border-left-color: #dc3545 !important;
}

.bg-danger-subtle {
  background-color: rgba(220, 53, 69, 0.1) !important;
}

.btn-primary {
  background-color: #5e64ff;
  border-color: #5e64ff;
}

.btn-primary:hover {
  background-color: #4349e8;
  border-color: #4349e8;
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
