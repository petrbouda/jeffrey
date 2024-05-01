<script setup>
import FlamegraphService from '@/service/FlamegraphService';
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/Flamegraph';
import MessageBus from '@/service/MessageBus';
import FlamegraphTooltips from "@/service/FlamegraphTooltips";

const props = defineProps(['primaryProfileId', 'secondaryProfileId', 'flamegraphId', 'graphMode', 'eventType', 'scrollableWrapperClass', 'timeRange']);

const toast = useToast();
const searchValue = ref(null);
const matchedValue = ref(null);
const threadModeEnabled = ref(false)
let flamegraph = null;

let primaryProfileId, secondaryProfileId, flamegraphId, graphMode, eventType, timeRange;
const contextMenu = ref(null);
const contextMenuItems = ref(null)

const contextMenuItemsForFlamegraph = [
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
  // {
  //   label: 'Filter out stacks',
  //   icon: 'pi pi-filter'
  // },
  // {
  //   label: 'List in EventViewer',
  //   icon: 'pi pi-list'
  // },
  {
    separator: true
  },
  {
    label: 'Close',
    icon: 'pi pi-times'
  }
]

const contextMenuItemsForDiffgraph = [
  {
    label: 'Search in Flamegraph',
    icon: 'pi pi-align-center',
    command: () => {
      searchValue.value = flamegraph.getContextFrame().title;
      search()
    }
  },
  {
    label: 'Filter out stacks',
    icon: 'pi pi-filter'
  },
  {
    label: 'List in EventViewer',
    icon: 'pi pi-list'
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

  if (flamegraphId != null) {
    FlamegraphService.getById(props.primaryProfileId, props.flamegraphId)
        .then((data) => {
          flamegraph = new Flamegraph(data, 'flamegraphCanvas', contextMenu, FlamegraphTooltips.resolveType(eventType, graphMode));
          flamegraph.drawRoot();
        });
  } else {
    drawFlamegraph(
        props.primaryProfileId,
        props.secondaryProfileId,
        props.graphMode,
        props.eventType,
        props.timeRange)
  }

  MessageBus.on(MessageBus.FLAMEGRAPH_CHANGED, (content) => {
    if (content.resetSearch) {
      resetSearch()
    }

    updateFlamegraphInfo(content)

    drawFlamegraph(
        content.primaryProfileId,
        content.secondaryProfileId,
        content.graphMode,
        content.eventType,
        content.timeRange)
        .then(() => {
          if (searchValue.value != null && !content.resetSearch) {
            search()
          }
        })
  });

  MessageBus.on(MessageBus.FLAMEGRAPH_SEARCH, (content) => {
    searchValue.value = content.searchValue

    if (content.zoomOut) {
      drawFlamegraph(primaryProfileId, null, graphMode, eventType, null)
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
  secondaryProfileId = content.secondaryProfileId
  flamegraphId = content.flamegraphId
  graphMode = content.graphMode
  eventType = content.eventType
  timeRange = content.timeRange
}

const changeThreadMode = () => {
  timeRange = null
  resetSearch()
  drawFlamegraph(primaryProfileId, secondaryProfileId, graphMode, eventType, timeRange)
}

function drawFlamegraph(primaryProfile, secondaryProfile, graphMode, eventType, timeRange) {
  if (graphMode === Flamegraph.DIFFERENTIAL) {
    contextMenuItems.value = contextMenuItemsForDiffgraph
  } else {
    contextMenuItems.value = contextMenuItemsForFlamegraph
  }

  let request
  if (graphMode == null || graphMode === Flamegraph.PRIMARY) {
    if (eventType != null && timeRange != null) {
      request = FlamegraphService.generateEventTypeRange(primaryProfile, eventType, timeRange, threadModeEnabled.value);
    } else if (eventType != null) {
      request = FlamegraphService.generateEventTypeComplete(primaryProfile, eventType, threadModeEnabled.value);
    } else {
      console.error('EventType needs to be propagated to load the flamegraph: ' + graphMode);
      return;
    }

  } else if (graphMode === Flamegraph.DIFFERENTIAL) {
    if (eventType != null && timeRange != null) {
      request = FlamegraphService.generateEventTypeDiffRange(primaryProfile, secondaryProfile, eventType, timeRange);
    } else if (eventType != null) {
      request = FlamegraphService.generateEventTypeDiffComplete(primaryProfile, secondaryProfile, eventType);
    } else {
      console.error('EventType needs to be propagated to load the flamegraph: ' + graphMode);
      return;
    }
  } else {
    console.error('Cannot resolve a correct type of the requested flamegraph: ' + graphMode);
    return;
  }

  return request.then((data) => {
    flamegraph = new Flamegraph(data, 'flamegraphCanvas', contextMenu, FlamegraphTooltips.resolveType(eventType, graphMode));
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
  let request

  if (flamegraphId != null) {
    request = FlamegraphService.exportById(primaryProfileId, flamegraphId);
  } else if (graphMode == null || graphMode === Flamegraph.PRIMARY) {
    if (eventType != null && timeRange != null) {
      request = FlamegraphService.exportEventTypeRange(primaryProfileId, eventType, timeRange, threadModeEnabled.value);
    } else if (eventType != null) {
      request = FlamegraphService.exportEventTypeComplete(primaryProfileId, eventType, threadModeEnabled.value);
    } else {
      console.error('EventType needs to be propagated to load the flamegraph: ' + graphMode);
      return;
    }

  } else if (graphMode === Flamegraph.DIFFERENTIAL) {
    if (eventType != null && timeRange != null) {
      request = FlamegraphService.exportEventTypeDiffRange(primaryProfileId, secondaryProfileId, eventType, timeRange);
    } else if (eventType != null) {
      request = FlamegraphService.exportEventTypeDiffComplete(primaryProfileId, secondaryProfileId, eventType);
    } else {
      console.error('EventType needs to be propagated to load the flamegraph: ' + graphMode);
      return;
    }
  } else {
    console.error('Cannot resolve a correct type of the requested flamegraph: ' + graphMode);
    return;
  }

  request.then(() => {
    toast.add({severity: 'success', summary: 'Successful', detail: 'Flamegraph exported', life: 3000});
  });
};
</script>

<template>
  <!--  resizes Canvas according to parent component to avoid sending message from parent to child component  -->
  <div v-resize="onResize" style="text-align: left; padding-bottom: 10px;padding-top: 10px">
    <div class="grid">
      <div class="col-3">
        <Button icon="pi pi-home" class="p-button-filled p-button-info mt-2" title="Reset Zoom"
                @click="flamegraph.resetZoom()"/>
        <Button id="reverse" icon="pi pi-arrows-v" class="p-button-filled p-button-info mt-2 ml-2"
                @click="flamegraph.reverse()" title="Reverse"/>
        <Button icon="pi pi-file-export" class="p-button-filled p-button-info mt-2 ml-2"
                @click="exportFlamegraph()" title="Export"/>
        <ToggleButton :disabled="graphMode === Flamegraph.DIFFERENTIAL" @click="changeThreadMode" v-model="threadModeEnabled" onLabel="Thread Mode" offLabel="Thread Mode" class="mt-2 ml-2" />
      </div>
      <div id="search_output" class="col-2 col-offset-2 relative">
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
