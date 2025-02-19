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
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/flamegraphs/Flamegraph';
import FlameUtils from "@/service/flamegraphs/FlameUtils";
import Utils from "@/service/Utils";
import FlamegraphContextMenu from "@/service/flamegraphs/FlamegraphContextMenu";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import ContextMenu from "primevue/contextmenu";
import GraphUpdater from "@/service/flamegraphs/updater/GraphUpdater";
import FlamegraphData from "@/service/flamegraphs/model/FlamegraphData";

const props = defineProps<{
  withTimeseries: boolean | null
  withSearch: string | null
  useWeight: boolean
  useGuardian: any | null
  exportEnabled: boolean | null
  scrollableWrapperClass: string | null
  flamegraphTooltip: FlamegraphTooltip
  graphUpdater: GraphUpdater
}>()

const toast = useToast();

const searchValue = ref<string | null>(null);
const searchMatched = ref<string | null>(null);
const guardMatched = ref(null);

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

  let flamegraphUpdate = (data: FlamegraphData) => {
    flamegraph = new Flamegraph(data, 'flamegraphCanvas', props.flamegraphTooltip, contextMenu.value as ContextMenu, props.useWeight);
    flamegraph.drawRoot();
    FlameUtils.registerAdjustableScrollableComponent(flamegraph, props.scrollableWrapperClass);
  };

  let zoomUpdate = (data: FlamegraphData) => {
    flamegraphUpdate(data)
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
</script>

<template>
  <div v-resize="() => { FlameUtils.canvasResize(flamegraph, 50) }"
       style="text-align: left; padding-bottom: 10px;padding-top: 10px">
    <div class="grid">
      <div class="col-5 flex flex-row">
        <Button class="p-button-filled p-button-info mt-2" title="Reset Zoom" @click="flamegraph.resetZoom()">
          <span class="material-symbols-outlined text-xl">home</span>
        </Button>
        <!--        <Button class="p-button-filled p-button-info mt-2 ml-2" title="Export" @click="exportFlamegraph()"-->
        <!--                v-if="Utils.parseBoolean(props.exportEnabled)">-->
        <!--          <span class="material-symbols-outlined text-xl">export_notes</span>-->
        <!--        </Button>-->
        <Button class="p-button-help mt-2 ml-2 cursor-auto pointer-events-none font-bold"
                style="filter: brightness(80%)"
                :style="{'color': guardMatched.color}" outlined severity="help"
                v-if="guardMatched != null">{{ `Guard Matched: ` + guardMatched.percent + `%` }}
        </Button>
      </div>
      <div id="search_output" class="relative" :class="preloaderActive ? 'col-1' : 'col-2'">
        <Button class="p-button-help mt-2 absolute right-0 font-bold" outlined severity="help"
                @click="resetSearch()" v-if="searchMatched != null"
                title="Reset Search">{{ `Matched: ` + searchMatched + `%` }}
        </Button>
      </div>

      <div class="flex col-1" v-if="preloaderActive">
        <div id="preloader" class="layout-preloader-container w-full"
             style="padding: 0; align-items: center; justify-content: end">
          <div class="layout-preloader mr-4" style="height: 20px; width: 20px">
            <span></span>
          </div>
        </div>
      </div>

      <div class="col-5 p-inputgroup" style="float: right">
        <Button class="p-button-info mt-2" label="Search" @click="search(searchValue)"/>
        <InputText v-model="searchValue" @keydown.enter="search(searchValue)"
                   placeholder="Full-text search in Flamegraph"
                   class="mt-2"/>
      </div>
    </div>
  </div>

  <canvas id="flamegraphCanvas" style="width: 100%"></canvas>

  <div class="card p-2 border-1 bg-gray-50" style="visibility:hidden; position:absolute" id="flamegraphTooltip"></div>

  <ContextMenu ref="contextMenu" :model="contextMenuItems" @hide="flamegraph.closeContextMenu()" style="width:250px"/>
  <Toast/>
</template>
