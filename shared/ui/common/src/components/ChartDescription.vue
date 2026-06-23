<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
      <span class="chart-description-separator">|</span>
      <span class="chart-description-label">Use case:</span> <slot name="use-case">{{ useCase }}</slot>
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
