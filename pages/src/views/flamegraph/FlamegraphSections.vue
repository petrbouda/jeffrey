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

<script setup lang="ts">

import {onBeforeMount, ref} from "vue";
import EventTypes from "@/service/EventTypes";
import FormattingService from "../../service/FormattingService";
import SectionCard from "@/components/SectionCard.vue";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";
import {useRoute} from "vue-router";
import EventSummariesClient from "@/service/flamegraphs/client/EventSummariesClient";
import EventSummary from "@/service/flamegraphs/model/EventSummary";

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

const items = [
  {label: 'Flamegraphs'},
  {label: 'Primary', route: 'flamegraph-sections'}
]

function stripLeadingJava(label: string): string {
  return label.replaceAll('Java', '')
}
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card">
    <div class="grid">
      <SectionCard v-if="loaded" v-for="(event, index) in executionSampleEvents" :key="index"
                   router-forward="flamegraph"
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
                   :title="stripLeadingJava(event.label)"
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
