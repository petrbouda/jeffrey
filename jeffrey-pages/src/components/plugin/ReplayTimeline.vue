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

<script setup lang="ts">
import { computed } from 'vue';
import PluginKindTag from '@/components/plugin/PluginKindTag.vue';
import { formatOffset, usePluginUsage } from '@/composables/usePluginUsage';
import type { ReplayRun } from '@/types/pluginUsage';

// A run's calls in order, each in its lane's column: the main conversation, then one per agent.
// Calls after the playback position are dimmed; a row click moves the position there.

const props = defineProps<{ run: ReplayRun; position: number }>();
const emit = defineEmits<{ (e: 'seek', index: number): void }>();

const model = usePluginUsage();
const lanes = computed(() => model.orderedLanes(props.run));
const columnOf = (lane: string): number => lanes.value.indexOf(lane) + 2;
</script>

<template>
  <div class="timeline" :style="{ '--lanes': lanes.length }" role="table" aria-label="The run's calls in order">
    <div class="timeline-row timeline-head" role="row">
      <span role="columnheader">Time</span>
      <span v-for="lane in lanes" :key="lane" class="timeline-head-lane" role="columnheader">{{ model.laneLabel(run, lane) }}</span>
      <span class="timeline-head-call" role="columnheader">Call · lane</span>
    </div>
    <button
      v-for="(step, index) in run.steps"
      :key="index"
      type="button"
      class="timeline-row timeline-step"
      :class="{ 'timeline-step--current': index === position, 'timeline-step--future': index > position }"
      role="row"
      @click="emit('seek', index)"
    >
      <span class="timeline-time" role="cell">{{ formatOffset(step.t) }}</span>
      <span class="timeline-call" :style="{ gridColumn: columnOf(step.lane) }" role="cell">
        <span class="timeline-lane">{{ model.laneLabel(run, step.lane) }}</span>
        <span class="timeline-name"><PluginKindTag :kind="step.kind" /> {{ step.name }}</span>
        <span v-if="step.input" class="timeline-input" :title="step.input">{{ step.input }}</span>
      </span>
    </button>
    <p v-if="!run.steps.length" class="timeline-empty">This run made no Skill, Agent or tool call.</p>
  </div>
</template>

<style scoped>
.timeline {
  display: flex;
  flex-direction: column;
}

.timeline-row {
  display: grid;
  grid-template-columns: 64px repeat(var(--lanes), minmax(0, 1fr));
  gap: 0 14px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--site-line);
}

.timeline-head {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--site-faint);
}

.timeline-head-call {
  display: none;
}

.timeline-step {
  width: 100%;
  border-top: 0;
  border-left: 0;
  border-right: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: opacity 0.15s, background 0.15s;
}

.timeline-step:hover {
  background: var(--ap-hover);
}

.timeline-step--current,
.timeline-step--current:hover {
  background: var(--ap-current);
}

.timeline-step--future {
  opacity: 0.38;
}

.timeline-time {
  grid-column: 1;
  font-family: var(--site-font-mono);
  font-size: 12px;
  color: var(--site-faint);
}

.timeline-call {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.timeline-lane {
  display: none;
}

.timeline-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
  color: var(--site-ink);
}

.timeline-input {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--site-font-mono);
  font-size: 11px;
  color: var(--site-faint);
}

.timeline-empty {
  margin: 0;
  padding: 16px 12px;
  color: var(--site-faint);
  font-size: 13px;
}

/* One column on a phone: each call says which lane it is in. */
@media (max-width: 859px) {
  .timeline-row {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .timeline-head-lane {
    display: none;
  }

  .timeline-head-call {
    display: block;
  }

  .timeline-call {
    grid-column: 2 !important;
  }

  .timeline-lane {
    display: block;
    font-size: 10.5px;
    color: var(--site-faint);
  }
}
</style>
