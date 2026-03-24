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
  <div class="enm-overlay" @click.self="emit('close')">
    <div class="enm-modal">
      <div class="enm-header">
        <span class="enm-title">{{ title }}</span>
        <button class="enm-close" @click="emit('close')">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
      <div class="enm-body">
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
      </div>
      <div class="enm-footer">
        <button class="enm-btn-cancel" @click="emit('close')">Cancel</button>
        <button class="enm-btn-save" @click="emit('submit')" :disabled="!modelValue.trim()">{{ submitLabel }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.enm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1050;
}

.enm-modal {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  width: 380px;
  max-width: 90vw;
  animation: enmSlideIn 0.2s ease-out;
}

@keyframes enmSlideIn {
  from { transform: translateY(-8px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.enm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #e5e7eb;
}

.enm-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
}

.enm-close {
  background: transparent;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  font-size: 0.8rem;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.enm-close:hover {
  color: #374151;
  background: #f3f4f6;
}

.enm-body {
  padding: 16px 18px;
}

.enm-input {
  width: 100%;
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 0.8rem;
  transition: border-color 0.15s ease;
}

.enm-input:focus {
  outline: none;
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
}

.enm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  padding: 10px 18px;
  border-top: 1px solid #e5e7eb;
}

.enm-btn-cancel {
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  color: #374151;
  padding: 5px 14px;
  border-radius: 6px;
  font-size: 0.78rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.enm-btn-cancel:hover {
  background: #e5e7eb;
}

.enm-btn-save {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border: none;
  color: white;
  padding: 5px 14px;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.enm-btn-save:hover {
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.4);
}

.enm-btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
