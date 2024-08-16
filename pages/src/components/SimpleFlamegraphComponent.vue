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
import {onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/flamegraphs/Flamegraph';
import FlameUtils from "@/service/flamegraphs/FlameUtils";
import GraphType from "@/service/flamegraphs/GraphType";
import ToastUtils from "@/service/ToastUtils";
import FlamegraphContextMenu from "@/service/flamegraphs/FlamegraphContextMenu";
import MessageBus from "@/service/MessageBus";

const props = defineProps([
  'profileId',
  'flamegraphId'
]);

const toast = useToast();
const searchValue = ref(null);
const matchedValue = ref(null);
let flamegraph = null;

const contextMenu = ref(null);

const contextMenuItems =
    FlamegraphContextMenu.resolve(
        false,
        null,
        () => search(flamegraph.getContextFrame().title),
        () => flamegraph.resetZoom())

onMounted(() => {
  FlamegraphService.getById(props.profileId, props.flamegraphId)
      .then((data) => {
        flamegraph = new Flamegraph(
            data.content, 'flamegraphCanvas',
            contextMenu, data.eventType, data.useWeight, GraphType.isDifferential(data.graphType));
        flamegraph.drawRoot();
      });
});

function search() {
  const matched = flamegraph.search(searchValue.value);
  matchedValue.value = `Matched: ${matched}%`;
}

function resetSearch() {
  flamegraph.resetSearch();
  matchedValue.value = null;
  searchValue.value = null;
}

const exportFlamegraph = () => {
  FlamegraphService.exportById(props.profileId, props.flamegraphId)
      .then(() => ToastUtils.exported(toast));
};
</script>

<template>
  <div v-resize="() => { FlameUtils.canvasResize(flamegraph, 50) }"
       style="text-align: left; padding-bottom: 10px;padding-top: 10px">
    <div class="grid">
      <div class="col-5 flex flex-row">
        <Button icon="pi pi-home" class="p-button-filled p-button-info mt-2" title="Reset Zoom"
                @click="flamegraph.resetZoom()"/>
        <Button icon="pi pi-file-export" class="p-button-filled p-button-info mt-2 ml-2"
                @click="exportFlamegraph()" title="Export"/>
      </div>
      <div id="search_output" class="col-2 relative">
        <Button class="p-button-help mt-2 absolute right-0 font-bold" outlined severity="help"
                @click="resetSearch()" v-if="matchedValue != null"
                title="Reset Search">{{ matchedValue }}
        </Button>
      </div>
      <div class="col-5 p-inputgroup" style="float: right">
        <Button class="p-button-info mt-2" label="Search" @click="search()"/>
        <InputText v-model="searchValue" @keydown.enter="search" placeholder="Full-text search in Flamegraph"
                   class="mt-2"/>
      </div>
    </div>
  </div>

  <canvas id="flamegraphCanvas" style="width: 100%"></canvas>

  <div class="card p-2 border-1 bg-gray-50" style="visibility:hidden; position:absolute" id="flamegraphTooltip"></div>

  <ContextMenu ref="contextMenu" :model="contextMenuItems" @hide="flamegraph.closeContextMenu()" style="width:250px"/>
  <Toast/>
</template>
