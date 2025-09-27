<template>
  <BaseModal
      modal-id="mirrorWorkspaceModal"
      title="Connect Remote Workspace"
      icon="bi-cloud-download"
      icon-color="text-info"
      primary-button-text="Connect Workspace"
      primary-button-icon="bi-arrow-down-circle"
      size="xl"
      :enable-enter-key="true"
      :show-description-card="true"
      :loading="isLoading"
      @submit="mirrorWorkspace"
      @cancel="closeModal"
      @shown="handleShown"
      @hidden="handleHidden"
      ref="modalRef"
  >
    <template #description>
      <p class="text-muted mb-2">
        Connect to an external Jeffrey instance to access one of its workspaces remotely.
      </p>
      <p class="text-muted mb-0 small">
        <i class="bi bi-info-circle me-1"></i>
        Remote workspaces are read-only and synchronized from the external source.
      </p>
    </template>

    <template #body>
      <!-- Phase 1: URL Input -->
      <div v-if="!showWorkspaceSelection">
        <FormInput
            v-model="remoteUrl"
            label="External Jeffrey API URL"
            icon="bi-globe"
            placeholder="https://prod-jeffrey.company.com"
            @input="validateUrl"
        />

        <div class="d-flex justify-content-end mb-3">
          <button
              type="button"
              class="btn btn-outline-info"
              @click="loadRemoteWorkspaces"
              :disabled="!urlValid || loadingWorkspaces"
          >
            <span v-if="loadingWorkspaces" class="spinner-border spinner-border-sm me-2" role="status"></span>
            <i v-else class="bi bi-cloud-arrow-down me-1"></i>
            {{ loadingWorkspaces ? 'Loading...' : 'Load Workspaces' }}
          </button>
        </div>
      </div>

      <!-- Phase 2: Workspace Selection -->
      <div v-else>
        <div class="mb-3">
          <label class="fw-medium mb-2">
            <i class="bi bi-grid-3x3-gap me-2 text-info"></i>
            Select Workspaces to Connect
          </label>
          <p class="text-muted small mb-3">
            Choose one or more workspaces from <strong>{{ remoteUrl }}</strong> to connect remotely.
          </p>
        </div>

        <!-- Remote Workspaces Grid -->
        <div v-if="remoteWorkspaces.length > 0" class="remote-workspaces-grid">
          <div
              v-for="workspace in remoteWorkspaces"
              :key="workspace.id"
              class="remote-workspace-card"
              :class="{ 'selected': selectedWorkspaceIds.includes(workspace.id) }"
              @click="toggleWorkspaceSelection(workspace.id)"
          >
            <div class="workspace-card-content">
              <div class="workspace-card-header">
                <div class="workspace-name-container">
                  <div class="workspace-selection-icon">
                    <i
                        :class="selectedWorkspaceIds.includes(workspace.id) ? 'bi bi-check-circle-fill' : 'bi bi-circle'"
                        class="selection-icon"
                    ></i>
                  </div>
                  <div class="workspace-info">
                    <div class="workspace-name-row">
                      <div class="workspace-name-with-icon">
                        <i class="bi bi-display external-icon"></i>
                        <h6 class="workspace-name">{{ workspace.name }}</h6>
                      </div>
                      <Badge
                          :value="`${workspace.projectCount} projects`"
                          variant="teal"
                          size="xs"
                          :uppercase="false"
                      />
                    </div>
                  </div>
                </div>
              </div>
              <div class="workspace-card-description">
                {{ workspace.description || `Projects for ${workspace.name}` }}
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-else class="text-center py-4">
          <i class="bi bi-inbox fs-1 text-muted mb-3"></i>
          <h6 class="text-muted">No workspaces found</h6>
          <p class="text-muted small mb-0">The remote Jeffrey instance has no available workspaces to connect.</p>
        </div>

        <!-- Back Button -->
        <div class="d-flex justify-content-start mt-3">
          <button
              type="button"
              class="btn btn-outline-secondary btn-sm"
              @click="goBackToUrlInput"
          >
            <i class="bi bi-arrow-left me-1"></i>
            Change URL
          </button>
        </div>
      </div>

      <!-- Error Messages -->
      <div v-if="errorMessage" class="alert alert-danger mt-3">
        <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ errorMessage }}
      </div>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import BaseModal from '@/components/BaseModal.vue';
import FormInput from '@/components/form/FormInput.vue';
import Badge from '@/components/Badge.vue';
import Workspace from '@/services/workspace/model/Workspace';
import ToastService from '@/services/ToastService';
import {useModal} from '@/composables/useModal';
import RemoteWorkspaceClient from "@/services/workspace/RemoteWorkspaceClient.ts";

interface Emits {
  (e: 'workspace-added'): void;

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

// Form states
const remoteUrl = ref('');
const showWorkspaceSelection = ref(false);
const loadingWorkspaces = ref(false);
const remoteWorkspaces = ref<Workspace[]>([]);
const selectedWorkspaceIds = ref<string[]>([]);
const errorMessage = ref('');

// URL validation
const urlValid = computed(() => {
  return remoteUrl.value.trim().length > 0;
});

// Reset form
const resetForm = () => {
  remoteUrl.value = '';
  showWorkspaceSelection.value = false;
  loadingWorkspaces.value = false;
  remoteWorkspaces.value = [];
  selectedWorkspaceIds.value = [];
  errorMessage.value = '';
};

// Close modal
const closeModal = () => {
  hideModal();
  emit('modal-closed');
};

// Validate URL input
const validateUrl = () => {
  errorMessage.value = '';
  // Remove trailing slash if present
  if (remoteUrl.value.endsWith('/')) {
    remoteUrl.value = remoteUrl.value.slice(0, -1);
  }
};

// Load remote workspaces
const loadRemoteWorkspaces = async () => {
  if (!urlValid.value) {
    errorMessage.value = 'Please enter a URL';
    return;
  }

  loadingWorkspaces.value = true;
  errorMessage.value = '';

  try {
    // Call the backend API to get available workspaces to add as remote
    remoteWorkspaces.value = await RemoteWorkspaceClient.listRemote(remoteUrl.value.trim());
    showWorkspaceSelection.value = true;

    if (remoteWorkspaces.value.length === 0) {
      ToastService.info('No Workspaces Found', 'The remote Jeffrey instance has no available workspaces.');
    } else {
      ToastService.success('Workspaces Loaded', `Found ${remoteWorkspaces.value.length} workspace(s) available for remote connection.`);
    }
  } catch (error) {
    console.error('Failed to load remote workspaces:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Failed to connect to the remote Jeffrey instance';
  } finally {
    loadingWorkspaces.value = false;
  }
};

// Toggle workspace selection (multiple selection)
const toggleWorkspaceSelection = (workspaceId: string) => {
  const index = selectedWorkspaceIds.value.indexOf(workspaceId);
  if (index > -1) {
    // Remove if already selected
    selectedWorkspaceIds.value.splice(index, 1);
  } else {
    // Add if not selected
    selectedWorkspaceIds.value.push(workspaceId);
  }
  errorMessage.value = '';
};

// Go back to URL input
const goBackToUrlInput = () => {
  showWorkspaceSelection.value = false;
  remoteWorkspaces.value = [];
  selectedWorkspaceIds.value = [];
  errorMessage.value = '';
};

// Mirror workspace
const mirrorWorkspace = async () => {
  if (!showWorkspaceSelection.value) {
    // If we're in URL phase, load workspaces instead
    await loadRemoteWorkspaces();
    return;
  }

  if (selectedWorkspaceIds.value.length === 0) {
    errorMessage.value = 'Please select at least one workspace to connect';
    return;
  }

  await handleAsyncSubmit(
      async () => {
        const selectedWorkspaces = remoteWorkspaces.value.filter(w => selectedWorkspaceIds.value.includes(w.id));
        if (selectedWorkspaces.length === 0) {
          throw new Error('Selected workspaces not found');
        }

        await RemoteWorkspaceClient.createRemote(
            remoteUrl.value.trim(),
            selectedWorkspaceIds.value
        );

        const selectedWorkspaceNames = selectedWorkspaces.map(w => w.name).join('", "');
        const successMessage = selectedWorkspaces.length === 1
            ? `"${selectedWorkspaceNames}" workspace has been connected successfully`
            : `"${selectedWorkspaceNames}" workspaces have been connected successfully`;

        ToastService.success('Workspaces Connected!', successMessage);

        emit('workspace-added');
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
/* Remote workspace selection styling */
.remote-workspaces-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 2px;
}

.remote-workspace-card {
  background: linear-gradient(135deg, #f0fdfa, #ccfbf1);
  border: 2px solid rgba(56, 178, 172, 0.2);
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04),
  0 1px 2px rgba(0, 0, 0, 0.02);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(56, 178, 172, 0.15),
    0 2px 8px rgba(56, 178, 172, 0.1);
    border-color: rgba(56, 178, 172, 0.3);
  }

  &.selected {
    background: linear-gradient(135deg, #38b2ac, #319795);
    border-color: #319795;
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(56, 178, 172, 0.3),
    0 2px 8px rgba(56, 178, 172, 0.2);

    .workspace-name {
      color: white;
    }

    .workspace-card-description {
      color: rgba(255, 255, 255, 0.8);
    }

    .selection-icon {
      color: white;
    }

    .external-icon {
      color: white;
      opacity: 1;
    }
  }
}

.workspace-card-content {
  width: 100%;
}

.workspace-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 6px;
}

.workspace-name-container {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.workspace-selection-icon {
  margin-top: 0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  height: 1.4rem; /* Match workspace name line height */
}

.selection-icon {
  font-size: 1rem;
  color: #38b2ac;
  transition: all 0.2s ease;
}

.workspace-info {
  flex: 1;
}

.workspace-name-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.workspace-name-with-icon {
  display: flex;
  align-items: center;
  gap: 6px;
}

.external-icon {
  font-size: 0.8rem;
  color: #38b2ac;
  opacity: 0.8;
}

.workspace-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #234e52;
  margin: 0;
  letter-spacing: 0.01em;
}

.workspace-card-description {
  font-size: 0.7rem;
  color: #285e61;
  line-height: 1.3;
  margin: 0;
  margin-left: 26px; /* Align with workspace name, accounting for icon + gap */
}


/* Load button styling */
.btn-outline-info {
  background: linear-gradient(135deg, transparent, rgba(56, 178, 172, 0.05));
  border: 1px solid rgba(56, 178, 172, 0.3);
  color: #38b2ac;
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #38b2ac, #319795);
    border-color: #319795;
    color: white;
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(56, 178, 172, 0.2);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

/* Scrollbar styling for workspace grid */
.remote-workspaces-grid::-webkit-scrollbar {
  width: 6px;
}

.remote-workspaces-grid::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.remote-workspaces-grid::-webkit-scrollbar-thumb {
  background: rgba(56, 178, 172, 0.3);
  border-radius: 3px;
}

.remote-workspaces-grid::-webkit-scrollbar-thumb:hover {
  background: rgba(56, 178, 172, 0.5);
}
</style>
