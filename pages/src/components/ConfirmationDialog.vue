<script setup lang="ts">
import {nextTick, watch} from 'vue';

const props = defineProps({
  // Main control state
  show: {
    type: Boolean,
    required: true
  },
  // Dialog content
  title: {
    type: String,
    default: 'Confirm Action'
  },
  message: {
    type: String,
    required: true
  },
  subMessage: {
    type: String,
    default: ''
  },
  // Dialog customization
  confirmLabel: {
    type: String,
    default: 'Confirm'
  },
  cancelLabel: {
    type: String,
    default: 'Cancel'
  },
  confirmButtonClass: {
    type: String,
    default: 'btn-danger'
  },
  confirmButtonId: {
    type: String,
    default: 'confirm-button'
  },
  modalId: {
    type: String,
    default: 'confirmation-dialog'
  }
});

const emit = defineEmits(['confirm', 'cancel', 'update:show']);

// Focus modal when shown
watch(() => props.show, (newVal) => {
  if (newVal) {
    nextTick(() => {
      const modal = document.querySelector('.modal.d-block');
      if (modal) {
        modal.focus();
      }
    });
  }
});

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
  <div class="modal"
       :class="{ 'd-block': show, 'd-none': !show }"
       @keyup.enter="onConfirm"
       tabindex="-1"
       :id="modalId">
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
          <button type="button" class="btn btn-secondary" @click="onCancel">{{ cancelLabel }}</button>
          <button
              type="button"
              class="btn"
              :class="confirmButtonClass"
              @click="onConfirm"
              :id="confirmButtonId">{{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal {
  background-color: rgba(0, 0, 0, 0.5);
}

.modal-header {
  padding: 1rem;
}

.btn-close {
  margin: -0.5rem -0.5rem -0.5rem auto;
}

.d-block {
  display: block !important;
}

.d-none {
  display: none !important;
}
</style>
