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

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import GenericModal from '@/components/GenericModal.vue';

const props = withDefaults(defineProps<{
  modelValue: string;
  title?: string;
  placeholder?: string;
  submitLabel?: string;
}>(), {
  title: 'Edit Profile',
  placeholder: 'Enter profile name',
  submitLabel: 'Update',
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'submit'): void;
  (e: 'close'): void;
}>();

const inputRef = ref<HTMLInputElement | null>(null);

const onInput = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
};

onMounted(() => {
  inputRef.value?.focus();
});
</script>

<template>
  <GenericModal
      modal-id="editNameModal"
      :show="true"
      :title="title"
      size="sm"
      @update:show="emit('close')">
    <template #default>
      <input
          ref="inputRef"
          :value="modelValue"
          type="text"
          class="enm-input"
          :placeholder="placeholder"
          @input="onInput"
          @keydown.enter="emit('submit')"
          @keydown.escape="emit('close')"
      >
    </template>
    <template #footer>
      <button class="btn btn-secondary btn-sm" @click="emit('close')">Cancel</button>
      <button class="btn btn-primary btn-sm" @click="emit('submit')" :disabled="!modelValue.trim()">{{ submitLabel }}</button>
    </template>
  </GenericModal>
</template>

<style scoped>
.enm-input {
  width: 100%;
  border: 1px solid var(--input-border-color);
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 0.8rem;
  transition: border-color 0.15s ease;
}

.enm-input:focus {
  outline: none;
  border-color: var(--input-focus-border-color);
  box-shadow: var(--input-focus-shadow);
}
</style>
