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
import type { PluginWorkload } from '@/types/pluginUsage';

// What an eval case ran on: a real service's recorded runs, or a workload made up for the evals. The
// workload's own description is in the title; compact shows the icon alone, for a list's rows.

defineProps<{ workload: PluginWorkload; compact?: boolean }>();
</script>

<template>
  <span
    class="workload-mark"
    :class="[workload.real ? 'workload-mark--real' : 'workload-mark--made-up', { 'workload-mark--compact': compact }]"
    :title="`${workload.label}: ${workload.detail}`"
    :aria-label="compact ? (workload.real ? 'Real service' : 'Made-up workload') : undefined"
    :role="compact ? 'img' : undefined"
  >
    <i class="bi" :class="workload.real ? 'bi-activity' : 'bi-pencil'" aria-hidden="true"></i>
    <template v-if="!compact">{{ workload.real ? 'Real service' : 'Made-up' }}</template>
  </span>
</template>

<style scoped>
.workload-mark {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;
  padding: 0 7px;
  border: 1px solid transparent;
  border-radius: var(--ap-radius-pill);
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
}

.workload-mark--compact {
  justify-content: center;
  width: 20px;
  padding: 0;
}

.workload-mark .bi {
  font-size: 10px;
}

.workload-mark--real {
  background: var(--ap-real-bg);
  color: var(--ap-real);
}

.workload-mark--made-up {
  border-color: var(--site-line-strong);
  border-style: dashed;
  color: var(--site-faint);
}
</style>
