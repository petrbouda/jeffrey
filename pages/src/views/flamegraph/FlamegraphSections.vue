<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup>

import {onBeforeMount, ref} from "vue";
import EventTypes from "@/service/EventTypes";
import FormattingService from "../../service/FormattingService";
import FlamegraphService from "@/service/flamegraphs/FlamegraphService";
import SectionCard from "@/components/SectionCard.vue";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";
import {useRoute} from "vue-router";

const objectAllocationEvents = ref([])
const executionSampleEvents = ref([])
const blockingEvents = ref([])
const wallClockEvents = ref([])
const nativeAllocationEvents = ref([])
const nativeLeakEvents = ref([])

const loaded = ref(false)

const route = useRoute()

onBeforeMount(() => {
  new FlamegraphService(route.params.projectId, route.params.profileId)
      .supportedEvents()
      .then((data) => {
        categorizeEventTypes(data)
        loaded.value = true
      })
});

function categorizeEventTypes(eventTypes) {
  for (let key in eventTypes) {
    let eventType = eventTypes[key];

    if (EventTypes.isExecutionEventType(key)) {
      executionSampleEvents.value.push(eventType)
    } else if (EventTypes.isAllocationEventType(key)) {
      objectAllocationEvents.value.push(eventType)
    } else if (EventTypes.isBlockingEventType(key)) {
      blockingEvents.value.push(eventType)
    } else if (EventTypes.isWallClock(key)) {
      wallClockEvents.value.push(eventType)
    } else if (EventTypes.isNativeAllocationEventType(key)) {
      nativeAllocationEvents.value.push(eventType)
    } else if (EventTypes.isNativeLeakEventType(key)) {
      nativeLeakEvents.value.push(eventType)
    }
  }
}

const items = [
  {label: 'Flamegraphs'},
  {label: 'Primary', route: 'flamegraph-sections'}
]

function stripLeadingJava(label) {
  return label.replaceAll('Java', '')
}
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card">
    <div class="grid">
      <SectionCard v-for="(event, index) in executionSampleEvents" :key="index"
                   router-forward="flamegraph"
                   title="Execution Samples"
                   color="blue"
                   icon="sprint"
                   thread-mode-opt="true"
                   weight-desc="Total Time on CPU"
                   :weight-formatter="FormattingService.formatDuration2Units"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in wallClockEvents" :key="index"
                   router-forward="flamegraph"
                   title="Wall-Clock Samples"
                   color="purple"
                   icon="alarm"
                   thread-mode-opt="true"
                   thread-mode-selected="true"
                   weight-desc="Total Time"
                   :weight-formatter="FormattingService.formatDuration2Units"
                   exclude-non-java-samples-opt="true"
                   exclude-non-java-samples-selected="true"
                   exclude-idle-samples-opt="true"
                   exclude-idle-samples-selected="true"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in objectAllocationEvents" :key="index"
                   router-forward="flamegraph"
                   title="Allocation Samples"
                   color="green"
                   icon="memory"
                   thread-mode-opt="true"
                   weight-opt="true"
                   weight-selected="true"
                   weight-desc="Total Allocation"
                   :weight-formatter="FormattingService.formatBytes"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in nativeAllocationEvents" :key="index"
                   router-forward="flamegraph"
                   title="Native Allocation Samples"
                   color="pink"
                   icon="memory"
                   thread-mode-opt="true"
                   weight-opt="true"
                   weight-selected="true"
                   weight-desc="Total Allocation"
                   :weight-formatter="FormattingService.formatBytes"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in nativeLeakEvents" :key="index"
                   router-forward="flamegraph"
                   title="Native Allocation Leaks"
                   color="pink"
                   icon="memory"
                   thread-mode-opt="true"
                   weight-opt="true"
                   weight-selected="true"
                   weight-desc="Total Allocation"
                   :weight-formatter="FormattingService.formatBytes"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in blockingEvents" :key="index"
                   router-forward="flamegraph"
                   :title="stripLeadingJava(event.label)"
                   color="red"
                   icon="lock"
                   thread-mode-opt="true"
                   weight-opt="true"
                   weight-selected="true"
                   weight-desc="Blocked Time"
                   :weight-formatter="FormattingService.formatDuration2Units"
                   :graph-mode="GraphType.PRIMARY"
                   :event="event"
                   :loaded="loaded"/>
    </div>
  </div>
</template>
