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
import {onMounted, ref} from 'vue';
import Flamegraph from '@/services/flamegraphs/Flamegraph';
import FlameUtils from "@/services/flamegraphs/FlameUtils";
import Utils from "@/services/Utils";
import FlamegraphContextMenu from "@/services/flamegraphs/FlamegraphContextMenu";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import FlamegraphData from "@/services/flamegraphs/model/FlamegraphData";
import GuardMatched from "@/services/flamegraphs/model/guard/GuardMatched";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import GraphComponents from "@/services/flamegraphs/model/GraphComponents";
import ToastService from "@/services/ToastService.ts";

const props = defineProps<{
  withTimeseries: boolean
  withSearch: string | null
  useWeight: boolean
  useGuardian: any | null
  saveEnabled: boolean | null
  scrollableWrapperClass: string | null
  flamegraphTooltip: FlamegraphTooltip
  graphUpdater: GraphUpdater
}>()
const searchValue = ref<string | null>(null);
const searchMatched = ref<string | null>(null);
const guardMatched = ref<GuardMatched | null>(null);

// Variables for Save Dialog
const flamegraphName = ref<string | null>(null);
const saveDialog = ref(false);
let currentTimeRange: TimeRange | null;
// ------------------------

let flamegraph: Flamegraph

const contextMenu = ref<ContextMenu>();

let contextMenuItems = FlamegraphContextMenu.resolve(
    () => props.graphUpdater.updateWithSearch(flamegraph!.getContextFrame()!.title),
    () => search(flamegraph!.getContextFrame()!.title),
    () => flamegraph.resetZoom())

const preloaderActive = ref(false)

onMounted(() => {
  if (props.useGuardian != null && props.useGuardian.matched != null) {
    guardMatched.value = props.useGuardian.matched
  }

  let flamegraphUpdate = (data: FlamegraphData, timeRange: TimeRange | null) => {
    currentTimeRange = timeRange;
    flamegraph = new Flamegraph(data, 'flamegraphCanvas', props.flamegraphTooltip, contextMenu.value as ContextMenu, props.useWeight);
    flamegraph.drawRoot();
    FlameUtils.registerAdjustableScrollableComponent(flamegraph, props.scrollableWrapperClass);
  };

  let zoomUpdate = (data: FlamegraphData, timeRange: TimeRange | null) => {
    flamegraphUpdate(data, timeRange)
    search(searchValue.value)
  }

  props.graphUpdater.registerFlamegraphCallbacks(
      () => preloaderActive.value = true,
      () => preloaderActive.value = false,
      flamegraphUpdate,
      search,
      () => {
        flamegraph.resetSearch();
        searchMatched.value = null;
        searchValue.value = null;
      },
      zoomUpdate,
      zoomUpdate
  )
});

function search(value: string | null) {
  if (Utils.isNotBlank(value)) {
    searchValue.value = value!.trim()
    searchMatched.value = flamegraph.search(searchValue.value);
  } else {
    searchValue.value = null
  }
}

function resetSearch() {
  props.graphUpdater.resetSearch();
}

const openSaveDialog = () => {
  flamegraphName.value = props.flamegraphTooltip.eventType + "-" + new Date().toISOString()
  saveDialog.value = true
}

const saveFlamegraph = () => {
  const components = props.withTimeseries ? GraphComponents.BOTH : GraphComponents.FLAMEGRAPH_ONLY
  props.graphUpdater.flamegraphClient().save(components, flamegraphName.value!!, currentTimeRange)
      .then(() => {
        saveDialog.value = false
        flamegraphName.value = null
        ToastService.info( "Flamegraph saved successfully")
      });
};
</script>

<template>
  <div v-resize="() => { FlameUtils.canvasResize(flamegraph, 50) }"
       style="text-align: left; padding-bottom: 10px;padding-top: 10px">
    <div class="row">
      <div class="col-5 d-flex">
        <button class="btn btn-outline-secondary mt-2 me-2" title="Reset Zoom" @click="flamegraph.resetZoom()">
          <i class="bi bi-arrows-angle-expand"></i> Reset Zoom
        </button>
        <button class="btn btn-outline-primary mt-2 me-2" title="Save" @click="openSaveDialog"
                v-if="Utils.parseBoolean(props.saveEnabled)">
          <i class="bi bi-download"></i> Save
        </button>
        <button class="btn btn-outline-info mt-2 disabled"
                :style="{'color': guardMatched?.color}"
                v-if="guardMatched != null">
          {{ `Guard Matched: ` + guardMatched.percent + `%` }}
        </button>
      </div>
      <div id="search_output" class="position-relative" :class="preloaderActive ? 'col-1' : 'col-2'">
        <button class="btn btn-outline-info mt-2 position-absolute end-0" 
                @click="resetSearch()" v-if="searchMatched != null"
                title="Reset Search">
          {{ `Matched: ` + searchMatched + `%` }}
        </button>
      </div>

      <div class="col-1 d-flex" v-if="preloaderActive">
        <div id="preloader" class="d-flex justify-content-end align-items-center w-100" style="padding: 0;">
          <div class="spinner-border spinner-border-sm text-primary me-4" style="height: 20px; width: 20px" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
      </div>

      <div class="col-5 d-flex" style="float: right">
        <div class="input-group mt-2">
          <button class="btn btn-primary" @click="search(searchValue)">Search</button>
          <input type="text" class="form-control" v-model="searchValue" @keydown.enter="search(searchValue)"
                 placeholder="Full-text search in Flamegraph">
        </div>
      </div>
    </div>
  </div>

  <canvas id="flamegraphCanvas" style="width: 100%"></canvas>

  <div class="card p-2 border-1 bg-gray-50" style="visibility:hidden; position:absolute" id="flamegraphTooltip"></div>

  <ContextMenu ref="contextMenu" :model="contextMenuItems" @hide="flamegraph.closeContextMenu()" style="width:250px"/>

  <!-- ------------------------------------------------ -->
  <!-- Dialog for saving the flamegraph with timeseries -->
  <!-- ------------------------------------------------ -->
  <div class="modal fade" :class="{ 'show': saveDialog }" tabindex="-1" :style="{ display: saveDialog ? 'block' : 'none' }">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Save the current Flamegraph</h5>
          <button type="button" class="btn-close" @click="saveDialog = false"></button>
        </div>
        <div class="modal-body">
          <div class="mb-3">
            <input class="form-control" id="filename" v-model="flamegraphName" type="text">
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-primary" @click="saveFlamegraph"
                  :disabled="flamegraphName == null || flamegraphName.trim().length === 0">Save</button>
          <button type="button" class="btn btn-secondary" @click="saveDialog = false">Cancel</button>
        </div>
      </div>
    </div>
  </div>
  <div class="modal-backdrop fade show" v-if="saveDialog"></div>
</template>
