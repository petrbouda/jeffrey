<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import '@/styles/shared-components.css'

defineProps<{
  placeholder?: string
  ariaLabel?: string
}>()

const value = defineModel<string>({ default: '' })

const clear = () => {
  value.value = ''
}
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
