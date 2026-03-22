<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<template>
  <div class="search-box">
    <div class="input-group input-group-sm phoenix-search">
      <span class="input-group-text border-0 ps-3 pe-0 search-icon-container">
        <i class="bi bi-search text-primary"></i>
      </span>
      <input
        type="text"
        class="form-control border-0 py-2"
        :value="modelValue"
        :placeholder="placeholder"
        @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      >
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  modelValue: string;
  placeholder?: string;
}

interface Emits {
  (e: 'update:modelValue', value: string): void;
}

withDefaults(defineProps<Props>(), {
  placeholder: 'Search...'
});

defineEmits<Emits>();
</script>

<style scoped>
.search-box {
  flex: 1;
  max-width: 600px;
}

.phoenix-search {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.12);
  overflow: hidden;
  border-radius: 12px;
  height: 48px;
  box-shadow:
    inset 0 1px 3px rgba(0, 0, 0, 0.05),
    0 1px 2px rgba(0, 0, 0, 0.02);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.phoenix-search:focus-within {
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow:
    inset 0 1px 3px rgba(0, 0, 0, 0.05),
    0 4px 12px rgba(94, 100, 255, 0.1),
    0 0 0 3px rgba(94, 100, 255, 0.05);
  transform: translateY(-1px);
}

.phoenix-search .search-icon-container {
  width: 48px;
  display: flex;
  justify-content: center;
  background: transparent;
  border: none;
  color: #6c757d;
}

.phoenix-search .form-control {
  height: 46px;
  font-size: 0.9rem;
  background: transparent;
  border: none;
  color: #374151;
  font-weight: 500;
}

.phoenix-search .form-control::placeholder {
  color: #9ca3af;
  font-weight: 400;
}

.phoenix-search .form-control:focus {
  box-shadow: none;
  background: transparent;
}
</style>
