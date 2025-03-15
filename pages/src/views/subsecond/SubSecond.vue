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
import {onBeforeMount, ref} from 'vue';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import MessageBus from '@/service/MessageBus';
import Utils from '@/service/Utils';
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import router from "@/router";
import GraphType from "@/service/flamegraphs/GraphType";
import SubSecondComponent from "@/components/SubSecondComponent.vue";
import {useRoute} from "vue-router";
import FlamegraphClient from "@/service/flamegraphs/client/FlamegraphClient";
import PrimaryFlamegraphClient from "@/service/flamegraphs/client/PrimaryFlamegraphClient";
import DifferentialFlamegraphClient from "@/service/flamegraphs/client/DifferentialFlamegraphClient";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/service/flamegraphs/tooltips/FlamegraphTooltipFactory";
import SubSecondDataProvider from "@/service/subsecond/SubSecondDataProvider";
import SubSecondDataProviderImpl from "@/service/subsecond/SubSecondDataProviderImpl";
import HeatmapTooltip from "@/service/subsecond/HeatmapTooltip";
import GraphUpdater from "@/service/flamegraphs/updater/GraphUpdater";
import OnlyFlamegraphGraphUpdater from "@/service/flamegraphs/updater/OnlyFlamegraphGraphUpdater";
import TimeRange from "@/service/flamegraphs/model/TimeRange";

const route = useRoute()

const queryParams = router.currentRoute.value.query

const showDialog = ref<boolean>(false);
let graphUpdater: GraphUpdater
let flamegraphTooltip: FlamegraphTooltip

let primarySubSecondDataProvider: SubSecondDataProvider
let secondarySubSecondDataProvider: SubSecondDataProvider | null = null

let useWeight = queryParams.useWeight === 'true'

onBeforeMount(() => {
  primarySubSecondDataProvider = new SubSecondDataProviderImpl(
      route.params.projectId as string,
      route.params.profileId as string,
      queryParams.eventType,
      useWeight,
  )

  if (queryParams.graphMode === GraphType.DIFFERENTIAL) {
    secondarySubSecondDataProvider = new SubSecondDataProviderImpl(
        SecondaryProfileService.projectId(),
        SecondaryProfileService.id(),
        queryParams.eventType,
        useWeight,
    )
  }
})

function createOnSelectedCallback(profileId: string) {
  return function (startTime: number[], endTime: number[]) {
    let selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    showFlamegraph(profileId, selectedTimeRange);
  };
}

const subSecondGraphsCleanup = () => {
  MessageBus.emit(MessageBus.SUBSECOND_SELECTION_CLEAR, {});
}

function showFlamegraph(profileId: string, timeRange: TimeRange) {
  subSecondGraphsCleanup()

  let isPrimary = queryParams.graphMode === GraphType.PRIMARY
  let flamegraphClient: FlamegraphClient
  if (isPrimary) {
    flamegraphClient = new PrimaryFlamegraphClient(
        route.params.projectId as string,
        profileId,
        queryParams.eventType,
        false,
        useWeight,
        false,
        false,
        false,
        null
    )
  } else {
    flamegraphClient = new DifferentialFlamegraphClient(
        route.params.projectId as string,
        route.params.profileId as string,
        SecondaryProfileService.id() as string,
        queryParams.eventType,
        useWeight,
        false,
        false,
        false,
    )
  }

  graphUpdater = new OnlyFlamegraphGraphUpdater(flamegraphClient, timeRange)
  flamegraphTooltip = FlamegraphTooltipFactory.create(queryParams.eventType, useWeight, !isPrimary)

  // Show the flamegraph dialog
  showDialog.value = true
}
</script>

<template>
  <SubSecondComponent
      :primary-data-provider="primarySubSecondDataProvider"
      :primary-selected-callback="createOnSelectedCallback(route.params.profileId as string)"
      :secondary-data-provider="secondarySubSecondDataProvider"
      :secondary-selected-callback="createOnSelectedCallback(SecondaryProfileService.id() as string)"
      :tooltip="new HeatmapTooltip(queryParams.eventType, useWeight)"
  />
  <Toast/>

  <Dialog class="scrollable" header=" " :pt="{root: 'overflow-hidden'}" v-model:visible="showDialog" modal
          :style="{ width: '95%' }" style="overflow-y: auto">
    <FlamegraphComponent
        :with-timeseries="false"
        :with-search="null"
        :use-weight="useWeight"
        :use-guardian="null"
        :save-enabled="true"
        scrollable-wrapper-class="p-dialog-content"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"/>
  </Dialog>
</template>

<style scoped lang="scss"></style>
