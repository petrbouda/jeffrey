<script setup>
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import PrimaryProfileService from "@/service/PrimaryProfileService";
import Flamegraph from "@/service/Flamegraph";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import router from "@/router";
import {onBeforeMount} from "vue";

let queryParams

onBeforeMount(() => {
  queryParams = router.currentRoute.value.query
});
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <TimeseriesComponent :primary-profile-id="PrimaryProfileService.id()"
                         :graph-mode="Flamegraph.PRIMARY"
                         :event-type="queryParams.eventType"
                         :use-weight="queryParams.useWeight"/>
    <FlamegraphComponent :primary-profile-id="PrimaryProfileService.id()"
                         :event-type="queryParams.eventType"
                         :use-thread-mode="queryParams.useThreadMode"
                         :use-weight="queryParams.useWeight"
                         :with-search-bar="false"/>
  </div>
</template>
