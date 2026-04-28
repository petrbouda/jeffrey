<template>
  <GenericModal
    modal-id="mirrorWorkspaceModal"
    :show="show"
    title="Connect Remote Workspace"
    icon="bi-cloud-download"
    size="xl"
    :show-footer="true"
    @update:show="$emit('update:show', $event)"
    @shown="handleShown"
    @hidden="handleHidden"
  >
    <template #footer>
      <button type="button" class="btn btn-light" @click="$emit('update:show', false)">
        Cancel
      </button>
      <button type="button" class="btn btn-primary" @click="mirrorWorkspace" :disabled="isLoading">
        <span v-if="isLoading" class="spinner-border spinner-border-sm me-2" role="status"></span>
        <i v-if="!isLoading" class="bi bi-arrow-down-circle me-1"></i>
        Connect Workspace
      </button>
    </template>

    <!-- Description Card -->
    <div class="modal-description-card mb-4">
      <div class="description-content">
        <p class="text-muted mb-2">
          Connect to an external Jeffrey instance to access one of its workspaces remotely.
        </p>
        <p class="text-muted mb-0 small">
          <i class="bi bi-info-circle me-1"></i>
          Remote workspaces are read-only and synchronized from the external source.
        </p>
      </div>
    </div>

    <!-- Phase 1: Connection Input -->
    <div v-if="!showWorkspaceSelection">
      <div class="grpc-info-callout mb-3">
        <i class="bi bi-hdd-network me-2"></i>
        <span>Enter the gRPC endpoint of the remote Jeffrey Server</span>
      </div>

      <div class="connection-fields mb-3">
        <div class="field-hostname">
          <label class="form-label fw-medium small mb-1">Hostname</label>
          <div class="input-group hostname-input-group">
            <span class="input-group-text">
              <i class="bi bi-globe"></i>
            </span>
            <input
              type="text"
              class="form-control"
              v-model="hostname"
              placeholder="prod-jeffrey.company.com"
              @keydown.enter.prevent="handleEnterKey"
            />
          </div>
        </div>
        <div class="field-port">
          <label class="form-label fw-medium small mb-1">Port</label>
          <input
            type="number"
            class="form-control text-end port-input"
            v-model.number="port"
            placeholder="443"
            min="1"
            max="65535"
            @keydown.enter.prevent="handleEnterKey"
          />
        </div>
      </div>

      <div class="form-check mb-3 plaintext-toggle">
        <input
          type="checkbox"
          class="form-check-input"
          id="remote-workspace-plaintext"
          v-model="plaintext"
        />
        <label class="form-check-label small" for="remote-workspace-plaintext">
          Use plaintext (no TLS)
          <span class="text-muted ms-1">
            <i class="bi bi-info-circle"></i>
            Enable for in-cluster Service DNS or trusted-LAN dev setups; leave off for the public internet.
          </span>
        </label>
      </div>

      <div class="d-flex justify-content-end mb-3">
        <button
          type="button"
          class="btn btn-outline-info"
          @click="loadRemoteWorkspaces"
          :disabled="!connectionValid || loadingWorkspaces"
        >
          <span
            v-if="loadingWorkspaces"
            class="spinner-border spinner-border-sm me-2"
            role="status"
          ></span>
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
          Choose one or more workspaces from <strong>{{ displayAddress }}</strong> to connect
          remotely.
        </p>
      </div>

      <!-- Remote Workspaces Grid -->
      <div v-if="remoteWorkspaces.length > 0" class="remote-workspaces-grid">
        <div
          v-for="workspace in remoteWorkspaces"
          :key="workspace.id"
          class="remote-workspace-card"
          :class="{ selected: selectedWorkspaceIds.includes(workspace.id) }"
          @click="toggleWorkspaceSelection(workspace.id)"
        >
          <div class="workspace-card-content">
            <div class="workspace-card-header">
              <div class="workspace-name-container">
                <div class="workspace-selection-icon">
                  <i
                    :class="
                      selectedWorkspaceIds.includes(workspace.id)
                        ? 'bi bi-check-circle-fill'
                        : 'bi bi-circle'
                    "
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
        <p class="text-muted small mb-0">
          The remote Jeffrey instance has no available workspaces to connect.
        </p>
      </div>

      <!-- Back Button -->
      <div class="d-flex justify-content-start mt-3">
        <button type="button" class="btn btn-outline-secondary btn-sm" @click="goBackToUrlInput">
          <i class="bi bi-arrow-left me-1"></i>
          Change Address
        </button>
      </div>
    </div>

    <!-- Error Messages -->
    <div v-if="errorMessage" class="alert alert-danger mt-3">
      <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ errorMessage }}
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import GenericModal from '@/components/GenericModal.vue';
import Badge from '@/components/Badge.vue';
import Workspace from '@/services/api/model/Workspace';
import ToastService from '@/services/ToastService';
import RemoteWorkspaceClient from '@/services/api/RemoteWorkspaceClient.ts';

const remoteWorkspaceClient = new RemoteWorkspaceClient();

interface Props {
  show: boolean;
}

defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'workspace-added'): void;
}>();

// Form states
const hostname = ref('');
const port = ref(443);
const plaintext = ref(false);
const showWorkspaceSelection = ref(false);
const loadingWorkspaces = ref(false);
const remoteWorkspaces = ref<Workspace[]>([]);
const selectedWorkspaceIds = ref<string[]>([]);
const errorMessage = ref('');
const isLoading = ref(false);

// Connection validation
const connectionValid = computed(() => {
  return hostname.value.trim().length > 0 && port.value >= 1 && port.value <= 65535;
});

// Display address for Phase 2
const displayAddress = computed(() => `${hostname.value}:${port.value}`);

// Reset form
const resetForm = () => {
  hostname.value = '';
  port.value = 443;
  plaintext.value = false;
  showWorkspaceSelection.value = false;
  loadingWorkspaces.value = false;
  remoteWorkspaces.value = [];
  selectedWorkspaceIds.value = [];
  errorMessage.value = '';
  isLoading.value = false;
};

// Handle enter key in input fields
const handleEnterKey = () => {
  mirrorWorkspace();
};

// Load remote workspaces
const loadRemoteWorkspaces = async () => {
  if (!connectionValid.value) {
    errorMessage.value = 'Please enter a valid hostname and port';
    return;
  }

  loadingWorkspaces.value = true;
  errorMessage.value = '';

  try {
    // Call the backend API to get available workspaces to add as remote
    remoteWorkspaces.value = await remoteWorkspaceClient.listRemote(
      hostname.value.trim(),
      port.value,
      plaintext.value
    );
    showWorkspaceSelection.value = true;

    if (remoteWorkspaces.value.length === 0) {
      ToastService.info(
        'No Workspaces Found',
        'The remote Jeffrey instance has no available workspaces.'
      );
    }
  } catch (error) {
    console.error('Failed to load remote workspaces:', error);
    errorMessage.value =
      error instanceof Error ? error.message : 'Failed to connect to the remote Jeffrey instance';
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

  isLoading.value = true;
  try {
    const selectedWorkspaces = remoteWorkspaces.value.filter(w =>
      selectedWorkspaceIds.value.includes(w.id)
    );
    if (selectedWorkspaces.length === 0) {
      throw new Error('Selected workspaces not found');
    }

    await remoteWorkspaceClient.createRemote(
      hostname.value.trim(),
      port.value,
      plaintext.value,
      selectedWorkspaceIds.value
    );

    const selectedWorkspaceNames = selectedWorkspaces.map(w => w.name).join('", "');
    const successMessage =
      selectedWorkspaces.length === 1
        ? `"${selectedWorkspaceNames}" workspace has been connected successfully`
        : `"${selectedWorkspaceNames}" workspaces have been connected successfully`;

    ToastService.success('Workspaces Connected!', successMessage);

    emit('workspace-added');
    emit('update:show', false);
    resetForm();
  } catch (error) {
    console.error('Failed to connect workspace:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Failed to connect workspace';
  } finally {
    isLoading.value = false;
  }
};

// Handle modal shown
const handleShown = () => {
  resetForm();
};

// Handle modal hidden
const handleHidden = () => {
  resetForm();
};
</script>

<style scoped>
/* Description card (inlined from BaseModal) */
.modal-description-card {
  background: linear-gradient(135deg, var(--color-light), var(--color-white));
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
  color: var(--color-text);
  margin-bottom: 0.5rem;
}

.description-content p:last-child {
  margin-bottom: 0;
}

/* gRPC info callout */
.grpc-info-callout {
  background: linear-gradient(135deg, var(--color-teal-light), var(--color-teal-100));
  border: 1px solid rgba(56, 178, 172, 0.2);
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 0.8rem;
  color: var(--color-teal-800);
  display: flex;
  align-items: center;
}

.grpc-info-callout i {
  color: var(--color-teal-600);
  font-size: 0.9rem;
}

/* Hostname input group - unified container border matching port field height */
.hostname-input-group {
  border-radius: 0.25rem;
  overflow: hidden;
  border: 1px solid var(--color-border-input);
  background: var(--color-white);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  flex-wrap: nowrap;
  height: calc(1.5em + 0.75rem + 2px);
}

.hostname-input-group:focus-within {
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
}

.hostname-input-group .input-group-text {
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  padding: 0 10px;
}

.hostname-input-group .form-control {
  border: none;
  border-radius: 0;
  box-shadow: none;
  background: transparent;
  height: auto;
  padding: 0.375rem 0.75rem;
}

.hostname-input-group .form-control:focus {
  box-shadow: none;
  transform: none;
}

/* Connection fields layout */
.connection-fields {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.field-hostname {
  flex: 1;
}

.field-port {
  width: 100px;
  flex-shrink: 0;
}

.port-input {
  -moz-appearance: textfield;
}

.port-input::-webkit-outer-spin-button,
.port-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

/* Remote workspace selection styling */
.remote-workspaces-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 2px;
}

.remote-workspace-card {
  background: linear-gradient(135deg, var(--color-teal-light), var(--color-teal-100));
  border: 2px solid rgba(56, 178, 172, 0.2);
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.02);

  &:hover {
    transform: translateY(-2px);
    box-shadow:
      0 6px 16px rgba(56, 178, 172, 0.15),
      0 2px 8px rgba(56, 178, 172, 0.1);
    border-color: rgba(56, 178, 172, 0.3);
  }

  &.selected {
    background: linear-gradient(135deg, var(--color-teal-600), var(--color-teal-700));
    border-color: var(--color-teal-700);
    transform: translateY(-1px);
    box-shadow:
      0 6px 20px rgba(56, 178, 172, 0.3),
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
  color: var(--color-teal-600);
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
  color: var(--color-teal-600);
  opacity: 0.8;
}

.workspace-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-teal-900);
  margin: 0;
  letter-spacing: 0.01em;
}

.workspace-card-description {
  font-size: 0.7rem;
  color: var(--color-teal-800);
  line-height: 1.3;
  margin: 0;
  margin-left: 26px; /* Align with workspace name, accounting for icon + gap */
}

/* Load button styling */
.btn-outline-info {
  background: linear-gradient(135deg, transparent, rgba(56, 178, 172, 0.05));
  border: 1px solid rgba(56, 178, 172, 0.3);
  color: var(--color-teal-600);
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, var(--color-teal-600), var(--color-teal-700));
    border-color: var(--color-teal-700);
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
  background: var(--color-light);
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
