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
import {useRoute} from 'vue-router';
import {onBeforeMount, ref} from "vue";
import FlamegraphService from "@/service/flamegraphs/FlamegraphService";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import FlamegraphData from "@/service/flamegraphs/model/FlamegraphData";
import BasicFlamegraphTooltip from "@/service/flamegraphs/tooltips/BasicFlamegraphTooltip";
import FlamegraphClient from "@/service/flamegraphs/client/FlamegraphClient";
import StaticFlamegraphClient from "@/service/flamegraphs/client/StaticFlamegraphClient";
import TimeseriesData from "@/service/timeseries/model/TimeseriesData";

const route = useRoute();

let content: FlamegraphData;
let eventType: string;
let useWeight: boolean;
let graphType: string
let flamegraphTooltip: BasicFlamegraphTooltip;
let flamegraphClient: FlamegraphClient;

const ready = ref<boolean>(false)

onBeforeMount(() => {
  new FlamegraphService(route.params.projectId as string, route.params.profileId as string)
      .getById(route.query.flamegraphId as string)
      .then((data) => {
        content = data.content
        eventType = data.eventType
        useWeight = data.useWeight
        graphType = data.graphType
        flamegraphTooltip = new BasicFlamegraphTooltip(eventType, useWeight)
        flamegraphClient = new StaticFlamegraphClient(content, new TimeseriesData([]))
        ready.value = true
      });
});
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <FlamegraphComponent v-if="ready"
        :with-timeseries="false"
        :with-search="null"
        :event-type="eventType"
        :use-weight="useWeight"
        :use-guardian="null"
        :time-range="null"
        :export-enabled="false"
        :scrollable-wrapper-class="null"
        :flamegraph-tooltip="flamegraphTooltip"
        :flamegraph-client="flamegraphClient"/>
  </div>
</template>
