<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div
    class="root-header"
    role="button"
    tabindex="0"
    aria-label="Reset zoom"
    title="Reset zoom"
    @click="emit('reset')"
    @keydown.enter.space.prevent="emit('reset')"
  >
    <div class="metric">
      <span class="label">Events</span>
      <span class="value">{{ formattedSamples }}</span>
    </div>
    <span v-if="useWeight" class="delimiter">·</span>
    <div v-if="useWeight" class="metric metric--weight">
      <span class="label">{{ weightLabel }}</span>
      <span class="value">{{ formattedWeight }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Frame from '@/services/api/model/Frame';
import EventTypes from '@/services/EventTypes';
import FormattingService from '@shared/services/FormattingService';

interface Props {
  frame: Frame;
  eventType: string;
  useWeight: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'reset'): void;
}>();

const formattedSamples = computed(() => FormattingService.formatNumber(props.frame.totalSamples));

const formattedWeight = computed(() => {
  const weight = props.frame.totalWeight ?? 0;
  if (EventTypes.isAllocationEventType(props.eventType)) {
    return FormattingService.formatBytes(weight);
  }
  if (
    EventTypes.isBlockingEventType(props.eventType) ||
    EventTypes.isCpuTimeSample(props.eventType)
  ) {
    return FormattingService.formatDuration2Units(weight);
  }
  return weight.toLocaleString();
});

const weightLabel = computed(() => {
  if (EventTypes.isAllocationEventType(props.eventType)) {
    return 'Allocated';
  }
  if (EventTypes.isCpuTimeSample(props.eventType)) {
    return 'CPU Time';
  }
  if (EventTypes.isBlockingEventType(props.eventType)) {
    return 'Duration';
  }
  return 'Weight';
});
</script>

<style scoped>
.root-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 5px 10px;
  background: var(--color-primary-light);
  border: none;
  border-radius: 0;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.12s ease;
}

.root-header:hover {
  background: var(--color-primary-bg);
}

.root-header:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.metric {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.value {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.1;
}

.metric--weight .value {
  color: var(--color-primary);
}

.delimiter {
  color: var(--color-slate-light);
  font-weight: 400;
}
</style>
