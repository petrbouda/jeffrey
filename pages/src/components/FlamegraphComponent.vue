<script setup>
import FlamegraphService from '@/service/FlamegraphService';
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/Flamegraph';
import MessageBus from '@/service/MessageBus';
import ExportFlamegraphService from "@/service/ExportFlamegraphService";

const props = defineProps([
  'primaryProfileId',
  'eventType',
  'useThreadMode',
  'useWeight',
  'scrollableWrapperClass',
  'timeRange'
]);

const toast = useToast();
const searchValue = ref(null);
const matchedValue = ref(null);
let flamegraph = null;

const contextMenu = ref(null);

let primaryProfileId, eventType, timeRange, useThreadMode, useWeight;

const contextMenuItems = [
  {
    label: 'Search in Timeseries',
    icon: 'pi pi-chart-bar',
    command: () => {
      const searchContent = {
        searchValue: flamegraph.getContextFrame().title
      }

      MessageBus.emit(MessageBus.TIMESERIES_SEARCH, searchContent);
    }
  },
  {
    label: 'Search in Flamegraph',
    icon: 'pi pi-align-center',
    command: () => {

      searchValue.value = flamegraph.getContextFrame().title;
      search()
    }
  },
  {
    label: 'Zoom out Flamegraph',
    icon: 'pi pi-search-minus',
    command: () => {
      flamegraph.resetZoom()
    }
  },
  {
    label: 'Export Flamegraph',
    icon: 'pi pi-file-export',
    command: () => {
      exportFlamegraph()
    }
  },
  {
    separator: true
  },
  {
    label: 'Close',
    icon: 'pi pi-times'
  }
]

function onResize({width, height}) {
  let w = document.getElementById("flamegraphCanvas")
      .parentElement.clientWidth

  // minus padding
  if (flamegraph != null) {
    flamegraph.resizeCanvas(w - 50);
  }
}

onMounted(() => {
  updateFlamegraphInfo(props)

  drawFlamegraph(
      props.primaryProfileId,
      props.eventType,
      props.timeRange,
      props.useThreadMode,
      props.useWeight
  )

  MessageBus.on(MessageBus.FLAMEGRAPH_CHANGED, (content) => {
    if (content.resetSearch) {
      resetSearch()
    }

    timeRange = content.timeRange

    drawFlamegraph(
        primaryProfileId,
        eventType,
        timeRange,
        useThreadMode,
        useWeight)
        .then(() => {
          if (searchValue.value != null && !content.resetSearch) {
            search()
          }
        })
  });

  MessageBus.on(MessageBus.FLAMEGRAPH_SEARCH, (content) => {
    searchValue.value = content.searchValue

    if (content.zoomOut) {
      drawFlamegraph(primaryProfileId, eventType, null, useThreadMode, useWeight)
          .then(() => {
            search()
          })
    } else {
      search()
    }
  })

  if (props.scrollableWrapperClass != null) {
    let el = document.getElementsByClassName(props.scrollableWrapperClass)[0]
    el.addEventListener("scroll", (event) => {
      flamegraph.updateScrollPositionY(el.scrollTop)
      flamegraph.removeHighlight()
    });
  }
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.FLAMEGRAPH_CHANGED);
  MessageBus.off(MessageBus.FLAMEGRAPH_SEARCH);
});

function updateFlamegraphInfo(content) {
  primaryProfileId = content.primaryProfileId
  eventType = content.eventType
  timeRange = content.timeRange
  useThreadMode = content.useThreadMode
  useWeight = content.useWeight
}

function drawFlamegraph(primaryProfile, eventType, timeRange, useThreadMode, useWeight) {
  let request
  if (timeRange != null) {
    request = FlamegraphService.generateEventTypeRange(primaryProfile, eventType, timeRange, useThreadMode);
  } else {
    request = FlamegraphService.generateEventTypeComplete(primaryProfile, eventType, useThreadMode);
  }

  return request.then((data) => {
    flamegraph = new Flamegraph(data, 'flamegraphCanvas', contextMenu, eventType, useWeight);
    flamegraph.drawRoot();
  });
}

function search() {
  const matched = flamegraph.search(searchValue.value);
  matchedValue.value = 'Matched: ' + matched + '%';
}

function resetSearch() {
  flamegraph.resetSearch();
  matchedValue.value = null;
  searchValue.value = null;
  MessageBus.emit(MessageBus.TIMESERIES_RESET_SEARCH, true);
}

const exportFlamegraph = () => {
  ExportFlamegraphService.exportFlamegraph(primaryProfileId, useThreadMode, eventType, timeRange)
      .then(() => {
        toast.add({severity: 'success', summary: 'Successful', detail: 'Flamegraph exported', life: 3000});
      });
};
</script>

<template>
  <div v-resize="onResize" style="text-align: left; padding-bottom: 10px;padding-top: 10px">
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
