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
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import router from "@/router";
import {onBeforeMount} from "vue";
import SecondaryProfileService from "@/services/SecondaryProfileService";
import GraphType from "@/services/flamegraphs/GraphType";
import {useRoute} from "vue-router";
import PrimaryFlamegraphClient from "@/services/flamegraphs/client/PrimaryFlamegraphClient";
import DifferentialFlamegraphClient from "@/services/flamegraphs/client/DifferentialFlamegraphClient";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/services/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import FullGraphUpdater from "@/services/flamegraphs/updater/FullGraphUpdater";

let queryParams = router.currentRoute.value.query

const route = useRoute()

let flamegraphTooltip: FlamegraphTooltip
let graphUpdater: GraphUpdater

const eventType = queryParams.eventType as string
const useThreadMode = queryParams.useThreadMode === 'true'
const useWeight = queryParams.useWeight === 'true'
const excludeNonJavaSamples = queryParams.excludeNonJavaSamples === 'true'
const excludeIdleSamples = queryParams.excludeIdleSamples === 'true'
const onlyUnsafeAllocationSamples = queryParams.onlyUnsafeAllocationSamples === 'true'
const isDifferential = queryParams.graphMode === GraphType.DIFFERENTIAL

onBeforeMount(() => {
  let flamegraphClient
  if (queryParams.graphMode === GraphType.PRIMARY) {
    flamegraphClient = new PrimaryFlamegraphClient(
        route.params.projectId as string,
        route.params.profileId as string,
        eventType,
        useThreadMode,
        useWeight,
        excludeNonJavaSamples,
        excludeIdleSamples,
        onlyUnsafeAllocationSamples,
        null)
  } else {
    flamegraphClient = new DifferentialFlamegraphClient(
        route.params.projectId as string,
        route.params.profileId as string,
        SecondaryProfileService.id() as string,
        eventType,
        useWeight,
        excludeNonJavaSamples,
        excludeIdleSamples,
        onlyUnsafeAllocationSamples)
  }

  graphUpdater = new FullGraphUpdater(flamegraphClient, true)
  flamegraphTooltip = FlamegraphTooltipFactory.create(eventType, useWeight, isDifferential)
});
</script>

<template>
  <div style="padding-left: 5px; padding-right: 5px;">
    <TimeseriesComponent
        :graph-type="queryParams.graphMode as string"
        :event-type="eventType"
        :use-weight="useWeight"
        :with-search="null"
        :search-enabled="queryParams.graphMode === GraphType.PRIMARY"
        :zoom-enabled="true"
        :graph-updater="graphUpdater"/>
    <FlamegraphComponent
        :with-timeseries="queryParams.graphMode === GraphType.PRIMARY"
        :with-search="null"
        :use-weight="useWeight"
        :use-guardian="null"
        :save-enabled="true"
        :scrollable-wrapper-class="null"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"/>
  </div>
</template>
