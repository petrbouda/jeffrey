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

<!--
  What goes under a recording's row while it initializes or after a run failed: the stages of the
  run, and the stage it failed in. Nothing at all otherwise.
-->
<script setup lang="ts">
import { computed } from 'vue';
import StageChips from '@shared/components/StageChips.vue';
import type Recording from '../services/api/model/Recording';
import InitFailedNote from './InitFailedNote.vue';
import { memberState } from './bundleSummary';
import { failedStageLabel, toTimelineSteps } from './profileInitChips';
import { recordingKindOf } from './recordingKinds';

const props = withDefaults(
  defineProps<{
    recording: Recording;
    /** Whether the recording's Analyze request is still in flight. */
    analyzing?: boolean;
    /** Epoch millis the parent ticks while an initialization is running. */
    tickNow?: number;
  }>(),
  {
    analyzing: false,
    tickNow: 0
  }
);

const state = computed(() => memberState(props.recording, props.analyzing));
const chips = computed(() => recordingKindOf(props.recording.eventSource).chips);
const steps = computed(() => toTimelineSteps(props.recording.initProgress, props.tickNow));
const showsStages = computed(
  () => (state.value === 'running' || state.value === 'failed') && chips.value.length > 0
);
const failedStage = computed(() => failedStageLabel(props.recording.initProgress, chips.value));
</script>

<template>
  <StageChips
    v-if="showsStages"
    class="recording-init-status"
    :groups="chips"
    :steps="steps"
    :tick-now="tickNow"
  />
  <InitFailedNote v-if="state === 'failed'" class="recording-init-status" :stage="failedStage" />
</template>

<style scoped>
.recording-init-status {
  margin-left: var(--spacing-8);
}
</style>
