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
import type { ReplayRun } from '@/types/pluginUsage';

// One dot per recorded run of a case: green passed, orange failed, grey when the run was not graded.

defineProps<{ runs: ReplayRun[] }>();

function runState(passed: boolean | null): string {
  if (passed === null) {
    return 'unknown';
  }
  return passed ? 'passed' : 'failed';
}
</script>

<template>
  <span class="run-dots" :aria-label="runs.map((run) => `run ${run.run} ${runState(run.passed)}`).join(', ')">
    <i v-for="run in runs" :key="run.run" class="run-dot" :class="`run-dot--${runState(run.passed)}`"></i>
  </span>
</template>

<style scoped>
.run-dots {
  display: inline-flex;
  flex: none;
  gap: 3px;
}

.run-dot {
  width: 7px;
  height: 7px;
  border-radius: var(--ap-radius-pill);
}

.run-dot--passed {
  background: var(--ap-ok);
}

.run-dot--failed {
  background: var(--ap-no);
}

.run-dot--unknown {
  background: var(--site-line-strong);
}
</style>
