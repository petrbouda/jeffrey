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
import Utils from "@/services/Utils";
import {useRoute} from "vue-router";
import EventSummary from "@/services/api/model/EventSummary";

const props = defineProps<{
  routerForward: string,
  buttonTitle: string,
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

// Get the card color based on the card type
const getCardColor = () => {
  const color = props.color.toLowerCase();
  if (color === 'blue') return 'primary';
  if (color === 'purple') return 'info';
  if (color === 'green') return 'success';
  if (color === 'pink' || color === 'red') return 'danger';
  return 'primary'; // Default
};

// Get border class for card
const getCardBorderClass = () => {
  return `border-${getCardColor()}`;
};

// Get background class for card
const getCardBgClass = () => {
  return `bg-${getCardColor()}-subtle`;
};

// Get background class for icon
const getIconBgClass = () => {
  return `bg-${getCardColor()}`;
};

// Get icon class based on the card type
const getIconClass = () => {
  const icon = props.icon.toLowerCase();
  if (icon.includes('sprint') || icon.includes('cpu')) return 'bi-cpu';
  if (icon.includes('alarm') || icon.includes('clock')) return 'bi-clock';
  if (icon.includes('memory')) return 'bi-memory';
  if (icon.includes('stopwatch')) return 'bi-stopwatch';
  return 'bi-fire'; // Default
};

const route = useRoute()

const activeEvent = ref<EventSummary>(props.event)

const enabled = computed(() => {
  return props.loaded
})

const containsSecondary = () => {
  return activeEvent.value.secondary != null
}

const isSameType = () => {
  return activeEvent.value.secondary != null && activeEvent.value.primary.code === activeEvent.value.secondary.code
}

const isSameSource = () => {
  return activeEvent.value.secondary != null && activeEvent.value.primary.source === activeEvent.value.secondary.source
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
</script>
<template>
  <div :class="['card', 'shadow-sm', 'guardian-card', getCardBorderClass(), getCardBgClass()]" v-if="props.loaded">
      <div :class="['card-header', getCardBgClass()]">
        <div class="d-flex align-items-center mb-1">
          <div :class="['status-icon', getIconBgClass(), 'me-2']">
            <i class="bi" :class="getIconClass()"></i>
          </div>
          <h5 class="card-title mb-0">{{ props.title }}</h5>
        </div>
      </div>

      <div class="card-body d-flex flex-column" v-if="enabled">
        <div class="flex-grow-1">
        <ul class="list-unstyled mb-4">
          <!-- Code -->
          <li class="field-row">
            <span class="field-label">Code:</span>
            <span class="field-value" v-if="containsSecondary() && !isSameType()">
              <span class="primary-value">{{ activeEvent.primary.code }}</span>
              <span class="secondary-value">/ {{ activeEvent.secondary?.code }}</span>
              <span class="secondary-value" v-if="Utils.parseBoolean(activeEvent.primary.calculated)">(calculated)</span>
            </span>
            <span class="field-value" v-else>
              {{ activeEvent.primary.code }}
              <span class="secondary-value" v-if="Utils.parseBoolean(activeEvent.primary.calculated)">(calculated)</span>
            </span>
          </li>

          <!-- Sub-Type -->
          <li class="field-row" v-if="activeEvent.primary.subtype != null">
            <span class="field-label">Sub-Type:</span>
            <span class="field-value" v-if="containsSecondary() && !isSameType()">
              <span class="primary-value">{{ activeEvent.primary.subtype }}</span>
              <span class="secondary-value">/ {{ activeEvent.secondary?.subtype }}</span>
            </span>
            <span class="field-value" v-else>
              {{ activeEvent.primary.subtype }}
            </span>
          </li>

          <!-- Source -->
          <li class="field-row">
            <span class="field-label">Source:</span>
            <span class="field-value" v-if="containsSecondary() && !isSameSource()">
              <span class="primary-value">{{ activeEvent.primary.source }}</span>
              <span class="secondary-value">/ {{ activeEvent.secondary?.source }}</span>
            </span>
            <span class="field-value" v-else>
              {{ activeEvent.primary.source }}
            </span>
          </li>

          <!-- Samples -->
          <li class="field-row">
            <span class="field-label">Samples:</span>
            <span class="field-value" v-if="containsSecondary()">
              <span class="primary-value">{{ activeEvent.primary.samples.toLocaleString() }}</span>
              <span class="secondary-value">/ {{ activeEvent.secondary?.samples.toLocaleString() }}</span>
            </span>
            <span class="field-value" v-else>
              {{ activeEvent.primary.samples.toLocaleString() }}
            </span>
          </li>

          <!-- Weight -->
          <li class="field-row" v-if="props.weightDesc != null">
            <span class="field-label">{{ props.weightDesc }}:</span>
            <span class="field-value" v-if="containsSecondary()">
              <span class="primary-value">{{ props.weightFormatter(activeEvent.primary.weight) }}</span>
              <span class="secondary-value">/ {{ props.weightFormatter(activeEvent.secondary?.weight) }}</span>
            </span>
            <span class="field-value" v-else>
              {{ props.weightFormatter(activeEvent.primary.weight) }}
            </span>
          </li>

          <!-- Sample Interval -->
          <li class="field-row" v-if="Utils.isNotNull(activeEvent.primary.extras?.sample_interval)">
            <span class="field-label">Sample Interval:</span>
            <span class="field-value" v-if="containsSecondary()">
              <span class="primary-value">{{ props.weightFormatter(activeEvent.primary.extras.sample_interval) }}</span>
              <span class="secondary-value">/ {{ props.weightFormatter(activeEvent.secondary?.extras.sample_interval) }}</span>
            </span>
            <span class="field-value" v-else>
              {{ props.weightFormatter(activeEvent.primary.extras.sample_interval) }}
            </span>
          </li>
        </ul>
        
        <!-- Additional Info -->
        <slot name="additionalInfo"></slot>

        <!-- Checkboxes -->
        <div class="mt-3">
          <div class="d-flex flex-column">
            <!-- Thread mode checkbox -->
            <div class="form-check mb-2" v-if="props.threadModeOpt">
              <input class="form-check-input" type="checkbox" :id="'threadMode_' + activeEvent.code" v-model="useThreadMode">
              <label class="form-check-label" :for="'threadMode_' + activeEvent.code">
                Use Thread-mode
              </label>
            </div>
            
            <!-- Weight option checkbox -->
            <div class="form-check mb-2" v-if="props.weightOpt">
              <input class="form-check-input" type="checkbox" :id="'useWeight_' + activeEvent.code" v-model="useWeight">
              <label class="form-check-label" :for="'useWeight_' + activeEvent.code">
                Use {{ weightDescription }}
              </label>
            </div>
            
            <!-- Exclude Idle Samples -->
            <div class="form-check mb-2" v-if="props.excludeIdleSamplesOpt">
              <input class="form-check-input" type="checkbox" :id="'excludeIdle_' + activeEvent.code" v-model="excludeIdleSamples" @click="switchIdleSamples()">
              <label class="form-check-label" :for="'excludeIdle_' + activeEvent.code">
                Exclude Idle Samples
                <i class="bi bi-info-circle-fill text-muted ms-1" data-bs-toggle="tooltip" data-bs-placement="top" title="Excludes samples that are parked in thread-pools"></i>
              </label>
            </div>
            
            <!-- Exclude non-Java Samples -->
            <div class="form-check mb-2" v-if="props.excludeNonJavaSamplesOpt">
              <input class="form-check-input" type="checkbox" :id="'excludeNonJava_' + activeEvent.code" v-model="excludeNonJavaSamples">
              <label class="form-check-label" :for="'excludeNonJava_' + activeEvent.code">
                Exclude non-Java Samples
                <i class="bi bi-info-circle-fill text-muted ms-1" data-bs-toggle="tooltip" data-bs-placement="top" title="Excludes samples belonging to JIT, Garbage Collector, and other non-Java threads"></i>
              </label>
            </div>
            
            <!-- Only Unsafe Allocation Samples -->
            <div class="form-check" v-if="props.onlyUnsafeAllocationSamplesOpt">
              <input class="form-check-input" type="checkbox" :id="'unsafeAlloc_' + activeEvent.code" v-model="onlyUnsafeAllocationSamples">
              <label class="form-check-label" :for="'unsafeAlloc_' + activeEvent.code">
                Only Allocations with Unsafe
                <i class="bi bi-info-circle-fill text-muted ms-1" data-bs-toggle="tooltip" data-bs-placement="top" title="Filters out all JVM-specific allocations and let only the relevant ones"></i>
              </label>
            </div>
          </div>
        </div>
        </div>
      </div>
      
      <div class="card-body" v-else>
        <div class="text-center">
          <div class="fw-bold">Samples Unavailable</div>
        </div>
      </div>

      <div class="card-footer bg-transparent">
        <div class="d-flex justify-content-end">
          <button class="btn btn-primary" type="button" :disabled="!enabled" @click="moveToFlamegraph">
            <i class="bi bi-fire me-1"></i> {{ props.buttonTitle }}
          </button>
        </div>
      </div>
    </div>
</template>

<style scoped>
.guardian-card {
  position: relative;
  transition: transform 0.2s, box-shadow 0.2s;
  border-width: 1px;
  border-left-width: 4px;
  overflow: hidden;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.guardian-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.card-body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}

.list-unstyled {
  padding-left: 0;
  list-style: none;
  margin-bottom: 0;
}

.field-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  margin-bottom: 0;
  min-height: 2.5rem;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.field-label {
  font-size: 0.875rem;
  color: #6c757d;
  flex: 0 0 auto;
  margin-right: 1rem;
  font-weight: 500;
}

.field-value {
  text-align: right;
  font-weight: 600;
  flex: 1;
  max-width: 70%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.primary-value {
  color: #6366f1;
}

.secondary-value {
  color: #83888f;
}

.card-header {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding: 1rem 1.25rem;
}

.status-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
}

.card-footer {
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  padding: 0.75rem 1rem;
}

.form-check-label {
  font-size: 0.875rem;
}

.btn-primary {
  background-color: #5e64ff;
  border-color: #5e64ff;
}

.btn-primary:hover {
  background-color: #4349e8;
  border-color: #4349e8;
}
</style>
