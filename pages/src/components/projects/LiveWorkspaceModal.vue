<template>
  <BaseModal
    modal-id="liveWorkspaceModal"
    title="Add New Workspace"
    icon="bi-plus-circle"
    primary-button-text="Add Workspace"
    primary-button-icon="bi-plus-lg"
    :enable-enter-key="true"
    :loading="isLoading"
    @submit="addWorkspace"
    @cancel="closeModal"
    @shown="handleShown"
    @hidden="handleHidden"
    ref="modalRef"
  >
    <template #description>
      <p class="text-muted mb-0">
        Create a new workspace to organize your projects by environment or team.
      </p>
    </template>

    <template #body>
      <FormInput
        v-model="workspaceName"
        label="Name"
        icon="bi-grid-3x3-gap"
        placeholder="Enter workspace name"
        @input="updateWorkspaceId"
      />

      <FormInput
        v-model="workspaceId"
        label="Workspace ID"
        icon="bi-hash"
        placeholder="ID to reference this workspace"
        @input="validateWorkspaceId"
      />

      <FormInput
        v-model="workspaceDescription"
        label="Description"
        icon="bi-file-text"
        placeholder="Enter workspace description"
      />

      <FormInput
        v-model="workspacePath"
        label="Directory Path"
        icon="bi-folder"
        :placeholder="useCustomPath ? 'Using JEFFREY_HOME as default' : 'Enter custom directory path'"
        :disabled="useCustomPath"
        :show-checkbox="true"
        :checkbox-value="useCustomPath"
        @update:checkbox-value="useCustomPath = $event"
        help-text='Use <span style="color: #dc3545;">JEFFREY_HOME</span> as default directory'
      />
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import BaseModal from '@/components/BaseModal.vue';
import FormInput from '@/components/form/FormInput.vue';
import WorkspaceClient from '@/services/workspace/WorkspaceClient';
import CreateWorkspaceRequest from '@/services/workspace/model/CreateWorkspaceRequest';
import ToastService from '@/services/ToastService';
import SlugService from '@/services/SlugService';
import {useModal} from '@/composables/useModal';
import WorkspaceType from "@/services/workspace/model/WorkspaceType.ts";

interface Emits {
  (e: 'workspace-created'): void;
  (e: 'modal-closed'): void;
}

const emit = defineEmits<Emits>();

// Modal reference and composable
const modalRef = ref<InstanceType<typeof BaseModal>>();
const {
  isLoading,
  showModal,
  hideModal,
  handleModalShown,
  handleModalHidden,
  handleAsyncSubmit,
  setValidationErrors
} = useModal(modalRef);

// Form data
const workspaceName = ref('');
const workspaceDescription = ref('');
const workspaceId = ref('');
const workspacePath = ref('');
const useCustomPath = ref(true);

// Reset form
const resetForm = () => {
  workspaceName.value = '';
  workspaceDescription.value = '';
  workspaceId.value = '';
  workspacePath.value = '';
  useCustomPath.value = true;
};

// Close modal
const closeModal = () => {
  hideModal();
  emit('modal-closed');
};

// Auto-generate workspace ID from name
const updateWorkspaceId = () => {
  workspaceId.value = SlugService.generateSlug(workspaceName.value);
};

// Validate workspace ID input
const validateWorkspaceId = () => {
  workspaceId.value = SlugService.validateSlug(workspaceId.value);
};

// Add workspace
const addWorkspace = async () => {
  await handleAsyncSubmit(
    async () => {
      // Validation
      if (!workspaceName.value.trim() || !workspaceId.value.trim()) {
        throw new Error('Name and Workspace ID are required');
      }

      if (!useCustomPath.value && !workspacePath.value.trim()) {
        throw new Error('Directory Path is required when not using JEFFREY_HOME');
      }

      const request = new CreateWorkspaceRequest(
        workspaceId.value.trim(),
        WorkspaceType.LIVE,
        workspaceName.value.trim(),
        workspaceDescription.value.trim() || undefined,
        useCustomPath.value ? undefined : workspacePath.value.trim() || undefined
      );

      const createdWorkspace = await WorkspaceClient.create(request);

      ToastService.success('Workspace Added!', `"${createdWorkspace.name}" workspace has been created`);

      emit('workspace-created');
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
  setValidationErrors
});
</script>

<style scoped>
/* Styles handled by BaseModal and FormInput components */
</style>
