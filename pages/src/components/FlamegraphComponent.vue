<script setup>
import FlamegraphService from '@/service/flamegraphs/FlamegraphService';
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/flamegraphs/Flamegraph';
import MessageBus from '@/service/MessageBus';
import FlameUtils from "@/service/flamegraphs/FlameUtils";

const props = defineProps([
  'primaryProfileId',
  'eventType',
  'useThreadMode',
  'useWeight',
  'scrollableWrapperClass',
]);

const toast = useToast();

const searchValue = ref(null);
const matchedValue = ref(null);

let flamegraph = null;

const contextMenu = ref(null);

let timeRange = null

const contextMenuItems =
    FlameUtils.contextMenuItems(
        () => {
          MessageBus.emit(MessageBus.TIMESERIES_SEARCH, flamegraph.getContextFrame().title)
        },
        () => {
          searchValue.value = flamegraph.getContextFrame().title
          search()
        },
        () => {
          flamegraph.resetZoom()
        }
    )

onMounted(() => {
  drawFlamegraph()

  MessageBus.on(MessageBus.FLAMEGRAPH_CHANGED, (content) => {
    if (content.resetSearch) {
      resetSearch()
    }

    timeRange = content.timeRange

    drawFlamegraph()
        .then(() => {
          if (searchValue.value != null && !content.resetSearch) {
            search()
          }
        })
  });

  MessageBus.on(MessageBus.FLAMEGRAPH_SEARCH, (content) => {
    searchValue.value = content.searchValue

    if (content.zoomOut) {
      drawFlamegraph()
          .then(() => {
            search()
          })
    } else {
      search()
    }
  })

  FlameUtils.registerAdjustableScrollableComponent(flamegraph, props.scrollableWrapperClass)
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.FLAMEGRAPH_CHANGED);
  MessageBus.off(MessageBus.FLAMEGRAPH_SEARCH);
});

function drawFlamegraph() {
  return FlamegraphService.generate(props.primaryProfileId, props.eventType, props.useThreadMode, timeRange)
      .then((data) => {
        console.log(data)
        flamegraph = new Flamegraph(data, 'flamegraphCanvas', contextMenu, props.eventType, props.useWeight);
        flamegraph.drawRoot();
      });
}

function search() {
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
  FlamegraphService.export(props.primaryProfileId, props.eventType, timeRange, props.useThreadMode)
      .then(FlameUtils.toastExported(toast));
}
</script>

<template>
  <div v-resize="() => { FlameUtils.canvasResize(flamegraph) }"
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
