<script setup>

import router from "@/router";
import {onBeforeMount, ref} from "vue";
import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventTypes from "@/service/EventTypes";
import FormattingService from "../../service/FormattingService";
import InformationService from "@/service/InformationService";

const useThreadMode_ExecutionSamples = ref(false);

const useThreadMode_ObjectAllocationSamples = ref(false);
const useTotalAllocations_ObjectAllocationSamples = ref(true);

const useThreadMode_Blocking = ref(false);
const useTotalTime_Blocking = ref(true);
const monitorType_Blocking = ref(null);

const infoLoaded = ref(false)

let objectAllocationEvent = null
let objectAllocationTitle = null

let executionSampleEvent = null
let executionSampleTitle = null

let caughtBlockingSamples = []


onBeforeMount(() => {
  InformationService.getEventsInfo(PrimaryProfileService.id())
      .then((data) => {
        console.log(data)

        // save all interesting events
        catchInterestingEventTypes(data)

        // process and prepare data for UI
        processExecutionSamples()
        processAllocationSamples()
        processBlockingSamples()

        infoLoaded.value = true
      })
});

function processBlockingSamples() {
  if (caughtBlockingSamples.length !== 0) {
    monitorType_Blocking.value = caughtBlockingSamples[0]
  }
}

function processAllocationSamples() {
  if (objectAllocationEvent != null) {
    const eventTypeCode = objectAllocationEvent.code
    if (EventTypes.isObjectAllocationInNewTLAB(eventTypeCode)) {
      if (objectAllocationEvent.extras != null && objectAllocationEvent.extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
        objectAllocationTitle = "Async-Profiler (" + EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB + ")"
      } else {
        objectAllocationTitle = "JDK (" + EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB + ")"
      }
    } else if (EventTypes.isObjectAllocationSample(eventTypeCode)) {
      objectAllocationTitle = "JDK (" + EventTypes.OBJECT_ALLOCATION_SAMPLE + ")"
    } else {
      objectAllocationTitle = ""
      console.log("Unknown Object Allocation Source")
    }
  }
}

function processExecutionSamples() {
  if (executionSampleEvent != null && executionSampleEvent.extras != null) {
    const extras = executionSampleEvent.extras

    if (extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
      if (extras.cpu_event === "cpu") {
        executionSampleTitle = "Async-Profiler (CPU - perf_event)"
      } else {
        executionSampleTitle = "Async-Profiler (" + extras.cpu_event + ")"
      }
    } else if (extras.source === "JDK") {
      executionSampleTitle = "JDK (Method Samples)"
    } else {
      executionSampleTitle = ""
      console.log("Unknown CPU Source")
    }
  }
}

function catchInterestingEventTypes(evenTypes) {
  for (let eventType of evenTypes) {
    if (EventTypes.isExecutionEventType(eventType.code)) {
      executionSampleEvent = eventType
    } else if (EventTypes.isAllocationEventType(eventType.code)) {
      objectAllocationEvent = eventType
    } else if (EventTypes.isBlockingEventType(eventType.code)) {
      caughtBlockingSamples.push(eventType)
    }
  }
}

function stripJavaPrefix(eventTypeLabel) {
  if (eventTypeLabel.startsWith("Java ")) {
    return eventTypeLabel.slice("Java ".length);
  }
}

function generateBlockingTitle(selectedEvent) {
  if (selectedEvent.extras != null && selectedEvent.extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
    return "Async-Profiler (" + selectedEvent.code + ")"
  } else {
    return "JDK (" + selectedEvent.code + ")"
  }
}
</script>

<template>

  <div class="card">
    <div class="grid">
      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-blue-50')"
           onmouseout="this.classList.remove('bg-blue-50')">
        <div class="shadow-1 surface-card text-center h-full ">
          <div class="p-4 inline-flex justify-content-center mb-4 w-full"
               v-bind:class="(executionSampleTitle)?'bg-blue-50 text-blue-600':'bg-gray-50 text-gray-600'">
            <span class="material-symbols-outlined text-5xl">sprint</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Execution Samples</div>
          <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
          <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
          <!--          </div>-->

          <div class="grid mx-5" v-if="executionSampleEvent">
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Type:</span> <span class="ml-3">{{ executionSampleTitle }}</span>
            </div>
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Samples:</span> <span class="ml-3">{{
                executionSampleEvent.samples
              }}</span>
            </div>

            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="useThreadMode_ExecutionSamples" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Thread-mode</label>
            </div>
          </div>
          <div class="grid mx-5" v-else>
            <div class="text-700 pl-3 font-semibold">Samples Unavailable</div>
            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
            </div>
          </div>

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    :disabled="executionSampleEvent == null"
                    @click="router.push({ name: 'flamegraphs', query: { eventType: 'jdk.ExecutionSample', useThreadMode: useThreadMode_ExecutionSamples, useWeight: false } })">
              <span class="p-button-label" data-pc-section="label">Show Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-green-50')"
           onmouseout="this.classList.remove('bg-green-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 inline-flex justify-content-center mb-4 w-full"
               v-bind:class="(objectAllocationEvent)?'bg-green-50 text-green-600':'bg-gray-50 text-gray-600'">
            <span class="material-symbols-outlined text-5xl">memory</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Object Allocations</div>
          <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
          <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
          <!--          </div>-->

          <div class="grid mx-5" v-if="objectAllocationEvent">
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Type:</span>
              <div v-if="objectAllocationEvent">
                <span class="ml-3">{{ objectAllocationTitle }}</span>
              </div>
              <div v-else>
                <div class="text-700 pl-3 font-semibold">Samples Unavailable</div>
              </div>
            </div>

            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Samples:</span> <span class="ml-3">
              {{ objectAllocationEvent.samples }}
              </span>
            </div>
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Total Allocation:</span> <span
                class="ml-3">{{ FormattingService.formatBytes(objectAllocationEvent.weight) }}</span>
            </div>

            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="useThreadMode_ObjectAllocationSamples" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Thread-mode</label>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="useTotalAllocations_ObjectAllocationSamples" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Total Allocation</label>
            </div>
          </div>
          <div class="grid mx-5" v-else>
            <div class="text-700 pl-3 font-semibold">Samples Unavailable</div>
            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
            </div>
          </div>

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    :disabled="objectAllocationEvent == null"
                    @click="router.push({ name: 'flamegraphs', query: { eventType: objectAllocationEvent.code, useThreadMode: useThreadMode_ObjectAllocationSamples, useWeight: useTotalAllocations_ObjectAllocationSamples } })">
              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-red-50')"
           onmouseout="this.classList.remove('bg-red-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 inline-flex justify-content-center mb-4 w-full"
               v-bind:class="(monitorType_Blocking)?'bg-red-50 text-red-600':'bg-gray-50 text-gray-600'">
            <span class="material-symbols-outlined text-5xl">lock</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Blocking Samples</div>

          <div class="grid mx-5" v-if="monitorType_Blocking">
            <div class="col-12 flex justify-content-center flex-wrap">
              <div class="field-radiobutton px-2" v-for="(value, key) in caughtBlockingSamples" :key="key">
                <RadioButton id="option1" name="option" :value="value" v-model="monitorType_Blocking"/>
                <label for="option1">{{ stripJavaPrefix(value.label) }}</label>
              </div>
            </div>

            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Type:</span>
              <div v-if="monitorType_Blocking">
                <span class="ml-3">{{ generateBlockingTitle(monitorType_Blocking) }}</span>
              </div>
              <div v-else>
                <div class="text-700 pl-3 font-semibold">Samples Unavailable</div>
              </div>
            </div>

            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Samples:</span> <span class="ml-3">
              {{ monitorType_Blocking.samples }}
              </span>
            </div>
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Total Blocked Time:</span> <span
                class="ml-3">{{ FormattingService.formatDuration(monitorType_Blocking.weight) }}</span>
            </div>

            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="useThreadMode_Blocking" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Thread-mode</label>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="useTotalTime_Blocking" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Total Blocked Time</label>
            </div>
          </div>
          <div class="grid mx-5" v-else>
            <div class="text-700 pl-3 font-semibold">Samples Unavailable</div>
            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
            </div>
          </div>

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    :disabled="monitorType_Blocking == null"
                    @click="router.push({ name: 'flamegraphs', query: { eventType: monitorType_Blocking.code, useThreadMode: useThreadMode_Blocking, useWeight: useTotalTime_Blocking } })">
              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-yellow-50')"
           onmouseout="this.classList.remove('bg-yellow-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 bg-yellow-50 text-yellow-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">delete_forever</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Old Object Allocations</div>
          <div class="flex justify-content-center flex-wrap mx-5 h-max">
            <div class="field-radiobutton px-2">
              <RadioButton id="option1" name="option" value="Monitor Lock"/>
              <label for="option1">Monitor Lock</label>
            </div>
            <div class="field-radiobutton px-2">
              <RadioButton id="option2" name="option" value="Monitor Wait"/>
              <label for="option2">Monitor Wait</label>
            </div>
            <div class="field-radiobutton px-2">
              <RadioButton id="option3" name="option" value="Thread Park"/>
              <label for="option3">Thread Park</label>
            </div>
          </div>

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    @click="router.push({ name: 'flamegraph-difference' })">
              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>
            </button>
          </div>
        </div>
      </div>


      <!--            <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-pink-50')"-->
      <!--                 onmouseout="this.classList.remove('bg-pink-50')">-->
      <!--        <div class="shadow-1 surface-card text-center">-->
      <!--          <div class="p-4 bg-pink-50 text-pink-600 inline-flex justify-content-center align-items-center mb-4 w-full">-->
      <!--            <span class="material-symbols-outlined text-5xl">apps</span>-->
      <!--          </div>-->
      <!--          <div class="text-900 font-medium text-2xl mb-4 p-1">Sub-Second Analysis</div>-->
      <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
      <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
      <!--          </div>-->

      <!--          <div class="flex justify-content-between flex-wrap">-->
      <!--            <button class="p-button p-button-text m-2" type="button">-->
      <!--              <span class="material-symbols-outlined text-2xl">help</span>-->
      <!--            </button>-->

      <!--            <button class="p-button p-component p-button-text m-2" type="button"-->
      <!--                    @click="router.push({ name: 'flamegraph-startup' })">-->
      <!--              <span class="p-button-label" data-pc-section="label">Enter Section</span>-->
      <!--            </button>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--      </div>-->

      <!--      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-orange-50')"-->
      <!--           onmouseout="this.classList.remove('bg-orange-50')">-->
      <!--        <div class="shadow-1 surface-card text-center">-->
      <!--          <div-->
      <!--              class="p-4 bg-orange-50 text-orange-600 inline-flex justify-content-center align-items-center mb-4 w-full">-->
      <!--            <span class="material-symbols-outlined text-5xl">memory</span>-->
      <!--          </div>-->
      <!--          <div class="text-900 font-medium text-2xl mb-4 p-1">Native Memory</div>-->
      <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
      <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
      <!--          </div>-->

      <!--          <div class="flex justify-content-between flex-wrap">-->
      <!--            <button class="p-button p-button-text m-2" type="button">-->
      <!--              <span class="material-symbols-outlined text-2xl">help</span>-->
      <!--            </button>-->

      <!--            <button class="p-button p-component p-button-text m-2" type="button"-->
      <!--                    @click="router.push({ name: 'native-memory' })">-->
      <!--              <span class="p-button-label" data-pc-section="label">Enter Section</span>-->
      <!--            </button>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--      </div>-->

      <!--      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-yellow-50')"-->
      <!--           onmouseout="this.classList.remove('bg-yellow-50')">-->
      <!--        <div class="shadow-1 surface-card text-center">-->
      <!--          <div-->
      <!--              class="p-4 bg-yellow-50 text-yellow-600 inline-flex justify-content-center align-items-center mb-4 w-full">-->
      <!--            <span class="material-symbols-outlined text-5xl">delete</span>-->
      <!--          </div>-->
      <!--          <div class="text-900 font-medium text-2xl mb-4 p-1">Garbage Collection</div>-->
      <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
      <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
      <!--          </div>-->

      <!--          <div class="flex justify-content-between flex-wrap">-->
      <!--            <button class="p-button p-button-text m-2" type="button">-->
      <!--              <span class="material-symbols-outlined text-2xl">help</span>-->
      <!--            </button>-->

      <!--            <button class="p-button p-component p-button-text m-2" type="button"-->
      <!--                    @click="router.push({ name: 'garbage-collection' })">-->
      <!--              <span class="p-button-label" data-pc-section="label">Enter Section</span>-->
      <!--            </button>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--      </div>-->
    </div>
  </div>
</template>
