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

<script setup lang="ts">
import router from "@/router";
import {computed, ref} from "vue";
import Utils from "@/service/Utils";
import {useRoute} from "vue-router";
import EventSummary from "@/service/flamegraphs/model/EventSummary";

const props = defineProps<{
  routerForward: string,
  title: string,
  color: string,
  icon: string,
  graphMode: string,
  threadModeOpt: boolean,
  threadModeSelected: boolean,
  weightOpt: boolean,
  weightSelected: boolean,
  weightDesc: string,
  weightFormatter: (bytes: number) => string,
  excludeNonJavaSamplesOpt: boolean,
  excludeNonJavaSamplesSelected: boolean,
  excludeIdleSamplesOpt: boolean,
  excludeIdleSamplesSelected: boolean,
  onlyUnsafeAllocationSamplesOpt: boolean,
  onlyUnsafeAllocationSamplesSelected: boolean,
  event: EventSummary,
  loaded: any
}>()

const useThreadMode = ref(Utils.parseBoolean(props.threadModeSelected))
const useWeight = ref(Utils.parseBoolean(props.weightSelected))
const excludeNonJavaSamples = ref(Utils.parseBoolean(props.excludeNonJavaSamplesSelected))
const excludeIdleSamples = ref(Utils.parseBoolean(props.excludeIdleSamplesSelected))
const onlyUnsafeAllocationSamples = ref(Utils.parseBoolean(props.onlyUnsafeAllocationSamplesSelected))
const weightDescription = ref(props.weightDesc)

const backgroundColor = 'bg-' + props.color + '-50'
const cardStyleEnabled = backgroundColor + ' text-' + props.color + '-600'

const route = useRoute()

const activeEvent = ref<EventSummary>(props.event)

const enabled = computed(() => {
  return props.loaded
})

const containsSecondary = () => {
  return activeEvent.value.secondary != null
}

const isSameType = () => {
  return activeEvent.value.primary.extras.type === activeEvent.value.secondary.extras.type
}

const isSameSource = () => {
  return activeEvent.value.primary.extras.source === activeEvent.value.secondary.extras.source
}

const moveToFlamegraph = () => {
  let query = {
    eventType: activeEvent.value.code,
    graphMode: props.graphMode
  }

  if (useThreadMode.value) {
    query.useThreadMode = useThreadMode.value
  }
  if (useWeight.value) {
    query.useWeight = useWeight.value
  }
  if (excludeNonJavaSamples.value) {
    query.excludeNonJavaSamples = excludeNonJavaSamples.value
  }
  if (excludeIdleSamples.value) {
    query.excludeIdleSamples = excludeIdleSamples.value
  }
  if (onlyUnsafeAllocationSamples.value) {
    query.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples.value
  }

  router.push({
    name: props.routerForward,
    params: {
      projectId: route.params.projectId,
      profileId: route.params.profileId,
    },
    query: query
  });
}

function switchIdleSamples() {
  if (excludeIdleSamples.value) {
    excludeIdleSamples.value = false
    excludeNonJavaSamples.value = false
  } else {
    excludeIdleSamples.value = true
    excludeNonJavaSamples.value = true
  }
}

function mouseOverAddColor(e: MouseEvent) {
  if (e.currentTarget != null) {
    (e.currentTarget as Element).classList.add(backgroundColor)
  }
}

function mouseOutRemoveColor(e: MouseEvent) {
  if (e.currentTarget != null) {
    (e.currentTarget as Element).classList.remove(backgroundColor)
  }
}
</script>

<template>
  <div class="lg:col-4 md:col-6" @mouseover="mouseOverAddColor" @mouseout="mouseOutRemoveColor">
    <div class="shadow-1 surface-card text-center h-full" v-if="props.loaded">
      <div class="p-4 inline-flex justify-content-center mb-4 w-full"
           :class="enabled ? cardStyleEnabled : 'bg-gray-50 text-gray-600'">
        <span class="material-symbols-outlined text-4xl">{{ props.icon }}</span>
        <div class="font-bold text-2xl p-1 ml-3">{{ props.title }}</div>
      </div>

      <div class="grid mx-5" v-if="enabled">
        <div class="col-12 flex align-items-center">
          <span class="ml-2 font-semibold">Type:</span>
          <div v-if="containsSecondary() && !isSameType()">
            <span class="ml-3" style="color: #6366f1">{{ activeEvent.primary.extras.type }}</span>
            <span class="ml-2" style="color: #83888f">/ {{ activeEvent.secondary.extras.type }}</span>
            <span class="ml-2" style="color: #83888f" v-if="Utils.parseBoolean(activeEvent.primary.extras.calculated)">(calculated)</span>
          </div>
          <div v-else>
            <span class="ml-3">{{ activeEvent.primary.extras.type }}</span>
            <span class="ml-2" style="color: #83888f" v-if="Utils.parseBoolean(activeEvent.primary.extras.calculated)">(calculated)</span>
          </div>
        </div>
        <div class="col-12 flex align-items-center">
          <span class="ml-2 font-semibold">Source:</span>
          <div v-if="containsSecondary() && !isSameSource()">
            <span class="ml-3" style="color: #6366f1">{{ activeEvent.primary.extras.source }}</span>
            <span class="ml-2" style="color: #83888f">/ {{ activeEvent.secondary.extras.source }}</span>
          </div>
          <div v-else>
            <span class="ml-3">{{ activeEvent.primary.extras.source }}</span>
          </div>
        </div>
        <div class="col-12 flex align-items-center">
          <span class="ml-2 font-semibold">Samples:</span>
          <div v-if="containsSecondary()">
            <span class="ml-3" style="color: #6366f1">{{ activeEvent.primary.samples }}</span>
            <span class="ml-2" style="color: #83888f">/ {{ activeEvent.secondary.samples }}</span>
          </div>
          <div v-else>
            <span class="ml-3" style="color: #6366f1">{{ activeEvent.primary.samples }}</span>
          </div>
        </div>

        <div class="col-12 flex align-items-center" v-if="props.weightDesc != null">
          <span class="ml-2 font-semibold">{{ props.weightDesc }}:</span>
          <div v-if="containsSecondary()">
            <span class="ml-3" style="color: #6366f1">{{ props.weightFormatter(activeEvent.primary.weight) }}</span>
            <span class="ml-2" style="color: #83888f">/ {{ props.weightFormatter(activeEvent.secondary.weight) }}</span>
          </div>
          <div v-else>
            <span class="ml-3" style="color: #6366f1">{{ props.weightFormatter(activeEvent.primary.weight) }}</span>
          </div>
        </div>

        <div class="col-12 flex align-items-center" v-if="Utils.isNotNull(activeEvent.primary.extras.sample_interval)">
          <span class="ml-2 font-semibold">Sample Interval:</span>
          <div v-if="containsSecondary()">
            <span class="ml-3"
                  style="color: #6366f1">{{ props.weightFormatter(activeEvent.primary.extras.sample_interval) }}</span>
            <span class="ml-2" style="color: #83888f">/ {{
                props.weightFormatter(activeEvent.secondary.extras.sample_interval)
              }}</span>
          </div>
          <div v-else>
            <span class="ml-3"
                  style="color: #6366f1">{{ props.weightFormatter(activeEvent.primary.extras.sample_interval) }}</span>
          </div>
        </div>

        <slot name="additionalInfo"></slot>

        <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
          <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
        </div>

        <div v-if="props.threadModeOpt" class="col-12 flex align-items-center">
          <Checkbox v-model="useThreadMode" :binary="true"/>
          <label for="ingredient1" class="ml-2">Use Thread-mode</label>
        </div>

        <div v-if="props.weightOpt" class="col-12 flex align-items-center">
          <Checkbox v-model="useWeight" :binary="true"/>
          <label for="ingredient1" class="ml-2">Use {{ weightDescription }}</label>
        </div>

        <div v-if="props.excludeIdleSamplesOpt" class="col-12 flex align-items-center">
          <Checkbox v-model="excludeIdleSamples" :binary="true" @click="switchIdleSamples()"/>
          <label for="ingredient1" class="ml-2">Exclude Idle Samples
            <span class="material-symbols-outlined text-sm"
                  v-tooltip="{ value: 'Excludes samples that are parked in thread-pools', showDelay: 300, hideDelay: 300 }">help</span>
          </label>
        </div>

        <div v-if="props.excludeNonJavaSamplesOpt" class="col-12 flex align-items-center">
          <Checkbox v-model="excludeNonJavaSamples" :binary="true"/>
          <label for="ingredient1" class="ml-2">Exclude non-Java Samples
            <span class="material-symbols-outlined text-sm"
                  v-tooltip="{ value: 'Excludes samples belonging to JIT, Garbage Collector, and other non-java threads', showDelay: 300, hideDelay: 300 }">help</span>
          </label>
        </div>

        <div v-if="props.onlyUnsafeAllocationSamplesOpt" class="col-12 flex align-items-center">
          <Checkbox v-model="onlyUnsafeAllocationSamples" :binary="true"/>
          <label for="ingredient1" class="ml-2">Only Allocations with Unsafe
            <span class="material-symbols-outlined text-sm"
                  v-tooltip="{ value: 'Filters out all JVM-specific allocations and let only the relevant ones', showDelay: 300, hideDelay: 300 }">help</span>
          </label>
        </div>
      </div>
      <div class="grid mx-5" v-else>
        <div class="text-700 pl-3 font-semibold">Samples Unavailable</div>
        <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
          <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
        </div>
      </div>

      <div>
        <button class="p-button p-component p-button-text m-2" type="button" :disabled="!enabled"
                @click="moveToFlamegraph">
          <span class="p-button-label" data-pc-section="label">Show Graph</span>
        </button>
      </div>
    </div>
  </div>
</template>
