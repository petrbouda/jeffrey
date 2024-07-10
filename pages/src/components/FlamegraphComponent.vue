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
import FlamegraphService from '@/service/flamegraphs/FlamegraphService';
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/flamegraphs/Flamegraph';
import MessageBus from '@/service/MessageBus';
import FlameUtils from "@/service/flamegraphs/FlameUtils";
import Utils from "@/service/Utils";
import FlamegraphContextMenu from "@/service/flamegraphs/FlamegraphContextMenu";
import GraphTypeResolver from "@/service/replace/GraphTypeResolver";
import ToastUtils from "@/service/ToastUtils";

const props = defineProps([
  'primaryProfileId',
  'secondaryProfileId',
  'eventType',
  'useThreadMode',
  'timeRange',
  'useWeight',
  'scrollableWrapperClass',
  'exportEnabled',
  'graphType',
  'generated'
]);

const toast = useToast();

const searchValue = ref(null);
const matchedValue = ref(null);

let flamegraph = null;

const contextMenu = ref(null);

let timeRange = props.timeRange

const resolvedGraphType = GraphTypeResolver.resolve(props.graphType, props.generated);

//
// Creates a context menu after clicking using right-button on flamegraph's frame
// There are some specific behavior when the flamegraph is PRIMARY/DIFFERENTIAL/GENERATED
//
let contextMenuItems = FlamegraphContextMenu.resolve(
    resolvedGraphType,
    props.generated,
    () => MessageBus.emit(MessageBus.TIMESERIES_SEARCH, flamegraph.getContextFrame().title),
    () => search(flamegraph.getContextFrame().title),
    () => flamegraph.resetZoom())

const flamegraphService = new FlamegraphService(
    props.primaryProfileId,
    props.secondaryProfileId,
    props.eventType,
    props.useThreadMode,
    props.useWeight,
    resolvedGraphType,
    props.generated
)

onMounted(() => {
  drawFlamegraph()

  MessageBus.on(MessageBus.FLAMEGRAPH_CHANGED, (content) => {
    timeRange = content.timeRange

    drawFlamegraph()
        .then(() => {
          if (searchValue.value != null && !content.resetSearch) {
            search(searchValue.value)
          }
        })
  });

  MessageBus.on(MessageBus.FLAMEGRAPH_SEARCH, (content) => {
    drawFlamegraph()
        .then(() => search(content.searchValue))
  })
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.FLAMEGRAPH_CHANGED);
  MessageBus.off(MessageBus.FLAMEGRAPH_SEARCH);
});

function drawFlamegraph() {
  return flamegraphService.generate(timeRange)
      .then((data) => {
        flamegraph = new Flamegraph(data, 'flamegraphCanvas', contextMenu, props.eventType, props.useWeight, resolvedGraphType);
        flamegraph.drawRoot();
        FlameUtils.registerAdjustableScrollableComponent(flamegraph, props.scrollableWrapperClass)
      });
}

function search(value) {
  searchValue.value = value
  const matched = flamegraph.search(searchValue.value);
  matchedValue.value = `Matched: ${matched}%`;
}

function resetSearch() {
  flamegraph.resetSearch();
  matchedValue.value = null;
  searchValue.value = null;
  MessageBus.emit(MessageBus.TIMESERIES_RESET_SEARCH, true);
}

const exportFlamegraph = () => {
  flamegraphService.export(timeRange)
      .then(() => ToastUtils.exported(toast));
}
</script>

<template>
  <div v-resize="() => { FlameUtils.canvasResize(flamegraph) }"
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
      </div>
      <div id="search_output" class="col-2 relative">
        <Button class="p-button-help mt-2 absolute right-0 font-bold" outlined severity="help"
                @click="resetSearch()" v-if="matchedValue != null"
                title="Reset Search">{{ matchedValue }}
        </Button>
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
