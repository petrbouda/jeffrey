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

<template>
  <div class="field-group">
    <label class="field-label">
      {{ label }}
      <span v-if="required" class="field-required">*</span>
    </label>
    <div class="field-wrap" :class="{ 'is-disabled': disabled, 'is-invalid': !!error }">
      <slot></slot>
    </div>
    <div v-if="error" class="field-error">
      <i class="bi bi-exclamation-circle"></i>
      <span>{{ error }}</span>
    </div>
    <div v-else-if="hint || $slots.hint" class="field-hint">
      <slot name="hint">{{ hint }}</slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import '@shared/styles/shared-components.css';

defineProps<{
  label: string;
  required?: boolean;
  hint?: string;
  error?: string;
  disabled?: boolean;
}>();
</script>
