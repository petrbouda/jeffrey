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
