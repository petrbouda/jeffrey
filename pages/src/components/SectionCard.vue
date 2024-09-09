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

<script setup>
import router from "@/router";
import {computed, onBeforeMount, ref} from "vue";
import Utils from "@/service/Utils";

const props = defineProps([
  'routerForward',
  'title',
  'titleFormatter',
  'color',
  'icon',
  'graphMode',
  'threadModeOpt',
  'weightOpt',
  'weightSelected',
  'weightDesc',
  'weightFormatter',
  'eventDesc',
  'events',
  'loaded'
]);

const useThreadMode = ref(false)
const useWeight = ref(Utils.parseBoolean(props.weightSelected) === true)
const backgroundColor = 'bg-' + props.color + '-50'
const cardStyleEnabled = backgroundColor + ' text-' + props.color + '-600'

let weightDescription = "Use Weight"

const multiEvent = computed(() => {
  return props.events.length > 1
})

const activeEvent = ref(null)

const enabled = computed(() => {
  if (props.loaded) {
    if (props.events.length > 0) {
      // eslint-disable-next-line vue/no-side-effects-in-computed-properties
      activeEvent.value = props.events[0]
    }

    return props.events.length > 0
  } else {
    return false
  }
})

onBeforeMount(() => {
  if (props.weightDesc != null) {
    weightDescription = props.weightDesc
  }

})

function stripJavaPrefix(eventTypeLabel) {
  if (eventTypeLabel.startsWith("Java ")) {
    return eventTypeLabel.slice("Java ".length);
  }
  return eventTypeLabel
}

</script>

<template>
  <div class="lg:col-4 md:col-6"
       @mouseover="(e) => e.currentTarget.classList.add(backgroundColor)"
       @mouseout="(e) => e.currentTarget.classList.remove(backgroundColor)">
    <div class="shadow-1 surface-card text-center h-full" v-if="props.loaded">
      <div class="p-4 inline-flex justify-content-center mb-4 w-full"
           :class="enabled ? cardStyleEnabled : 'bg-gray-50 text-gray-600'">
        <span class="material-symbols-outlined text-5xl">{{ props.icon }}</span>
      </div>

      <div class="text-900 font-bold text-2xl mb-4 p-1">{{ props.title }}</div>

      <div class="grid mx-5" v-if="enabled">
        <div class="col-12 flex justify-content-center flex-wrap" v-if="multiEvent">
          <div class="field-radiobutton px-2" v-for="(value, key) in props.events" :key="key">
            <RadioButton id="option1" name="option" :value="value" v-model="activeEvent"/>
            <label for="option1">{{ stripJavaPrefix(value.label) }}</label>
          </div>
        </div>

        <div class="col-12 flex align-items-center" v-if="props.eventDesc != null">
          <span class="ml-2 font-semibold">Type:</span> <span class="ml-3">{{ titleFormatter(activeEvent["primary"]) }}</span>
        </div>
        <div class="col-12 flex align-items-center" v-if="activeEvent != null">
          <span class="ml-2 font-semibold">Samples:</span>
          <div v-if="activeEvent.secondary != null">
            <span class="ml-3" style="color: #6366f1">{{ activeEvent.primary.samples }}</span>
            <span class="ml-2" style="color: #83888f">/ {{ activeEvent.secondary.samples }}</span>
          </div>
          <div v-else>
            <span class="ml-3" style="color: #6366f1">{{ activeEvent.primary.samples }}</span>
          </div>
        </div>
        <div class="col-12 flex align-items-center" v-if="activeEvent != null && props.weightDesc != null">
          <span class="ml-2 font-semibold">{{ props.weightDesc }}:</span>
          <div v-if="activeEvent.secondary != null">
            <span class="ml-3" style="color: #6366f1">{{ props.weightFormatter(activeEvent.primary.weight) }}</span>
            <span class="ml-2" style="color: #83888f">/ {{ props.weightFormatter(activeEvent.secondary.weight) }}</span>
          </div>
          <div v-else>
            <span class="ml-3" style="color: #6366f1">{{ props.weightFormatter(activeEvent.primary.weight) }}</span>
          </div>
        </div>

        <slot name="additionalInfo"></slot>

        <div class="flex w-full relative align-items-center justify-content-start my-3 px-4">
          <div class="border-top-1 surface-border top-50 left-0 absolute w-full"></div>
        </div>

        <div v-if="Utils.parseBoolean(props.threadModeOpt)" class="col-12 flex align-items-center">
          <Checkbox v-model="useThreadMode" :binary="true"/>
          <label for="ingredient1" class="ml-2">Use Thread-mode</label>
        </div>

        <div v-if="Utils.parseBoolean(props.weightOpt)" class="col-12 flex align-items-center">
          <Checkbox v-model="useWeight" :binary="true"/>
          <label for="ingredient1" class="ml-2">Use {{ weightDescription }}</label>
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
                @click="router.push({ name: props.routerForward, query: { eventType: activeEvent.code, graphMode: props.graphMode, useThreadMode: useThreadMode, useWeight: useWeight } })">
          <span class="p-button-label" data-pc-section="label">Show Flamegraph</span>
        </button>
      </div>
    </div>
  </div>
</template>
