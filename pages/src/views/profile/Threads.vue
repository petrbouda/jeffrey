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
import Konva from "konva";

const route = useRoute()

const items = [
  {label: 'Threads', route: 'threads'}
]

const EVENT_COUNT_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) => b.eventsCount - a.eventsCount
const LIFESPAN_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) => b.totalDuration - a.totalDuration
const ALPHABETICAL_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) => a.threadInfo.javaName.localeCompare(b.threadInfo.javaName)

const projectId = route.params.projectId as string
const profileId = route.params.profileId as string

const threadRows = ref<ThreadRowData[]>()
const threadCommon = ref<ThreadCommon>()

const fulltextFilter = ref<string>('')
const fulltextFilterAfterTimeout = ref<string>('')
const selectedSorting = ref<string>('Event Count')
const sortingTypes = ref<string[]>(['Event Count', 'Lifespan', 'Alphabetically'])

const forceRenderThreads = ref<number>(0)

let filterTimeout: ReturnType<typeof setTimeout>

let threadService;
onBeforeMount(() => {
  // Too many layers, it spams the console
  Konva.showWarnings = false;

  threadService = new ThreadService(projectId, profileId)

  threadService.list()
      .then((response) => {
        console.log(response)
        threadRows.value = sortThreadRows(selectedSorting.value, response.rows)
        threadCommon.value = response.common
      })
});

function sortThreadRows(sortingType: string, threadRows: ThreadRowData[] | undefined): ThreadRowData[] | undefined {
  if (threadRows != undefined) {
    const comparator = selectComparator(sortingType)
    return threadRows.sort(comparator)
  } else {
    return threadRows
  }
}

function selectComparator(sortingType: string) {
  if (sortingType === 'Event Count') {
    return EVENT_COUNT_COMPARATOR
  } else if (sortingType === 'Lifespan') {
    return LIFESPAN_COMPARATOR
  } else if (sortingType === 'Alphabetically') {
    return ALPHABETICAL_COMPARATOR
  }
}

function onFilterChange(event: string) {
  clearTimeout(filterTimeout)
  filterTimeout = setTimeout(() => {
    fulltextFilterAfterTimeout.value = event
  }, 750)
}

function sortingChanged(event: any) {
  selectedSorting.value = event.value
  threadRows.value = sortThreadRows(event.value, threadRows.value)

  // Trigger re-render of the threads
  forceRenderThreads.value++
}
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <div class="grid">
      <div class="col-4">
        <span class="p-input-icon-left w-full">
            <i class="pi pi-search"/>
            <InputText type="text" class="w-full" placeholder="Full-text Filter" v-model="fulltextFilter"
                       @update:model-value="onFilterChange"/>
        </span>
      </div>

      <div class="col-4">
        <SelectButton v-model="selectedSorting" :options="sortingTypes" @change="sortingChanged"/>
      </div>
    </div>

    <Divider/>

    <div :key="forceRenderThreads">
      <div v-for="(threadRow, index) in threadRows" :key="index">
        <ThreadComponent v-if="threadRow.threadInfo.javaName.includes(fulltextFilterAfterTimeout)"
                         :index="index"
                         :project-id="projectId"
                         :primary-profile-id="profileId"
                         :thread-common="threadCommon as ThreadCommon"
                         :thread-row="threadRow"/>
      </div>
    </div>
  </div>

  <Toast/>
</template>
