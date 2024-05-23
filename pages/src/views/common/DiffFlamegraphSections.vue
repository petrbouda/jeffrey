<script setup>

import router from "@/router";
import {onBeforeMount, ref} from "vue";
import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventTypes from "@/service/EventTypes";
import FormattingService from "../../service/FormattingService";
import FlamegraphService from "@/service/flamegraphs/FlamegraphService";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import Flamegraph from "@/service/flamegraphs/Flamegraph";

const useTotalAllocations_ObjectAllocationSamples = ref(true);

const infoLoaded = ref(false)

const objectAllocationEvent = ref(null)
const objectAllocationTitle = ref(null)

const executionSampleEvent = ref(null)
const executionSampleTitle = ref(null)

const caughtBlockingSamples = ref([])


onBeforeMount(() => {
  FlamegraphService.supportedEventsDiff(PrimaryProfileService.id(), SecondaryProfileService.id())
      .then((data) => {
        console.log(data)

        // save all interesting events
        catchInterestingEventTypes(data)

        // process and prepare data for UI
        processExecutionSamples()
        processAllocationSamples()

        infoLoaded.value = true
      })
});

function processAllocationSamples() {
  if (objectAllocationEvent.value != null) {
    const eventTypeCode = objectAllocationEvent.value.code
    if (EventTypes.isObjectAllocationInNewTLAB(eventTypeCode)) {
      if (objectAllocationEvent.value.extras != null && objectAllocationEvent.value.extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
        objectAllocationTitle.value = "Async-Profiler (" + EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB + ")"
      } else {
        objectAllocationTitle.value = "JDK (" + EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB + ")"
      }
    } else if (EventTypes.isObjectAllocationSample(eventTypeCode)) {
      objectAllocationTitle.value = "JDK (" + EventTypes.OBJECT_ALLOCATION_SAMPLE + ")"
    } else {
      objectAllocationTitle.value = ""
      console.log("Unknown Object Allocation Source")
    }
  }
}

function processExecutionSamples() {
  if (executionSampleEvent.value != null && executionSampleEvent.value.extras != null) {
    const extras = executionSampleEvent.value.extras

    if (extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
      if (extras.cpu_event === "cpu") {
        executionSampleTitle.value = "Async-Profiler (CPU - perf_event)"
      } else {
        executionSampleTitle.value = "Async-Profiler (" + extras.cpu_event + ")"
      }
    } else if (extras.source === "JDK") {
      executionSampleTitle.value = "JDK (Method Samples)"
    } else {
      executionSampleTitle.value = ""
      console.log("Unknown CPU Source")
    }
  }
}

function catchInterestingEventTypes(evenTypes) {
  for (let eventType of evenTypes) {
    if (EventTypes.isExecutionEventType(eventType.code)) {
      executionSampleEvent.value = eventType
    } else if (EventTypes.isAllocationEventType(eventType.code)) {
      objectAllocationEvent.value = eventType
    } else if (EventTypes.isBlockingEventType(eventType.code)) {
      caughtBlockingSamples.value.push(eventType)
    }
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
               v-bind:class="(executionSampleTitle != null)?'bg-blue-50 text-blue-600':'bg-gray-50 text-gray-600'">
            <span class="material-symbols-outlined text-5xl">sprint</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Execution Samples</div>
          <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
          <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
          <!--          </div>-->

          <div class="grid mx-5" v-if="executionSampleTitle != null">
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Type:</span> <span class="ml-3">{{ executionSampleTitle }}</span>
            </div>
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Samples (Primary):</span> <span class="ml-3">{{
                executionSampleEvent.samples
              }}</span>
            </div>

            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
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
                    @click="router.push({ name: 'flamegraphs', query: { eventType: 'jdk.ExecutionSample', graphMode: Flamegraph.DIFFERENTIAL, useThreadMode: false, useWeight: false } })">
              <span class="p-button-label" data-pc-section="label">Show Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-green-50')"
           onmouseout="this.classList.remove('bg-green-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 inline-flex justify-content-center mb-4 w-full"
               v-bind:class="(objectAllocationEvent != null)?'bg-green-50 text-green-600':'bg-gray-50 text-gray-600'">
            <span class="material-symbols-outlined text-5xl">memory</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Object Allocations</div>
          <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
          <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
          <!--          </div>-->

          <div class="grid mx-5" v-if="objectAllocationEvent != null">
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Type:</span> <span class="ml-3">{{ objectAllocationTitle }}</span>
            </div>

            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Samples (Primary):</span> <span class="ml-3">
              {{ objectAllocationEvent.samples }}
              </span>
            </div>
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Total Allocation (Primary):</span> <span
                class="ml-3">{{ FormattingService.formatBytes(objectAllocationEvent.weight) }}</span>
            </div>

            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
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
                    @click="router.push({ name: 'flamegraphs', query: { eventType: objectAllocationEvent.code, graphMode: Flamegraph.DIFFERENTIAL, useThreadMode: false, useWeight: useTotalAllocations_ObjectAllocationSamples } })">
              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
