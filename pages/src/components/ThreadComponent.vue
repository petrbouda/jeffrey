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

const props = defineProps([
  'index',
  'projectId',
  'primaryProfileId',
  'threadCommon',
  'threadData',
]);

const canvasId = ref(`thread-canvas-${props.index}`)

const threadCommon = props.threadCommon
const threadInfo = props.threadData.threadInfo
const threadEvents = props.threadData

let threadRow = null

onMounted(() => {
  threadRow = new ThreadRow(threadCommon.durationInMillis, threadEvents, canvasId.value)
  threadRow.draw()
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
</script>

<template>
  <div v-resize="() => { canvasResize() }"
       class="grid" style="text-align: left; padding-bottom: 10px;padding-top: 10px">
    <div class="col-3">
      <span class="text-sm">{{ threadInfo.javaName }}</span>
    </div>

    <div class="col-9">
      <canvas :id="canvasId" style="width: 100%; height: 20px"></canvas>
    </div>
  </div>
  <Toast/>
</template>
