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

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import GenericModal from '@shared/components/GenericModal.vue';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    title?: string;
    placeholder?: string;
    submitLabel?: string;
  }>(),
  {
    title: 'Edit Name',
    placeholder: 'Enter profile name',
    submitLabel: 'Update'
  }
);

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
    size="md"
    modal-dialog-class="modal-dialog-centered"
    @update:show="emit('close')"
  >
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
      />
    </template>
    <template #footer>
      <button class="btn btn-secondary" @click="emit('close')">Cancel</button>
      <button class="btn btn-primary" :disabled="!modelValue.trim()" @click="emit('submit')">
        {{ submitLabel }}
      </button>
    </template>
  </GenericModal>
</template>

<style scoped>
.enm-input {
  width: 100%;
  border: 1px solid var(--color-border-input);
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 0.9rem;
  transition: border-color 0.15s ease;
}

.enm-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}
</style>
