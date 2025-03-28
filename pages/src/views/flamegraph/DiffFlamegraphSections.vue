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
import SecondaryProfileService from "@/service/SecondaryProfileService";
import ProfileDialog from "@/components/SecondaryProfileDialog.vue";
import SectionCard from "@/components/SectionCard.vue";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";
import {useRoute} from "vue-router";
import EventSummariesClient from "@/service/flamegraphs/client/EventSummariesClient.js";
import EventSummary from "@/service/flamegraphs/model/EventSummary";

const objectAllocationEvents = ref<EventSummary[]>([])
const executionSampleEvents = ref<EventSummary[]>([])
const wallClockEvents = ref<EventSummary[]>([])

const route = useRoute()
const loaded = ref(false)

const profileSelector = ref(false)

const items = [
  {label: 'Flamegraphs'},
  {label: 'Differential', route: 'diff-flamegraph-sections'}
]

onBeforeMount(() => {
  if (SecondaryProfileService.id() != null) {
    EventSummariesClient.differential(
        route.params.projectId as string, route.params.profileId as string, SecondaryProfileService.id())
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

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card">
    <div class="grid">
      <SectionCard v-for="(event, index) in executionSampleEvents" :key="index"
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

  <ProfileDialog
      v-if="profileSelector"
      :activated="true"
      :primary-project-id="route.params.projectId as string"/>
</template>
