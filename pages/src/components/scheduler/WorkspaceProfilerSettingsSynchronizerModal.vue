<template>
  <BaseModal
    :modal-id="modalId"
    title="Create a Profiler Settings Synchronizer Job"
    icon="bi-cpu"
    icon-color="text-orange"
    primary-button-text="Save Job"
    primary-button-icon="bi-save"
    :show-description-card="true"
    :enable-enter-key="true"
    :loading="isLoading"
    @submit="createJob"
    @cancel="closeModal"
    @shown="handleShown"
    @hidden="handleHidden"
    ref="modalRef"
  >
    <template #description>
      <p class="mb-2">
        Synchronizes <strong class="text-primary">profiler agent settings</strong> from the database to remote workspace storage.
      </p>
      <p class="mb-0 text-muted">
        This ensures profiler configurations are backed up and versioned for disaster recovery. Legacy settings versions are automatically cleaned up based on the retention policy.
      </p>
    </template>

    <template #body>
      <div class="mb-4 row">
        <label for="maxVersionsInput" class="col-sm-3 col-form-label fw-medium">Max Versions</label>
        <div class="col-sm-9">
          <div class="mb-2">
            <div class="text-muted small">
              Number of legacy settings versions to retain before cleanup
            </div>
          </div>
          <input
            id="maxVersionsInput"
            type="number"
            class="form-control"
            v-model.number="maxVersions"
            :min="1"
            :max="100"
            placeholder="Enter max versions (1-100)"
            required
          />
        </div>
      </div>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import BaseModal from '@/components/BaseModal.vue';
import { useModal } from '@/composables/useModal';

interface Props {
  modalId: string;
}

interface Emits {
  (e: 'job-created', params: any): void;
  (e: 'modal-closed'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Modal reference and composable
const modalRef = ref<InstanceType<typeof BaseModal>>();
const {
  isLoading,
  showModal,
  hideModal,
  handleModalShown,
  handleModalHidden,
  handleAsyncSubmit
} = useModal(modalRef);

// Form data
const maxVersions = ref<number>(10);

// Reset form
const resetForm = () => {
  maxVersions.value = 10;
};

// Close modal
const closeModal = () => {
  hideModal();
  emit('modal-closed');
};

// Create job
const createJob = async () => {
  await handleAsyncSubmit(
    async () => {
      const params = {
        maxVersions: maxVersions.value
      };

      emit('job-created', params);
    },
    () => {
      // On success, don't auto-hide modal - let parent handle it
      resetForm();
    }
  );
};

// Handle modal shown
const handleShown = () => {
  resetForm();
  handleModalShown();
};

// Handle modal hidden
const handleHidden = () => {
  handleModalHidden();
  emit('modal-closed');
};

// Expose methods for parent component
defineExpose({
  showModal,
  closeModal: hideModal,
  setValidationErrors: (errors: string[]) => {
    if (modalRef.value) {
      modalRef.value.setValidationErrors(errors);
    }
  }
});
</script>

<style scoped>
.fw-medium {
  font-weight: 500;
}

.form-control {
  border: 1px solid #ced4da;
  height: 38px;
}

.form-control:focus {
  box-shadow: none;
  border-color: #ced4da;
}

/* Remove spinner arrows for number input */
input[type="number"]::-webkit-inner-spin-button,
input[type="number"]::-webkit-outer-spin-button {
  opacity: 1;
}
</style>