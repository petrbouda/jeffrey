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
import ApexTimeSeriesChart from "@/components/ApexTimeSeriesChart.vue";
import SearchBarComponent from "@/components/SearchBarComponent.vue";
import router from "@/router";
import {onBeforeMount} from "vue";
import SecondaryProfileService from "@/services/SecondaryProfileService";
import GraphType from "@/services/flamegraphs/GraphType";
import {useRoute} from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import PrimaryFlamegraphClient from "@/services/flamegraphs/client/PrimaryFlamegraphClient";
import DifferentialFlamegraphClient from "@/services/flamegraphs/client/DifferentialFlamegraphClient";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/services/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import FullGraphUpdater from "@/services/flamegraphs/updater/FullGraphUpdater";

let queryParams = router.currentRoute.value.query

const route = useRoute()
const { workspaceId, projectId } = useNavigation();

let flamegraphTooltip: FlamegraphTooltip
let graphUpdater: GraphUpdater

const eventType = queryParams.eventType as string
const useThreadMode = queryParams.useThreadMode === 'true'
const useWeight = queryParams.useWeight === 'true'
const excludeNonJavaSamples = queryParams.excludeNonJavaSamples === 'true'
const excludeIdleSamples = queryParams.excludeIdleSamples === 'true'
const onlyUnsafeAllocationSamples = queryParams.onlyUnsafeAllocationSamples === 'true'
const isDifferential = queryParams.graphMode === GraphType.DIFFERENTIAL
const isPrimary = queryParams.graphMode === GraphType.PRIMARY

onBeforeMount(() => {
  // Scroll the workspace-content container to top
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }

  let flamegraphClient
  if (queryParams.graphMode === GraphType.PRIMARY) {
    flamegraphClient = new PrimaryFlamegraphClient(
        workspaceId.value!,
        projectId.value!,
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
        workspaceId.value!,
        projectId.value!,
        route.params.profileId as string,
        SecondaryProfileService.id() as string,
        eventType,
        useWeight,
        excludeNonJavaSamples,
        excludeIdleSamples,
        onlyUnsafeAllocationSamples)
  }

  graphUpdater = new FullGraphUpdater(flamegraphClient, true)
  graphUpdater.setTimeseriesSearchEnabled(isPrimary)
  flamegraphTooltip = FlamegraphTooltipFactory.create(eventType, useWeight, isDifferential)
});
</script>

<template>
  <div style="padding-left: 5px; padding-right: 5px;">
    <SearchBarComponent
        :graph-updater="graphUpdater"
        :with-timeseries="true"/>
    <ApexTimeSeriesChart
        :graph-updater="graphUpdater"
        :primary-title="isDifferential ? 'Primary' : undefined"
        :secondary-title="isDifferential ? 'Secondary' : undefined"
        :primary-axis-type="useWeight ? 'bytes' : 'number'"
        :visible-minutes="60"
        :zoom-enabled="true"
        time-unit="milliseconds"/>
    <FlamegraphComponent
        :with-timeseries="isPrimary"
        :use-weight="useWeight"
        :use-guardian="null"
        :scrollable-wrapper-class="null"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"/>
  </div>
</template>
