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
import ThreadRow from "@/service/thread/ThreadRow";

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

const infoDialogVisible = ref<boolean>(false)

let filterTimeout: ReturnType<typeof setTimeout>

let threadService;

onBeforeMount(() => {
  // Too many layers, it spams the console
  Konva.showWarnings = false;

  threadService = new ThreadService(projectId, profileId)

  threadService.list()
      .then((response) => {
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
      <Button class="p-button-primary justify-content-center m-0 p-3" text @click="infoDialogVisible = true">
        <div class="material-symbols-outlined text-2xl">question_mark</div>
      </Button>
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

  <Dialog header="Thread View Information" v-model:visible="infoDialogVisible" :modal="true">
    <p class="line-height-3 m-0">
      <h6>Thread's Timeline</h6>
      <ul>
        <li>Timeline contains green parts representing the lifespan of the threads.</li>
        <li>Other events fits into the thread's lifespan.</li>
        <li>Entire timeline is divided into pixels. For longer timelines, multiple events can be represented by a single pixel.</li>
        <li>One pixel of the timeline can keep multiple events of same type (the first one shows details), or different types.</li>
      </ul>

      <h6>Event Types</h6>
      <table>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.lifespanColor}"></div></td>
          <td>Lifespan of the thread, time between Thread Start and End</td>
        </tr>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.parkedColor}"></div></td>
          <td>Parking of the thread: <b>LockSupport#park()</b> (e.g. parking threads in Thread Pools)</td>
        </tr>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.sleepColor}"></div></td>
          <td>Threads Sleep, emitted by <b>Thread#sleep()</b></td>
        </tr>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.waitingColor}"></div></td>
          <td>Thread Wait, emitted by <b>Thread#wait()</b></td>
        </tr>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.blockedColor}"></div></td>
          <td>Blocked thread, caused by <b>MonitorEnter</b> (e.g. synchronized)</td>
        </tr>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.socketReadColor}"></div></td>
          <td>Blocking reads from a Socket (e.g. <b>SocketInputStream#read</b>)</td>
        </tr>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.socketWriteColor}"></div></td>
          <td>Blocking writes to a Socket (e.g. <b>SocketOutputStream#write</b>)</td>
        </tr>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.fileReadColor}"></div></td>
          <td>Blocking reads from a File (e.g. <b>FileInputStream#read</b>)</td>
        </tr>
        <tr>
          <td><div class="mr-2 w-1rem h-1rem border-1" :style="{'background-color': ThreadRow.fileWriteColor}"></div></td>
          <td>Blocking writes to a File (e.g. <b>FileOutputStream#write</b>)</td>
        </tr>
      </table>
    </p>
    <template #footer>
      <Button label="Close" @click="infoDialogVisible = false" icon="pi pi-times" outlined />
    </template>
  </Dialog>

  <Toast/>
</template>
