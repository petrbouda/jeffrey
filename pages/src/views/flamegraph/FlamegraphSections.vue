<script setup>

import {onBeforeMount, ref} from "vue";
import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventTypes from "@/service/EventTypes";
import FormattingService from "../../service/FormattingService";
import FlamegraphService from "@/service/flamegraphs/FlamegraphService";
import SectionCard from "@/components/SectionCard.vue";
import EventTitleFormatter from "@/service/flamegraphs/EventTitleFormatter";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";

const objectAllocationEvents = ref([])
const executionSampleEvents = ref([])
const blockingEvents = ref([])

const loaded = ref(false)

onBeforeMount(() => {
  FlamegraphService.supportedEvents(PrimaryProfileService.id())
      .then((data) => {
        categorizeEventTypes(data)
        loaded.value = true
      })
});

function categorizeEventTypes(evenTypes) {
  for (let eventType of evenTypes) {
    if (EventTypes.isExecutionEventType(eventType.code)) {
      executionSampleEvents.value.push(eventType)
    } else if (EventTypes.isAllocationEventType(eventType.code)) {
      objectAllocationEvents.value.push(eventType)
    } else if (EventTypes.isBlockingEventType(eventType.code)) {
      blockingEvents.value.push(eventType)
    }
  }
}

const items = [
  {label: 'Flamegraphs'},
  {label: 'Primary', route: '/common/flamegraph-sections'}
]
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card">
    <div class="grid">
      <SectionCard
          router-forward="flamegraph"
          title="Execution Samples"
          :title-formatter="EventTitleFormatter.executionSamples"
          color="blue"
          icon="sprint"
          thread-mode-opt="true"
          event-desc="Execution Sample"
          :graph-mode="GraphType.PRIMARY"
          :events="executionSampleEvents"
          :loaded="loaded"/>

      <SectionCard
          router-forward="flamegraph"
          title="Object Allocations"
          :title-formatter="EventTitleFormatter.allocationSamples"
          color="green"
          icon="memory"
          thread-mode-opt="true"
          weight-opt="true"
          weight-selected="true"
          weight-desc="Total Allocation"
          :weight-formatter="FormattingService.formatBytes"
          event-desc="Object Allocation Events"
          :graph-mode="GraphType.PRIMARY"
          :events="objectAllocationEvents"
          :loaded="loaded"/>

      <SectionCard
          router-forward="flamegraph"
          title="Blocking Samples"
          :title-formatter="EventTitleFormatter.blockingSamples"
          color="red"
          icon="lock"
          thread-mode-opt="true"
          weight-opt="true"
          weight-selected="true"
          weight-desc="Blocked Time"
          :weight-formatter="FormattingService.formatDuration"
          event-desc="Blocking Events"
          :graph-mode="GraphType.PRIMARY"
          :events="blockingEvents"
          :loaded="loaded"/>

      <!--      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-yellow-50')"-->
      <!--           onmouseout="this.classList.remove('bg-yellow-50')">-->
      <!--        <div class="shadow-1 surface-card text-center h-full">-->
      <!--          <div class="p-4 bg-yellow-50 text-yellow-600 inline-flex justify-content-center mb-4 w-full">-->
      <!--            <span class="material-symbols-outlined text-5xl">delete_forever</span>-->
      <!--          </div>-->
      <!--          <div class="text-900 font-bold text-2xl mb-4 p-1">Old Object Allocations</div>-->
      <!--          <div class="flex justify-content-center flex-wrap mx-5 h-max">-->
      <!--            <div class="field-radiobutton px-2">-->
      <!--              <RadioButton id="option1" name="option" value="Monitor Lock"/>-->
      <!--              <label for="option1">Monitor Lock</label>-->
      <!--            </div>-->
      <!--            <div class="field-radiobutton px-2">-->
      <!--              <RadioButton id="option2" name="option" value="Monitor Wait"/>-->
      <!--              <label for="option2">Monitor Wait</label>-->
      <!--            </div>-->
      <!--            <div class="field-radiobutton px-2">-->
      <!--              <RadioButton id="option3" name="option" value="Thread Park"/>-->
      <!--              <label for="option3">Thread Park</label>-->
      <!--            </div>-->
      <!--          </div>-->

      <!--          <div>-->
      <!--            <button class="p-button p-button-text m-2" type="button">-->
      <!--              <span class="material-symbols-outlined text-2xl">help</span>-->
      <!--            </button>-->

      <!--            <button class="p-button p-component p-button-text m-2" type="button"-->
      <!--                    @click="router.push({ name: 'flamegraph-difference' })">-->
      <!--              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>-->
      <!--            </button>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--      </div>-->


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
