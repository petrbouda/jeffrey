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

const useThreadMode_Locking = ref(false);
const useTotalTime_Locking = ref(true);
const monitorType_Locking = ref("Monitor Lock");

const infoLoaded = ref(false)
let objectAllocationEvent = null
let executionSampleEvent = null

onBeforeMount(() => {
  InformationService.getEventsInfo(PrimaryProfileService.id())
      .then((data) => {
        selectObjectAllocationEventCode(data)
        infoLoaded.value = true
      })
});

function selectObjectAllocationEventCode(evenTypes) {
  for (let eventType of evenTypes) {
    if (EventTypes.isExecutionEventType(eventType.code)) {
      executionSampleEvent = eventType
    } else if (EventTypes.isAllocationEventType(eventType.code)) {
      objectAllocationEvent = eventType
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
          <div class="p-4 bg-blue-50 text-blue-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">sprint</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Execution Samples</div>
          <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
          <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
          <!--          </div>-->

          <div class="grid mx-5">
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Type:</span> <span class="ml-3">Async-Profiler (ctimer)</span>
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

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    @click="router.push({ name: 'flamegraphs', query: { eventType: 'jdk.ExecutionSample', useThreadMode: useThreadMode_ExecutionSamples, useWeight: false } })">
              <span class="p-button-label" data-pc-section="label">Show Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-green-50')"
           onmouseout="this.classList.remove('bg-green-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 bg-green-50 text-green-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">memory</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Object Allocations</div>
          <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
          <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
          <!--          </div>-->

          <div class="grid mx-5">
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Type:</span>
              <div  v-if="objectAllocationEvent.code === EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB">
                <span class="ml-3">Async-Profiler - New TLAB Allocation</span>
              </div>
              <div
                  v-else-if="objectAllocationEvent.code === EventTypes.OBJECT_ALLOCATION_SAMPLE">
                <span class="ml-3">JDK - Object Allocation Sample</span>
              </div>
              <div v-else>
                <span class="ml-3">Unknown Allocation Type</span>
              </div>
            </div>

            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Samples:</span> <span class="ml-3">
              {{ objectAllocationEvent.samples }}
              </span>
            </div>
            <div v-if="infoLoaded" class="col-12 flex align-items-center">
              <span class="ml-2 font-semibold">Total Allocation:</span> <span
                class="ml-3">~{{ FormattingService.formatBytes(objectAllocationEvent.weight) }}</span>
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

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    @click="router.push({ name: 'flamegraphs', query: { eventType: objectAllocationEventCode, useThreadMode: useThreadMode_ObjectAllocationSamples, useWeight: useTotalAllocations_ObjectAllocationSamples } })">
              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-red-50')"
           onmouseout="this.classList.remove('bg-red-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 bg-red-50 text-red-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">lock</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Blocking Samples</div>

          <div class="grid mx-5">
            <div class="col-12 flex justify-content-center flex-wrap">
              <div class="field-radiobutton px-2">
                <RadioButton id="option1" name="option" value="Monitor Lock" v-model="monitorType_Locking"/>
                <label for="option1">Monitor Lock</label>
              </div>
              <div class="field-radiobutton px-2">
                <RadioButton id="option2" name="option" value="Monitor Wait" v-model="monitorType_Locking"/>
                <label for="option2">Monitor Wait</label>
              </div>
              <div class="field-radiobutton px-2">
                <RadioButton id="option3" name="option" value="Thread Park" v-model="monitorType_Locking"/>
                <label for="option3">Thread Park</label>
              </div>
            </div>

            <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
              <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="useThreadMode_Locking" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Thread-mode</label>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="useTotalTime_Locking" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Total Blocked Time</label>
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

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-yellow-50')"
           onmouseout="this.classList.remove('bg-yellow-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 bg-yellow-50 text-yellow-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">delete_forever</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Old Object Allocations</div>
          <div class="flex justify-content-center flex-wrap mx-5 h-max">
            <div class="field-radiobutton px-2">
              <RadioButton id="option1" name="option" value="Monitor Lock" v-model="radioValue"/>
              <label for="option1">Monitor Lock</label>
            </div>
            <div class="field-radiobutton px-2">
              <RadioButton id="option2" name="option" value="Monitor Wait" v-model="radioValue"/>
              <label for="option2">Monitor Wait</label>
            </div>
            <div class="field-radiobutton px-2">
              <RadioButton id="option3" name="option" value="Thread Park" v-model="radioValue"/>
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
