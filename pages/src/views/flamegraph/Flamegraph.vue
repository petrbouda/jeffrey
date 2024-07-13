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
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import PrimaryProfileService from "@/service/PrimaryProfileService";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import router from "@/router";
import {onBeforeMount} from "vue";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import GraphType from "@/service/flamegraphs/GraphType";

let queryParams

onBeforeMount(() => {
  queryParams = router.currentRoute.value.query
});
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <TimeseriesComponent
        :primary-profile-id="PrimaryProfileService.id()"
        :secondary-profile-id="SecondaryProfileService.id()"
        :graph-type="queryParams.graphMode"
        :event-type="queryParams.eventType"
        :use-weight="queryParams.useWeight"/>
    <FlamegraphComponent
        :primary-profile-id="PrimaryProfileService.id()"
        :secondary-profile-id="SecondaryProfileService.id()"
        :with-timeseries="true"
        :event-type="queryParams.eventType"
        :use-thread-mode="queryParams.useThreadMode"
        :use-weight="queryParams.useWeight"
        :graph-type="queryParams.graphMode"
        :export-enabled="false"
        :generated="false"/>
  </div>
</template>
