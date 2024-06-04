<script setup>

import {onBeforeMount, ref} from "vue";
import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventTypes from "@/service/EventTypes";
import FormattingService from "../../service/FormattingService";
import FlamegraphService from "@/service/flamegraphs/FlamegraphService";
import SectionCard from "@/components/SectionCard.vue";
import EventTitleFormatter from "@/service/flamegraphs/EventTitleFormatter";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import ProfileDialog from "@/components/ProfileDialog.vue";
import Flamegraph from "@/service/flamegraphs/Flamegraph";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";
import ProfileType from "@/service/flamegraphs/ProfileType";

const objectAllocationEvents = ref([])
const executionSampleEvents = ref([])

const loaded = ref(false)

const profileSelector = ref(false)

const items = [
  {label: 'Sub-Second'},
  {label: 'Differential', route: '/common/diff-subsecond-sections'}
]

onBeforeMount(() => {
  if (SecondaryProfileService.id() != null) {
    FlamegraphService.supportedEventsDiff(PrimaryProfileService.id(), SecondaryProfileService.id())
        .then((data) => {
          categorizeEventTypes(data)
          loaded.value = true
        })
  } else {
    profileSelector.value = true
  }
});

function categorizeEventTypes(evenTypes) {
  for (let eventType of evenTypes) {
    if (EventTypes.isExecutionEventType(eventType.code)) {
      executionSampleEvents.value.push(eventType)
    } else if (EventTypes.isAllocationEventType(eventType.code)) {
      objectAllocationEvents.value.push(eventType)
    }
  }
}
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
          thread-mode-opt="false"
          event-desc="Execution Sample"
          :graph-mode="GraphType.DIFFERENTIAL"
          :events="executionSampleEvents"
          :loaded="loaded"/>

      <SectionCard
          router-forward="subsecond"
          title="Object Allocations"
          :title-formatter="EventTitleFormatter.allocationSamples"
          color="green"
          icon="memory"
          thread-mode-opt="false"
          weight-opt="true"
          weight-desc="Total Allocation"
          :weight-formatter="FormattingService.formatBytes"
          event-desc="Object Allocation Events"
          :graph-mode="GraphType.DIFFERENTIAL"
          :events="objectAllocationEvents"
          :loaded="loaded"/>
    </div>
  </div>

  <ProfileDialog v-if="profileSelector" :activatedFor="ProfileType.SECONDARY" activated="true"></ProfileDialog>
</template>
