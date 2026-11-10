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
  <table class="summary-table">
    <tbody>
      <tr v-for="(item, index) in items" :key="index">
        <td>{{ item.label }}</td>
        <td>{{ item.value }}</td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
export interface SummaryItem {
  label: string;
  value: string;
}

defineProps<{
  items: SummaryItem[];
}>();
</script>

<style scoped>
.summary-table {
  width: 100%;
  border-collapse: collapse;
}

.summary-table td {
  padding: 0.6rem 0.75rem;
  font-size: 0.85rem;
  border-bottom: 1px solid var(--color-border-light);
}

.summary-table tr:last-child td {
  border-bottom: none;
}

.summary-table td:first-child {
  color: var(--color-text-muted);
}

.summary-table td:last-child {
  text-align: right;
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: var(--color-dark);
}
</style>
