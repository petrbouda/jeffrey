<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  <div
    ref="overlayRef"
    class="modal modal-overlay"
    :class="{ 'd-block': show, 'd-none': !show }"
    :id="modalId"
    tabindex="-1"
    :aria-labelledby="modalId + 'Label'"
    @keyup.esc="closeModal"
    @click.self="closeModal"
  >
    <div class="modal-dialog" :class="[modalSizeClass, modalDialogClass]" :style="fullscreenStyle">
      <div class="modal-content">
        <div class="modal-header">
          <slot name="header">
            <h5 class="modal-title" :id="modalId + 'Label'">
              <i v-if="icon" :class="icon + ' me-2'"></i>
              {{ title }}
            </h5>
            <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
          </slot>
        </div>
        <div class="modal-body">
          <slot></slot>
        </div>
        <div class="modal-footer" v-if="showFooter">
          <slot name="footer">
            <button type="button" class="btn btn-secondary" @click="closeModal">Close</button>
          </slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';

interface Props {
  modalId: string;
  show: boolean;
  title?: string;
  icon?: string;
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'fullscreen';
  modalDialogClass?: string;
  showFooter?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  size: 'lg',
  modalDialogClass: '',
  showFooter: true
});

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'shown'): void;
  (e: 'hidden'): void;
}>();

const overlayRef = ref<HTMLElement | null>(null);

const closeModal = () => {
  emit('update:show', false);
};

const modalSizeClass = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'modal-sm';
    case 'md':
      return '';
    case 'lg':
      return 'modal-lg';
    case 'xl':
      return 'modal-xl';
    case 'fullscreen':
      return 'modal-lg';
    default:
      return '';
  }
});

const fullscreenStyle = computed(() => {
  if (props.size === 'fullscreen') {
    return { width: '95vw', maxWidth: '95%' };
  }
  return undefined;
});

watch(
  () => props.show,
  newVal => {
    if (newVal) {
      nextTick(() => {
        overlayRef.value?.focus();
        emit('shown');
      });
    } else {
      emit('hidden');
    }
  }
);
</script>

<style scoped>
.modal-content {
  animation: modalSlideIn 0.2s ease-out;
}

@keyframes modalSlideIn {
  from {
    transform: translateY(-10px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
</style>

<!-- Base overlay styles provided by global .modal-overlay class in styles.scss -->
