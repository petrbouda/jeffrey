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
  Standardized search input field. Wraps the shared `.search-container`
  markup pattern (defined in `styles/shared-components.css`) as a Vue
  component so callers don't need to repeat the input-group / icon /
  clear-button structure.

  Usage:
    <SearchInput v-model="query" placeholder="Search by session ID..." />

  Two-way binding via `v-model`. Emits no other events. The clear
  button shows automatically when the value is non-empty.
-->

<script setup lang="ts">
import '@shared/styles/shared-components.css';

defineProps<{
  placeholder?: string;
  ariaLabel?: string;
}>();

const value = defineModel<string>({ default: '' });

const clear = () => {
  value.value = '';
};
</script>

<template>
  <div class="input-group search-container">
    <span class="input-group-text">
      <i class="bi bi-search search-icon"></i>
    </span>
    <input
      v-model="value"
      type="text"
      class="form-control search-input"
      :placeholder="placeholder"
      :aria-label="ariaLabel ?? placeholder"
    />
    <button
      v-if="value"
      class="btn btn-outline-secondary clear-btn"
      type="button"
      :aria-label="'Clear search'"
      @click="clear"
    >
      <i class="bi bi-x-lg"></i>
    </button>
  </div>
</template>
