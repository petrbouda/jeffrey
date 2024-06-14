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
import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventTypes from "@/service/EventTypes";
import FormattingService from "../../service/FormattingService";
import FlamegraphService from "@/service/flamegraphs/FlamegraphService";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import Flamegraph from "@/service/flamegraphs/Flamegraph";
import ProfileDialog from "@/components/ProfileDialog.vue";
import EventTitleFormatter from "@/service/flamegraphs/EventTitleFormatter";
import SectionCard from "@/components/SectionCard.vue";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";
import ProfileType from "@/service/flamegraphs/ProfileType";

const objectAllocationEvents = ref([])
const executionSampleEvents = ref([])

const loaded = ref(false)

const profileSelector = ref(false)

const items = [
  {label: 'Flamegraphs'},
  {label: 'Differential', route: '/common/diff-flamegraph-sections'}
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
          router-forward="flamegraph"
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
          router-forward="flamegraph"
          title="Object Allocations"
          :title-formatter="EventTitleFormatter.allocationSamples"
          color="green"
          icon="memory"
          thread-mode-opt="false"
          weight-opt="true"
          weight-selected="true"
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
