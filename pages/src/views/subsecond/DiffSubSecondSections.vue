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
import EventTitleFormatter from "@/service/flamegraphs/EventTitleFormatter";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import ProfileDialog from "@/components/ProfileDialog.vue";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";
import ProfileType from "@/service/flamegraphs/ProfileType";
import {useRoute} from "vue-router";

const objectAllocationEvents = ref([])
const executionSampleEvents = ref([])
const wallClockEvents = ref([])

const loaded = ref(false)

const route = useRoute()

const profileSelector = ref(false)

const items = [
  {label: 'Sub-Second'},
  {label: 'Differential', route: 'diff-subsecond-sections'}
]

onBeforeMount(() => {
  if (SecondaryProfileService.id() != null) {
    new FlamegraphService(route.params.projectId, route.params.profileId, SecondaryProfileService.id())
        .supportedEventsDiff()
        .then((data) => {
          categorizeEventTypes(data)
          loaded.value = true
        })
  } else {
    profileSelector.value = true
  }
});

function categorizeEventTypes(eventTypes) {
  for (let key in eventTypes) {
    if (EventTypes.isExecutionEventType(key)) {
      executionSampleEvents.value.push(eventTypes[key])
    } else if (EventTypes.isAllocationEventType(key)) {
      objectAllocationEvents.value.push(eventTypes[key])
    } else if (EventTypes.isWallClock(key)) {
      wallClockEvents.value.push(eventTypes[key])
    }
  }
}
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card">
    <div class="grid">
      <SectionCard v-for="(event, index) in executionSampleEvents" :key="index"
                   router-forward="subsecond"
                   title="Execution Samples"
                   color="blue"
                   icon="sprint"
                   thread-mode-opt="false"
                   weight-desc="Total Time on CPU"
                   :weight-formatter="FormattingService.formatDuration2Units"
                   :graph-mode="GraphType.DIFFERENTIAL"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in wallClockEvents" :key="index"
                   router-forward="subsecond"
                   title="Wall-Clock Samples"
                   color="purple"
                   icon="alarm"
                   thread-mode-opt="false"
                   weight-desc="Total Time"
                   :weight-formatter="FormattingService.formatDuration2Units"
                   exclude-non-java-samples-opt="true"
                   exclude-non-java-samples-selected="true"
                   exclude-idle-samples-opt="true"
                   exclude-idle-samples-selected="true"
                   :graph-mode="GraphType.DIFFERENTIAL"
                   :event="event"
                   :loaded="loaded"/>

      <SectionCard v-for="(event, index) in objectAllocationEvents" :key="index"
                   router-forward="subsecond"
                   title="Allocation Samples"
                   color="green"
                   icon="memory"
                   thread-mode-opt="false"
                   weight-opt="true"
                   weight-selected="true"
                   weight-desc="Total Allocation"
                   :weight-formatter="FormattingService.formatBytes"
                   :graph-mode="GraphType.DIFFERENTIAL"
                   :event="event"
                   :loaded="loaded"/>
    </div>
  </div>

  <ProfileDialog v-if="profileSelector" :activatedFor="ProfileType.SECONDARY" activated="true"></ProfileDialog>
</template>
