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
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/flamegraphs/Flamegraph';
import MessageBus from '@/service/MessageBus';
import FlameUtils from "@/service/flamegraphs/FlameUtils";
import Utils from "@/service/Utils";
import FlamegraphContextMenu from "@/service/flamegraphs/FlamegraphContextMenu";
import FlamegraphDataProvider from "@/service/flamegraphs/service/FlamegraphDataProvider";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import ContextMenu from "primevue/contextmenu";
import ToastUtils from "@/service/ToastUtils";

const props = defineProps<{
  withTimeseries: boolean | null
  withSearch: string | null
  useWeight: boolean
  useGuardian: any | null
  timeRange: any | null
  exportEnabled: boolean | null
  scrollableWrapperClass: string | null
  flamegraphTooltip: FlamegraphTooltip
  flamegraphDataProvider: FlamegraphDataProvider
}>()

const toast = useToast();

const searchValue = ref<string | null>(null);
const searchMatched = ref<string | null>(null);
const guardMatched = ref(null);

let flamegraph: Flamegraph

const contextMenu = ref<ContextMenu>();

let timeRange = props.timeRange

// These values can be replaced by CLI tool
// const resolvedWeight = ReplaceResolver.resolveWeight(props.generated, props.useWeight)
// const resolvedSearch = ReplaceResolver.resolveSearch(props.generated, props.withSearch)
// const resolvedWithTimeseries = ReplaceResolver.resolveWithTimeseries(props.generated, props.withTimeseries)

//
// Creates a context menu after clicking using right-button on flamegraph's frame
// There are some specific behavior when the flamegraph is PRIMARY/DIFFERENTIAL/GENERATED
//

const NOOP_FUNCTION = () => {
}

let contextMenuItems = FlamegraphContextMenu.resolve(
    props.withTimeseries ? () => MessageBus.emit(MessageBus.TIMESERIES_SEARCH, flamegraph!.getContextFrame()!.title) : NOOP_FUNCTION,
    () => search(flamegraph!.getContextFrame()!.title),
    () => flamegraph.resetZoom())

const preloaderActive = ref(false)

onMounted(() => {
  if (props.useGuardian != null && props.useGuardian.matched != null) {
    guardMatched.value = props.useGuardian.matched
  }

  fetchAndDrawFlamegraph()
      .then(() => {
        // Automatically search the value - used particularly in CLI tool
        if (props.withSearch != null) {
          search(props.withSearch)
        }
      })

  MessageBus.on(MessageBus.FLAMEGRAPH_CHANGED, (content: any) => {
    timeRange = content.timeRange

    fetchAndDrawFlamegraph().then(() => {
      if (searchValue.value != null && !content.resetSearch) {
        search(searchValue.value)
      }
    })
  });

  MessageBus.on(MessageBus.FLAMEGRAPH_SEARCH, (content: any) => {
    fetchAndDrawFlamegraph()
        .then(() => search(content.searchValue))
  })
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.FLAMEGRAPH_CHANGED);
  MessageBus.off(MessageBus.FLAMEGRAPH_SEARCH);
});

function fetchAndDrawFlamegraph() {
  preloaderActive.value = true
  return props.flamegraphDataProvider.provide(timeRange)
      .then((data) => {
        flamegraph = new Flamegraph(data, 'flamegraphCanvas', props.flamegraphTooltip, contextMenu.value as ContextMenu, props.useWeight);
        flamegraph.drawRoot();
        FlameUtils.registerAdjustableScrollableComponent(flamegraph, props.scrollableWrapperClass)
        preloaderActive.value = false
      });
}

function search(value: string | null) {
  if (Utils.isNotBlank(value)) {
    searchValue.value = value!.trim()
    searchMatched.value = flamegraph.search(searchValue.value);
  } else {
    searchValue.value = null
  }
}

function resetSearch() {
  flamegraph.resetSearch();
  searchMatched.value = null;
  searchValue.value = null;
  MessageBus.emit(MessageBus.TIMESERIES_RESET_SEARCH, true);
}

const exportFlamegraph = () => {
  props.flamegraphDataProvider.export(timeRange)
      .then(() => ToastUtils.exported(toast));
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
        <Button class="p-button-filled p-button-info mt-2 ml-2" title="Export" @click="exportFlamegraph()"
                v-if="Utils.parseBoolean(props.exportEnabled)">
          <span class="material-symbols-outlined text-xl">export_notes</span>
        </Button>
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
