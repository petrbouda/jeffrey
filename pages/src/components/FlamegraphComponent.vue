<script setup>
import FlamegraphService from '@/service/FlamegraphService';
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/Flamegraph';
import MessageBus from '@/service/MessageBus';

const props = defineProps(['primaryProfileId', 'secondaryProfileId', 'flamegraphId', 'flamegraphType', 'eventType', 'scrollableWrapperClass', 'timeRange']);

const toast = useToast();
const searchValue = ref(null);
const matchedValue = ref(null);
let flamegraph = null;

let primaryProfileId, secondaryProfileId, flamegraphId, flamegraphType, eventType, scrollableWrapperClass, timeRange;

function onResize({width, height}) {
  let w = document.getElementById("flamegraphCanvas")
      .parentElement.clientWidth

  // minus padding
  flamegraph.resizeCanvas(w - 50);
}

onMounted(() => {
  primaryProfileId = props.primaryProfileId
  secondaryProfileId = props.secondaryProfileId
  flamegraphId = props.flamegraphId
  flamegraphType = props.flamegraphType
  eventType = props.eventType
  scrollableWrapperClass = props.scrollableWrapperClass
  timeRange = props.timeRange

  let request = null;

  if (flamegraphId != null) {
    request = FlamegraphService.getById(primaryProfileId, flamegraphId);
  } else {
    if (flamegraphType == null || flamegraphType === Flamegraph.PRIMARY) {
      if (eventType != null && timeRange != null) {
        request = FlamegraphService.generateEventTypeRange(primaryProfileId, eventType, timeRange);
      } else if (eventType != null) {
        request = FlamegraphService.generateEventTypeComplete(primaryProfileId, eventType);
      } else {
        console.error('EventType needs to be propagated to load the flamegraph: ' + flamegraphType);
        return;
      }

    } else if (flamegraphType === Flamegraph.DIFFERENTIAL) {
      if (eventType != null && timeRange != null) {
        request = FlamegraphService.generateEventTypeDiffRange(primaryProfileId, secondaryProfileId, eventType, timeRange);
      } else if (eventType != null) {
        request = FlamegraphService.generateEventTypeDiffComplete(primaryProfileId, secondaryProfileId, eventType);
      } else {
        console.error('EventType needs to be propagated to load the flamegraph: ' + flamegraphType);
        return;
      }
    } else {
      console.error('Cannot resolve a correct type of the requested flamegraph: ' + flamegraphType);
      return;
    }
  }

  request.then((data) => {
    flamegraph = new Flamegraph(data, 'flamegraphCanvas');
    flamegraph.drawRoot();
  });

  MessageBus.on(MessageBus.FLAMEGRAPH_EVENT_TYPE_CHANGED, (type) => {
    eventType = type;

    if (flamegraphType === Flamegraph.PRIMARY) {
      FlamegraphService.generateEventTypeComplete(primaryProfileId, eventType)
          .then((data) => {
            flamegraph = new Flamegraph(data, 'flamegraphCanvas');
            flamegraph.drawRoot();
          });
    } else if (flamegraphType === Flamegraph.DIFFERENTIAL) {
      FlamegraphService.generateEventTypeDiffComplete(primaryProfileId, secondaryProfileId, eventType)
          .then((data) => {
            flamegraph = new Flamegraph(data, 'flamegraphCanvas');
            flamegraph.drawRoot();
          });
    } else {
      console.error('Cannot resolve a correct type of the requested flamegraph');
    }
  });

  MessageBus.on(MessageBus.FLAMEGRAPH_TIMESERIES_RANGE_CHANGED, (timeRange) => {
    if (flamegraphType === "regular") {
      FlamegraphService.generateEventTypeRange(primaryProfileId, eventType, timeRange)
          .then((data) => {
            flamegraph = new Flamegraph(data, 'flamegraphCanvas');
            flamegraph.drawRoot();
          });
    } else if (flamegraphType === "differential") {
      FlamegraphService.generateEventTypeDiffRange(primaryProfileId, secondaryProfileId, eventType, timeRange)
          .then((data) => {
            flamegraph = new Flamegraph(data, 'flamegraphCanvas');
            flamegraph.drawRoot();
          });
    } else {
      console.error('Cannot resolve a correct type of the requested flamegraph');
    }
  });

  if (scrollableWrapperClass != null) {
    let el = document.getElementsByClassName(scrollableWrapperClass)[0]
    el.addEventListener("scroll", (event) => {
      flamegraph.updateScrollPositionY(el.scrollTop)
      flamegraph.removeHighlight()
    });
  }
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.FLAMEGRAPH_EVENT_TYPE_CHANGED);
  MessageBus.off(MessageBus.FLAMEGRAPH_TIMESERIES_RANGE_CHANGED);
});

function search() {
  const matched = flamegraph.search(searchValue.value);
  searchValue.value = null;
  matchedValue.value = 'Matched: ' + matched + '%';
}

const exportFlamegraph = () => {
  let request = null;
  if (flamegraphId != null) {
    request = FlamegraphService.exportById(primaryProfileId, flamegraphId);
  } else if (eventType != null) {
    request = FlamegraphService.exportByEventType(primaryProfileId, eventType);
  } else {
    console.error('FlamegraphID or EventType needs to be propagated to load the flamegraph');
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
