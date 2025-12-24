<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<template>
  <BaseModal
    :modal-id="modalId"
    title="Create Orphaned Project Storage Cleaner Job"
    icon="bi-trash3"
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
        This job automatically removes <strong class="text-primary">orphaned projects from recording storage</strong> that no longer exist in the database.
      </p>
      <p class="mb-0 text-muted">
        When a project is deleted from the database, its recordings may still remain in the storage. This job periodically scans for such orphaned projects and cleans them up to free storage space.
      </p>
    </template>

    <template #body>
      <!-- No form inputs needed - job has no parameters -->
      <div class="alert alert-info mb-0">
        <i class="bi bi-info-circle me-2"></i>
        <strong>No configuration required.</strong> This job will automatically start cleaning orphaned projects once created.
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
