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
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import {useRoute} from "vue-router";
import {onBeforeMount, ref} from "vue";
import ThreadService from "@/service/ThreadService";
import ThreadComponent from "@/components/ThreadComponent.vue";
import ThreadCommon from "@/service/thread/model/ThreadCommon";
import ThreadRowData from "@/service/thread/model/ThreadRowData";

const route = useRoute()

const items = [
  {label: 'Threads', route: 'threads'}
]

const projectId = route.params.projectId as string
const profileId = route.params.profileId as string

const threadRows = ref<ThreadRowData[]>()
const threadCommon = ref<ThreadCommon>()

let threadService;
onBeforeMount(() => {
  threadService = new ThreadService(projectId, profileId)

  threadService.list()
      .then((response) => {
        console.log(response)
        threadRows.value = response.rows
        threadCommon.value = response.common
      })
});
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <ThreadComponent v-for="(threadRow, index) in threadRows" :key="index"
                     :index="index"
                     :project-id="projectId"
                     :primary-profile-id="profileId"
                     :thread-common="threadCommon as ThreadCommon"
                     :thread-row="threadRow"/>
  </div>

  <Toast/>
</template>
