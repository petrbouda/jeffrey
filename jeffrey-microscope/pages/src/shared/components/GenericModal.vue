<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  <div
    ref="overlayRef"
    class="modal modal-overlay"
    :class="{ 'd-block': show, 'd-none': !show }"
    :id="modalId"
    tabindex="-1"
    :aria-labelledby="modalId + 'Label'"
    @keyup.esc="closeModal"
    @click.self="onBackdropClick"
  >
    <div class="modal-dialog" :class="[modalSizeClass, modalDialogClass]" :style="fullscreenStyle">
      <div class="modal-content">
        <div class="modal-header">
          <slot name="header">
            <h5 class="modal-title" :id="modalId + 'Label'">
              <i v-if="icon" :class="icon + ' me-2'"></i>
              <slot name="title">{{ title }}</slot>
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
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue';
import { lockBodyScroll, unlockBodyScroll } from '@shared/composables/useScrollLock';

interface Props {
  modalId: string;
  show: boolean;
  title?: string;
  icon?: string;
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'fullscreen';
  modalDialogClass?: string;
  showFooter?: boolean;
  /**
   * Whether clicking the backdrop closes the dialog. A host whose dialog holds deep, accumulated
   * state (a drill-down several levels in) turns this off so a stray click beside the content
   * cannot discard all of it — Escape and the close button still work.
   */
  backdropClose?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  // Default to the large/immersive size used by data modals (flamegraphs, span events, …).
  // Confirmation / info / small-form modals should opt down explicitly (size="sm" / "md" / "lg").
  size: 'fullscreen',
  modalDialogClass: '',
  showFooter: true,
  backdropClose: true
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

const onBackdropClick = () => {
  if (props.backdropClose) {
    closeModal();
  }
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

// Lock page scrolling while the modal is open so scrolling inside it never chains to the page
// behind. Per-instance flag keeps the ref-counted body lock balanced across show/hide and unmount.
let scrollLocked = false;

function applyScrollLock(shouldLock: boolean): void {
  if (shouldLock && !scrollLocked) {
    lockBodyScroll();
    scrollLocked = true;
  } else if (!shouldLock && scrollLocked) {
    unlockBodyScroll();
    scrollLocked = false;
  }
}

watch(
  () => props.show,
  newVal => {
    applyScrollLock(newVal);
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

onMounted(() => applyScrollLock(props.show));
onUnmounted(() => applyScrollLock(false));
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
