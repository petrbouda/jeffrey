<script setup>

import {onBeforeMount, ref} from "vue";
import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventTypes from "@/service/EventTypes";
import FormattingService from "../../service/FormattingService";
import FlamegraphService from "@/service/flamegraphs/FlamegraphService";
import SectionCard from "@/components/SectionCard.vue";
import EventTitleFormatter from "@/service/flamegraphs/EventTitleFormatter";
import Flamegraph from "@/service/flamegraphs/Flamegraph";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";

const objectAllocationEvents = ref([])
const executionSampleEvents = ref([])
const blockingEvents = ref([])

const loaded = ref(false)

onBeforeMount(() => {
  FlamegraphService.supportedEvents(PrimaryProfileService.id())
      .then((data) => {
        categorizeEventTypes(data)
        loaded.value = true
      })
});

function categorizeEventTypes(evenTypes) {
  for (let eventType of evenTypes) {
    if (EventTypes.isExecutionEventType(eventType.code)) {
      executionSampleEvents.value.push(eventType)
    } else if (EventTypes.isAllocationEventType(eventType.code)) {
      objectAllocationEvents.value.push(eventType)
    } else if (EventTypes.isBlockingEventType(eventType.code)) {
      blockingEvents.value.push(eventType)
    }
  }
}

const items = [
  {label: 'Sub-Second'},
  {label: 'Primary', route: '/common/subsecond-sections'}
]
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card">
    <div class="grid">
      <SectionCard
          router-forward="subsecond"
          title="Execution Samples"
          :title-formatter="EventTitleFormatter.executionSamples"
          color="blue"
          icon="sprint"
          thread-mode-opt="true"
          event-desc="Execution Sample"
          :graph-mode="GraphType.PRIMARY"
          :events="executionSampleEvents"
          :loaded="loaded"/>

      <SectionCard
          router-forward="subsecond"
          title="Object Allocations"
          :title-formatter="EventTitleFormatter.allocationSamples"
          color="green"
          icon="memory"
          thread-mode-opt="true"
          weight-opt="true"
          weight-desc="Total Allocation"
          :weight-formatter="FormattingService.formatBytes"
          event-desc="Object Allocation Events"
          :graph-mode="GraphType.PRIMARY"
          :events="objectAllocationEvents"
          :loaded="loaded"/>

      <SectionCard
          router-forward="subsecond"
          title="Blocking Samples"
          :title-formatter="EventTitleFormatter.blockingSamples"
          color="red"
          icon="lock"
          thread-mode-opt="true"
          weight-opt="true"
          weight-desc="Blocked Time"
          :weight-formatter="FormattingService.formatDuration"
          event-desc="Blocking Events"
          :graph-mode="GraphType.PRIMARY"
          :events="blockingEvents"
          :loaded="loaded"/>
    </div>
  </div>
</template>