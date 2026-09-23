<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<!--
  Compact one-line description strip rendered above a timeseries chart: "Shows: … | Use case: …".
  Use the `shows` / `useCase` props for plain text, or the `#shows` / `#use-case` slots when the
  content needs markup (e.g. inline <code>) or conditionals.
-->
<template>
  <div class="chart-description">
    <span class="chart-description-text">
      <span class="chart-description-label">Shows:</span> <slot name="shows">{{ shows }}</slot>
      <template v-if="useCase || $slots['use-case']">
        <span class="chart-description-separator">|</span>
        <span class="chart-description-label">Use case:</span> <slot name="use-case">{{ useCase }}</slot>
      </template>
    </span>
    <span v-if="$slots.actions" class="chart-description-actions"><slot name="actions" /></span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  shows?: string;
  useCase?: string;
}>();
</script>

<style scoped>
.chart-description {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 0.5rem 1rem;
  margin-bottom: 0.5rem;
  font-size: 0.85rem;
  color: var(--color-text-muted);
  background-color: var(--color-light);
  border-radius: 4px;
}

.chart-description-text {
  flex: 1 1 auto;
  min-width: 0;
}

.chart-description-actions {
  flex: 0 0 auto;
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.chart-description-label {
  font-weight: 600;
  color: var(--color-text);
}

.chart-description-separator {
  margin: 0 0.75rem;
  color: var(--color-border);
}
</style>
