<script setup>
import FlamegraphService from '@/service/FlamegraphService';
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/Flamegraph';
import MessageBus from '@/service/MessageBus';

const props = defineProps(['primaryProfileId', 'secondaryProfileId', 'flamegraphId', 'flamegraphMode', 'eventType', 'scrollableWrapperClass', 'timeRange']);

const toast = useToast();
const searchValue = ref(null);
const matchedValue = ref(null);
let flamegraph = null;

let primaryProfileId, secondaryProfileId, flamegraphId, flamegraphMode, eventType, timeRange;

function onResize({width, height}) {
  let w = document.getElementById("flamegraphCanvas")
      .parentElement.clientWidth

  // minus padding
  flamegraph.resizeCanvas(w - 50);
}

onMounted(() => {
  updateFlamegraphInfo(props)

  if (flamegraphId != null) {
    FlamegraphService.getById(props.primaryProfileId, props.flamegraphId)
        .then((data) => {
          flamegraph = new Flamegraph(data, 'flamegraphCanvas');
          flamegraph.drawRoot();
        });
  } else {
    drawFlamegraph(
        props.primaryProfileId,
        props.secondaryProfileId,
        props.flamegraphMode,
        props.eventType,
        props.timeRange)
  }

  MessageBus.on(MessageBus.FLAMEGRAPH_CHANGED, (content) => {
    updateFlamegraphInfo(content)

    drawFlamegraph(
        content.primaryProfileId,
        content.secondaryProfileId,
        content.flamegraphMode,
        content.eventType,
        content.timeRange)
  });

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
});

function updateFlamegraphInfo(content) {
  primaryProfileId = content.primaryProfileId
  secondaryProfileId = content.secondaryProfileId
  flamegraphId = content.flamegraphId
  flamegraphMode = content.flamegraphMode
  eventType = content.eventType
  timeRange = content.timeRange
}

function drawFlamegraph(primaryProfile, secondaryProfile, flamegraphMode, eventType, timeRange) {
  let request
  if (flamegraphMode == null || flamegraphMode === Flamegraph.PRIMARY) {
    if (eventType != null && timeRange != null) {
      request = FlamegraphService.generateEventTypeRange(primaryProfile, eventType, timeRange);
    } else if (eventType != null) {
      request = FlamegraphService.generateEventTypeComplete(primaryProfile, eventType);
    } else {
      console.error('EventType needs to be propagated to load the flamegraph: ' + flamegraphMode);
      return;
    }

  } else if (flamegraphMode === Flamegraph.DIFFERENTIAL) {
    if (eventType != null && timeRange != null) {
      request = FlamegraphService.generateEventTypeDiffRange(primaryProfile, secondaryProfile, eventType, timeRange);
    } else if (eventType != null) {
      request = FlamegraphService.generateEventTypeDiffComplete(primaryProfile, secondaryProfile, eventType);
    } else {
      console.error('EventType needs to be propagated to load the flamegraph: ' + flamegraphMode);
      return;
    }
  } else {
    console.error('Cannot resolve a correct type of the requested flamegraph: ' + flamegraphMode);
    return;
  }

  request.then((data) => {
    flamegraph = new Flamegraph(data, 'flamegraphCanvas');
    flamegraph.drawRoot();
  });
}

function search() {
  const matched = flamegraph.search(searchValue.value);
  searchValue.value = null;
  matchedValue.value = 'Matched: ' + matched + '%';
}

const exportFlamegraph = () => {
  let request

  if (flamegraphId != null) {
    request = FlamegraphService.exportById(primaryProfileId, flamegraphId);
  } else if (flamegraphMode == null || flamegraphMode === Flamegraph.PRIMARY) {
    if (eventType != null && timeRange != null) {
      request = FlamegraphService.exportEventTypeRange(primaryProfileId, eventType, timeRange);
    } else if (eventType != null) {
      request = FlamegraphService.exportEventTypeComplete(primaryProfileId, eventType);
    } else {
      console.error('EventType needs to be propagated to load the flamegraph: ' + flamegraphMode);
      return;
    }

  } else if (flamegraphMode === Flamegraph.DIFFERENTIAL) {
    if (eventType != null && timeRange != null) {
      request = FlamegraphService.exportEventTypeDiffRange(primaryProfileId, secondaryProfileId, eventType, timeRange);
    } else if (eventType != null) {
      request = FlamegraphService.exportEventTypeDiffComplete(primaryProfileId, secondaryProfileId, eventType);
    } else {
      console.error('EventType needs to be propagated to load the flamegraph: ' + flamegraphMode);
      return;
    }
  } else {
    console.error('Cannot resolve a correct type of the requested flamegraph: ' + flamegraphMode);
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
      <div class="col-2">
        <Button icon="pi pi-home" class="p-button-filled p-button-info mt-2" title="Reset Zoom"
                @click="flamegraph.resetZoom()"/>&nbsp;
        <Button id="reverse" icon="pi pi-arrows-v" class="p-button-filled p-button-info mt-2"
                @click="flamegraph.reverse()" title="Reverse"/>&nbsp;
        <Button icon="pi pi-file-export" class="p-button-filled p-button-info mt-2"
                @click="exportFlamegraph()" title="Export"/>
      </div>
      <div id="search_output" class="col-2 col-offset-3 relative">
        <Button class="p-button-help mt-2 absolute right-0 font-bold" outlined severity="help"
                @click="flamegraph.resetSearch(); matchedValue = null" v-if="matchedValue != null"
                title="Reset Search">{{ matchedValue }}
        </Button>
      </div>
      <div class="col-5 p-inputgroup" style="float: right">
        <Button class="p-button-info mt-2" label="Search" @click="search()"/>
        <InputText v-model="searchValue" @keydown.enter="search" placeholder="Pattern" class="mt-2"/>
      </div>
    </div>
  </div>

  <canvas id="flamegraphCanvas" style="width: 100%"></canvas>
  <Toast/>
</template>
