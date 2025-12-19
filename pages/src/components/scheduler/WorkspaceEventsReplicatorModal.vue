<template>
  <BaseModal
    :modal-id="modalId"
    title="Create a Workspace Events Replicator Job"
    icon="bi-database-up"
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
        This job automatically replicates <strong class="text-primary">project and session creation events</strong> from the remote workspace repository into the local event log.
      </p>
      <p class="mb-0 text-muted">
        This enables comprehensive workspace activity tracking and provides an audit trail of all workspace changes. The job runs periodically and processes new events as they occur.
      </p>
    </template>

    <template #body>
      <!-- No form inputs needed - job has no parameters -->
      <div class="alert alert-info mb-0">
        <i class="bi bi-info-circle me-2"></i>
        <strong>No configuration required.</strong> This job will automatically start replicating events once created.
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

// Close modal
const closeModal = () => {
  hideModal();
  emit('modal-closed');
};

// Create job
const createJob = async () => {
  await handleAsyncSubmit(
    async () => {
      // No parameters needed - emit empty params object
      emit('job-created', {});
    },
    () => {
      // On success, don't auto-hide modal - let parent handle it
    }
  );
};

// Handle modal shown
const handleShown = () => {
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
/* Styles handled by BaseModal component */
</style>
