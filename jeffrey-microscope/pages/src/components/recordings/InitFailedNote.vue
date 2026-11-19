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

<!-- The line under a recording whose initialization failed, naming the stage it failed in. -->
<script setup lang="ts">
defineProps<{
  /** The stage's label, or null when the run reported no failed stage. */
  stage: string | null;
}>();
</script>

<template>
  <div class="init-failed-note">
    <i class="bi bi-exclamation-octagon-fill"></i>
    <span v-if="stage">Initialization failed during {{ stage }}.</span>
    <span v-else>Initialization failed.</span>
  </div>
</template>

<style scoped>
.init-failed-note {
  display: flex;
  align-items: center;
  gap: var(--spacing-1);
  font-size: 0.7rem;
  color: var(--color-danger);
}
</style>
