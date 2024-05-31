<script setup>
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import PrimaryProfileService from "@/service/PrimaryProfileService";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import router from "@/router";
import {onBeforeMount} from "vue";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import Flamegraph from "@/service/flamegraphs/Flamegraph";
import DiffFlamegraphComponent from "@/components/DiffFlamegraphComponent.vue";
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
        :graph-mode="queryParams.graphMode"
        :event-type="queryParams.eventType"
        :use-weight="queryParams.useWeight"/>
    <div v-if="queryParams.graphMode === GraphType.PRIMARY">
      <FlamegraphComponent
          :primary-profile-id="PrimaryProfileService.id()"
          :event-type="queryParams.eventType"
          :use-thread-mode="queryParams.useThreadMode"
          :use-weight="queryParams.useWeight"/>
    </div>
    <div v-else>
      <DiffFlamegraphComponent
          :primary-profile-id="PrimaryProfileService.id()"
          :secondary-profile-id="SecondaryProfileService.id()"
          :event-type="queryParams.eventType"
          :use-weight="queryParams.useWeight"/>
    </div>
  </div>
</template>
