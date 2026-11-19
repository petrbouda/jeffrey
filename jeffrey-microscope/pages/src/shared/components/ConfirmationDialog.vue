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
import { nextTick, watch } from 'vue';

interface Props {
  show: boolean;
  message: string;
  title?: string;
  subMessage?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  confirmButtonClass?: string;
  confirmButtonId?: string;
  modalId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  title: 'Confirm Action',
  subMessage: '',
  confirmLabel: 'Confirm',
  cancelLabel: 'Cancel',
  confirmButtonClass: 'btn-danger',
  confirmButtonId: 'confirm-button',
  modalId: 'confirmation-dialog'
});

interface Emits {
  (e: 'confirm'): void;
  (e: 'cancel'): void;
  (e: 'update:show', value: boolean): void;
}

const emit = defineEmits<Emits>();

// Focus modal when shown
watch(
  () => props.show,
  newVal => {
    if (newVal) {
      nextTick(() => {
        const modal = document.querySelector<HTMLElement>('.modal.d-block');
        if (modal) {
          modal.focus();
        }
      });
    }
  }
);

const onConfirm = () => {
  emit('confirm');
  emit('update:show', false);
};

const onCancel = () => {
  emit('cancel');
  emit('update:show', false);
};
</script>

<template>
  <div
    class="modal modal-overlay"
    :class="{ 'd-block': show, 'd-none': !show }"
    @keyup.enter="onConfirm"
    tabindex="-1"
    :id="modalId"
  >
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header d-flex justify-content-between align-items-center">
          <h5 class="modal-title">{{ title }}</h5>
          <button type="button" class="btn-close" @click="onCancel"></button>
        </div>
        <div class="modal-body">
          <p class="mb-0">{{ message }}</p>
          <p class="text-muted small" v-if="subMessage">{{ subMessage }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="onCancel">
            {{ cancelLabel }}
          </button>
          <button
            type="button"
            class="btn"
            :class="confirmButtonClass"
            @click="onConfirm"
            :id="confirmButtonId"
          >
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<!-- Modal overlay styles provided by global .modal-overlay class in styles.scss -->
