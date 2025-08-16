<template>
  <div class="modal fade" :id="modalId" tabindex="-1"
       :aria-labelledby="modalId + 'Label'" aria-hidden="true">
    <div class="modal-dialog" :class="modalSizeClass">
      <div class="modal-content modern-modal-content shadow">
        <!-- Header -->
        <div class="modal-header modern-modal-header border-bottom-0">
          <div class="d-flex align-items-center">
            <i :class="['bi', icon, 'fs-4', 'me-2', iconColor || 'text-primary']"></i>
            <h5 class="modal-title mb-0 text-dark" :id="modalId + 'Label'">{{ title }}</h5>
          </div>
          <button type="button" class="btn-close" @click="handleCancel"></button>
        </div>

        <!-- Body -->
        <div class="modal-body pt-4">
          <!-- Description Card (Optional) -->
          <div v-if="showDescriptionCard && $slots.description" class="modal-description-card mb-4">
            <div class="description-content">
              <slot name="description"></slot>
            </div>
          </div>

          <!-- Main Body Content -->
          <slot name="body"></slot>
        </div>
        
        <!-- Validation Errors -->
        <div v-if="validationErrors.length > 0" class="alert alert-danger mx-3 mb-3">
          <div v-for="(error, idx) in validationErrors" :key="idx">
            <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ error }}
          </div>
        </div>

        <!-- Footer -->
        <div class="modal-footer border-top-0">
          <slot name="footer-extra"></slot>
          <button type="button" class="btn btn-light" @click="handleCancel">
            Cancel
          </button>
          <button 
            type="button" 
            class="btn btn-primary" 
            @click="handleSubmit"
            :disabled="loading"
          >
            <span v-if="loading" class="spinner-border spinner-border-sm me-2" role="status"></span>
            <i v-if="!loading && primaryButtonIcon" :class="['bi', primaryButtonIcon, 'me-1']"></i>
            {{ primaryButtonText }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as bootstrap from 'bootstrap';

interface Props {
  modalId: string;
  title: string;
  icon: string;
  iconColor?: string;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  primaryButtonText: string;
  primaryButtonIcon?: string;
  enableEnterKey?: boolean;
  showDescriptionCard?: boolean;
  loading?: boolean;
}

interface Emits {
  (e: 'submit'): void;
  (e: 'cancel'): void;
  (e: 'shown'): void;
  (e: 'hidden'): void;
}

const props = withDefaults(defineProps<Props>(), {
  iconColor: 'text-primary',
  size: 'lg',
  primaryButtonIcon: undefined,
  enableEnterKey: true,
  showDescriptionCard: false,
  loading: false
});

const emit = defineEmits<Emits>();

// Modal instance and element references
let modalInstance: bootstrap.Modal | null = null;
let modalElement: HTMLElement | null = null;

// Validation errors
const validationErrors = ref<string[]>([]);

// Computed modal size class
const modalSizeClass = computed(() => {
  switch (props.size) {
    case 'sm': return 'modal-sm';
    case 'md': return '';
    case 'lg': return 'modal-lg';
    case 'xl': return 'modal-xl';
    default: return 'modal-lg';
  }
});

// Handle Enter key press
const handleKeydown = (event: KeyboardEvent) => {
  if (props.enableEnterKey && event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !event.altKey) {
    event.preventDefault();
    handleSubmit();
  }
};

// Handle submit
const handleSubmit = () => {
  if (!props.loading) {
    emit('submit');
  }
};

// Handle cancel
const handleCancel = () => {
  emit('cancel');
};

// Show modal
const showModal = () => {
  if (modalInstance) {
    validationErrors.value = [];
    modalInstance.show();
  }
};

// Hide modal
const hideModal = () => {
  if (modalInstance) {
    modalInstance.hide();
  }
};

// Set validation errors
const setValidationErrors = (errors: string[]) => {
  validationErrors.value = errors;
};

// Clear validation errors
const clearValidationErrors = () => {
  validationErrors.value = [];
};

// Initialize modal
onMounted(() => {
  nextTick(() => {
    const modalEl = document.getElementById(props.modalId);
    if (modalEl) {
      modalElement = modalEl;
      modalInstance = new bootstrap.Modal(modalEl);
      
      // Add event listeners
      modalEl.addEventListener('shown.bs.modal', () => {
        emit('shown');
      });

      modalEl.addEventListener('hidden.bs.modal', () => {
        emit('hidden');
      });

      // Add Enter key listener if enabled
      if (props.enableEnterKey) {
        modalEl.addEventListener('keydown', handleKeydown);
      }
    }
  });
});

// Cleanup event listeners
onBeforeUnmount(() => {
  if (modalElement) {
    if (props.enableEnterKey) {
      modalElement.removeEventListener('keydown', handleKeydown);
    }
  }
});

// Expose methods for parent components
defineExpose({
  showModal,
  hideModal,
  setValidationErrors,
  clearValidationErrors
});
</script>

<style scoped>
/* Modern Modal Styling */
.modern-modal-content {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 
    0 20px 40px rgba(0, 0, 0, 0.08),
    0 8px 24px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(10px);
}

.modern-modal-header {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.05), rgba(94, 100, 255, 0.08));
  border-radius: 16px 16px 0 0;
  padding: 20px 24px;
}

/* Modal Description Card */
.modal-description-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 0;
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.02);
}

.description-content {
  padding: 20px 24px;
}

.description-content p {
  font-size: 0.9rem;
  line-height: 1.5;
  color: #374151;
  margin-bottom: 0.5rem;
}

.description-content p:last-child {
  margin-bottom: 0;
}

.description-content .text-muted {
  font-size: 0.85rem;
  font-style: italic;
}

/* Button Styling */
.btn-light {
  background: linear-gradient(135deg, #f8f9fa, #e9ecef);
  border: 1px solid rgba(108, 117, 125, 0.2);
  color: #6c757d;
  font-weight: 500;
  border-radius: 10px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:hover {
    background: linear-gradient(135deg, #e9ecef, #dee2e6);
    color: #495057;
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
}

.btn-primary {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border: none;
  font-weight: 600;
  border-radius: 10px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4px 12px rgba(94, 100, 255, 0.3),
    0 2px 4px rgba(94, 100, 255, 0.2);
  
  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #4c52ff, #3f46ff);
    transform: translateY(-2px);
    box-shadow: 
      0 6px 16px rgba(94, 100, 255, 0.4),
      0 3px 6px rgba(94, 100, 255, 0.3);
  }

  &:active:not(:disabled) {
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
  }
}

/* Special color classes */
.text-purple {
  color: #6f42c1;
}
</style>