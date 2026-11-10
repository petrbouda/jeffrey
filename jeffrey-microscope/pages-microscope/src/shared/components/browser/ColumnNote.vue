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
  The quiet line a BrowserColumn shows instead of entries: nothing picked above it yet, or a
  listing that came back empty. Sized for a narrow column, where an EmptyState would not fit.
-->

<template>
  <div class="column-note">
    <i :class="['bi', icon]"></i>
    <span><slot></slot></span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** A Bootstrap icon class, e.g. `bi-folder`. */
  icon: string;
}>();
</script>

<style scoped>
.column-note {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: var(--spacing-5) var(--spacing-3);
  text-align: center;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.column-note i {
  font-size: 1.4rem;
  color: var(--color-text-light);
}
</style>
