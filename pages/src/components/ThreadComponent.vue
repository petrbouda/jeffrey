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
import {onMounted, ref} from 'vue';
import ThreadRow from "@/service/thread/ThreadRow";
import ContextMenu from 'primevue/contextmenu';
import GraphType from "@/service/flamegraphs/GraphType";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import {useRoute} from "vue-router";

const props = defineProps([
  'index',
  'projectId',
  'primaryProfileId',
  'threadCommon',
  'threadData',
]);

const contextMenu = ref(null);

const route = useRoute()

const selectedEventCode = ref(null)

let contextMenuItems = [
  {
    label: 'Wall-Clock',
    icon: 'pi pi-chart-bar',
    command: () => {
      showFlamegraph("profiler.WallClockSample")
    }
  },
  {
    label: 'Thread Park',
    icon: 'pi pi-chart-bar',
    command: () => {
      showFlamegraph("jdk.ThreadPark")
    }
  },
  {
    label: 'Monitor Blocked (Synchronized)',
    icon: 'pi pi-chart-bar',
    command: () => {
      showFlamegraph("jdk.JavaMonitorEnter")
    }
  },
  {
    label: 'Monitor Wait',
    icon: 'pi pi-chart-bar',
    command: () => {
      showFlamegraph("jdk.JavaMonitorWait")
    }
  }
];

const canvasId = ref(`thread-canvas-${props.index}`)

const showFlamegraphDialog = ref(false);

const threadCommon = props.threadCommon
const threadInfo = props.threadData.threadInfo
const threadEvents = props.threadData

let threadRow = null

onMounted(() => {
  threadRow = new ThreadRow(threadCommon.totalDuration, threadEvents, canvasId.value)
  threadRow.draw()

  document.addEventListener("scroll", () => {
    contextMenu.value.hide()
  });
});

function canvasResize() {
  let newWidth = document.getElementById(canvasId.value)
      .clientWidth

  if (threadRow != null) {
    threadRow.resizeCanvas(newWidth)
  }
}

document.addEventListener("scroll", () => {
  if (threadRow != null) {
    threadRow.onWindowScroll()
  }
})

const openContextMenu = (event) => {
  contextMenu.value.show(event)
}

const showFlamegraph = (eventCode) => {
  selectedEventCode.value = eventCode
  showFlamegraphDialog.value = true
}
</script>

<template>
  <div v-resize="() => { canvasResize() }"
       class="grid" style="text-align: left; padding-bottom: 10px;padding-top: 10px">

    <div class="threadRow rounded inline-flex align-items-center">
      <div class="col-3 inline-flex align-items-center">
        <Button class="p-button-info ml-2 p-2" text @click="openContextMenu">
          <span class="material-symbols-outlined text-2xl">local_fire_department</span>
        </Button>
        <span class="text-base ml-2">{{ threadInfo.javaName }}</span>
      </div>
      <div class="col-9 inline-flex align-items-center">
        <div :id="canvasId" style="width: 100%"></div>
      </div>
    </div>
  </div>
  <Toast/>

  <ContextMenu ref="contextMenu" :model="contextMenuItems" style="width:300px"/>

  <!-- Dialog for events that contain StackTrace field -->
  <Dialog class="scrollable" header=" " :pt="{root: 'overflow-hidden'}" v-model:visible="showFlamegraphDialog" modal
          :style="{ width: '95%' }" style="overflow-y: auto">
    <TimeseriesComponent :project-id="route.params.projectId"
                         :primary-profile-id="route.params.profileId"
                         :graph-type="GraphType.PRIMARY"
                         :eventType="selectedEventCode"
                         :use-weight="false"
                         :with-thread-info="props.threadData.threadInfo"/>
    <FlamegraphComponent :project-id="route.params.projectId"
                         :primary-profile-id="route.params.profileId"
                         :with-timeseries="true"
                         :eventType="selectedEventCode"
                         :use-weight="false"
                         :use-thread-mode="true"
                         scrollableWrapperClass="p-dialog-content"
                         :export-enabled="false"
                         :graph-type="GraphType.PRIMARY"
                         :with-thread-info="props.threadData.threadInfo"
                         :generated="false"/>
  </Dialog>
</template>

<style>
.threadRow {
  width: 100%;
  border-radius: 0.5rem;
}

.threadRow:hover {
  background-color: rgb(191, 219, 254, 0.2);
}
</style>
