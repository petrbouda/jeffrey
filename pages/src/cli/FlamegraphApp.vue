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
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import ReplacementResolver from "@/service/replace/ReplacementResolver";
import {onBeforeMount} from "vue";
import FlamegraphDataProvider from "@/service/flamegraphs/service/FlamegraphDataProvider";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/service/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphType from "@/service/flamegraphs/GraphType";
import StaticFlamegraphDataProvider from "@/service/flamegraphs/service/StaticFlamegraphDataProvider";

let flamegraphDataProvider: FlamegraphDataProvider
let flamegraphTooltip: FlamegraphTooltip

const isDifferential = ReplacementResolver.graphType() === GraphType.DIFFERENTIAL

onBeforeMount(() => {
  flamegraphTooltip = FlamegraphTooltipFactory.create(
      ReplacementResolver.eventType(),
      ReplacementResolver.useWeight(),
      isDifferential
  )

  flamegraphDataProvider = new StaticFlamegraphDataProvider(
      JSON.parse(ReplacementResolver.flamegraphData()),
      JSON.parse(ReplacementResolver.timeseriesData()))
})
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <!--
      By default, include Timeseries along with Flamegraph.
      Remove Timeseries only if it's required by CLI Tool
    -->
    <div v-if="ReplacementResolver.withTimeseries()">
      <TimeseriesComponent
          :graph-type="ReplacementResolver.graphType()"
          :event-type="ReplacementResolver.eventType()"
          :use-weight="ReplacementResolver.useWeight()"
          :with-search="null"
          :search-enabled="!isDifferential"
          :zoom-enabled="false"
          :flamegraph-data-provider="flamegraphDataProvider"/>
    </div>
    <FlamegraphComponent
        :with-timeseries="ReplacementResolver.withTimeseries()"
        :with-search="ReplacementResolver.search()"
        :use-weight="ReplacementResolver.useWeight()"
        :use-guardian="null"
        :time-range="null"
        :export-enabled="false"
        :scrollable-wrapper-class="null"
        :flamegraph-tooltip="flamegraphTooltip"
        :flamegraph-data-provider="flamegraphDataProvider"/>
  </div>
</template>

<style scoped></style>
