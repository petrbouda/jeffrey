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
  <div class="table-card">
    <slot name="toolbar" />
    <div class="table-responsive">
      <table :class="['table', 'table-sm', 'table-hover', 'mb-0', tableClass]">
        <slot />
      </table>
    </div>
    <slot name="footer" />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    tableClass?: string;
  }>(),
  {
    tableClass: undefined
  }
);
</script>

<style scoped>
.table-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
  overflow: hidden;
}
</style>
