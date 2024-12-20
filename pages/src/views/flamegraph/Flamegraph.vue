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
import SecondaryProfileService from "@/service/SecondaryProfileService";
import GraphType from "@/service/flamegraphs/GraphType";
import {useRoute} from "vue-router";
import PrimaryFlamegraphDataProvider from "@/service/flamegraphs/service/PrimaryFlamegraphDataProvider";
import DifferentialFlamegraphDataProvider from "@/service/flamegraphs/service/DifferentialFlamegraphDataProvider";
import FlamegraphDataProvider from "@/service/flamegraphs/service/FlamegraphDataProvider";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/service/flamegraphs/tooltips/FlamegraphTooltipFactory";

let queryParams = router.currentRoute.value.query

const route = useRoute()

let flamegraphDataProvider: FlamegraphDataProvider
let flamegraphTooltip: FlamegraphTooltip

const eventType = queryParams.eventType
const useThreadMode = queryParams.useThreadMode === 'true'
const useWeight = queryParams.useWeight === 'true'
const excludeNonJavaSamples = queryParams.excludeNonJavaSamples === 'true'
const excludeIdleSamples = queryParams.excludeIdleSamples === 'true'
const isDifferential = queryParams.graphMode === GraphType.DIFFERENTIAL

onBeforeMount(() => {
  if (queryParams.graphMode === GraphType.PRIMARY) {

    flamegraphDataProvider = new PrimaryFlamegraphDataProvider(
        route.params.projectId as string,
        route.params.profileId as string,
        eventType,
        useThreadMode,
        useWeight,
        excludeNonJavaSamples,
        excludeIdleSamples,
        null)
  } else {
    flamegraphDataProvider = new DifferentialFlamegraphDataProvider(
        route.params.projectId as string,
        route.params.profileId as string,
        SecondaryProfileService.id(),
        eventType,
        useWeight,
        excludeNonJavaSamples,
        excludeIdleSamples)
  }

  flamegraphTooltip = FlamegraphTooltipFactory.create(eventType, useWeight, isDifferential)
});
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <TimeseriesComponent
        :graph-type="queryParams.graphMode"
        :event-type="eventType"
        :use-weight="useWeight"
        :with-search="null"
        :search-enabled="queryParams.graphMode === GraphType.PRIMARY"
        :zoom-enabled="true"
        :flamegraph-data-provider="flamegraphDataProvider"/>
    <FlamegraphComponent
        :with-timeseries="queryParams.graphMode === GraphType.PRIMARY"
        :with-search="null"
        :use-weight="useWeight"
        :use-guardian="null"
        :time-range="null"
        :export-enabled="false"
        :scrollable-wrapper-class="null"
        :flamegraph-tooltip="flamegraphTooltip"
        :flamegraph-data-provider="flamegraphDataProvider"/>
  </div>
</template>
