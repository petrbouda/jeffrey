<template>
  <div>
    <!-- Servers card: switcher tabs + add server -->
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-hdd-network" title="Servers" :badge="servers.length">
          <template #actions>
            <button class="page-header-btn" @click="showAddServerModal = true">
              <i class="bi bi-plus-lg"></i>
              Add Server
            </button>
          </template>
        </MainCardHeader>
      </template>
      <div v-if="servers.length === 0" class="text-center py-5">
        <i class="bi bi-hdd-network display-4 text-muted mb-3 d-block"></i>
        <h5 class="text-muted">No Jeffrey servers connected</h5>
        <p class="text-muted small mb-3">
          Connect to a running jeffrey-server to view its workspaces and projects.
        </p>
        <button class="btn btn-primary" @click="showAddServerModal = true">
          <i class="bi bi-plus-lg me-1"></i> Add Server
        </button>
      </div>
      <div v-else class="server-switcher">
        <button
          v-for="server in servers"
          :key="server.id"
          class="server-tab"
          :class="{ active: selectedServerId === server.id }"
          @click="selectServer(server.id)"
        >
          <span class="status-dot"></span>
          <span class="tab-name">{{ server.name }}</span>
          <span class="tab-meta">{{ server.hostname }}:{{ server.port }}</span>
        </button>
      </div>
    </MainCard>

    <!-- Workspaces card (for selected server) -->
    <MainCard v-if="selectedServer">
      <template #header>
        <div class="workspace-context-bar">
          <div class="context-bar-info">
            <span class="workspace-name">{{ selectedServer.name }}</span>
            <span class="context-divider">•</span>
            <span class="workspace-meta">{{ workspaceCountText }}</span>
            <span class="context-divider">•</span>
            <span class="workspace-created">
              Connected {{ FormattingService.formatRelativeTime(selectedServer.createdAt) }}
            </span>
          </div>
          <div class="context-bar-actions">
            <div class="context-search">
              <i class="bi bi-search"></i>
              <input
                v-model="searchQuery"
                type="text"
                placeholder="Search workspaces..."
              />
            </div>
            <button class="context-btn" title="Refresh" @click="refreshWorkspaces">
              <i class="bi bi-arrow-clockwise"></i>
            </button>
            <button
              class="context-btn danger"
              title="Remove server (does not delete server-side data)"
              @click="confirmDeleteServer"
            >
              <i class="bi bi-trash"></i>
            </button>
          </div>
        </div>
      </template>

      <LoadingState v-if="loadingWorkspaces" message="Loading workspaces..." />
      <ErrorState
        v-else-if="workspacesError"
        :message="workspacesError"
        @retry="refreshWorkspaces"
      />
      <EmptyState
        v-else-if="filteredWorkspaces.length === 0 && !showCreateForm"
        icon="bi-folder-plus"
        title="No workspaces on this server"
        description="Create a workspace below — its Reference ID becomes jeffrey-cli's project.workspace-id."
      />
      <div v-else-if="filteredWorkspaces.length > 0" class="row g-4 p-4">
        <div
          v-for="workspace in filteredWorkspaces"
          :key="workspace.id"
          class="col-12 col-md-6 col-lg-4"
        >
          <div class="workspace-card">
            <div class="workspace-card-title">
              <i class="bi bi-folder text-primary"></i>
              {{ workspace.name }}
            </div>
            <div class="workspace-card-meta">
              <span class="ref-id">{{ workspace.id }}</span>
            </div>
            <div class="workspace-card-footer">
              <span>{{ workspace.projectCount }} projects</span>
              <button class="btn btn-sm btn-link p-0" @click="deleteWorkspace(workspace)">
                <i class="bi bi-trash text-danger"></i>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Inline expanding "+ Create Workspace" form (variant 1) -->
      <div class="create-workspace-region p-4">
        <button
          v-if="!showCreateForm"
          class="create-workspace-trigger"
          @click="openCreateForm"
        >
          <i class="bi bi-plus-lg me-2"></i> Create Workspace
        </button>

        <div v-else class="inline-form-card">
          <div class="inline-form-header">
            <h4>
              <i class="bi bi-plus-lg text-primary me-2"></i>
              Create Workspace on {{ selectedServer.name }}
            </h4>
            <button class="context-btn" title="Cancel" @click="closeCreateForm">
              <i class="bi bi-x-lg"></i>
            </button>
          </div>

          <div class="inline-form-row">
            <div class="form-group">
              <label class="form-label">
                Workspace Name <span class="text-danger">*</span>
              </label>
              <input
                v-model="createForm.name"
                type="text"
                class="form-control"
                placeholder="e.g. dev-pb"
                :disabled="creating"
              />
              <small class="text-muted">Shown in the workspace switcher and on cards.</small>
            </div>

            <div class="form-group">
              <label class="form-label">
                Reference ID <span class="text-danger">*</span>
              </label>
              <div class="input-group">
                <input
                  v-model="createForm.referenceId"
                  type="text"
                  class="form-control font-monospace"
                  :disabled="creating"
                />
                <button
                  type="button"
                  class="btn btn-outline-secondary"
                  title="Generate new UUID"
                  :disabled="creating"
                  @click="regenerateReferenceId"
                >
                  <i class="bi bi-arrow-clockwise"></i>
                </button>
              </div>
              <small class="text-muted">
                Used by <strong>jeffrey-cli</strong>'s
                <code>project.workspace-id</code>. Must be unique on this server.
              </small>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">Server</label>
            <div class="server-readonly">
              <span class="status-dot"></span>
              {{ selectedServer.hostname }}:{{ selectedServer.port }}
              <span class="ms-auto small text-muted">selected via tabs above</span>
            </div>
          </div>

          <div v-if="createError" class="alert alert-danger mb-0" role="alert">
            <i class="bi bi-exclamation-triangle me-2"></i>{{ createError }}
          </div>

          <div class="form-actions">
            <button class="btn btn-secondary" :disabled="creating" @click="closeCreateForm">
              Cancel
            </button>
            <button
              class="btn btn-primary"
              :disabled="!isCreateFormValid || creating"
              @click="submitCreate"
            >
              <span v-if="creating" class="spinner-border spinner-border-sm me-2" role="status"></span>
              Create Workspace
            </button>
          </div>
        </div>
      </div>
    </MainCard>

    <!-- Modals -->
    <AddServerModal v-model:show="showAddServerModal" @server-added="handleServerAdded" />

    <ConfirmationDialog
      v-model:show="showDeleteServerModal"
      title="Remove Server"
      :message="deleteServerMessage"
      sub-message="This removes the local pointer; server-side data is not affected."
      confirm-label="Remove"
      confirm-button-class="btn-danger"
      @confirm="deleteServer"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import AddServerModal from '@/components/projects/AddServerModal.vue';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import RemoteServerClient from '@/services/api/RemoteServerClient';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import RemoteServer from '@/services/api/model/RemoteServer';
import Workspace from '@/services/api/model/Workspace';

const remoteServerClient = new RemoteServerClient();

const servers = ref<RemoteServer[]>([]);
const selectedServerId = ref<string | null>(null);

const workspaces = ref<Workspace[]>([]);
const loadingWorkspaces = ref(false);
const workspacesError = ref<string | null>(null);
const searchQuery = ref('');

const showAddServerModal = ref(false);
const showDeleteServerModal = ref(false);

const showCreateForm = ref(false);
const creating = ref(false);
const createError = ref<string | null>(null);
const createForm = ref({ name: '', referenceId: '' });

const selectedServer = computed(() =>
  servers.value.find(s => s.id === selectedServerId.value),
);

const filteredWorkspaces = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return workspaces.value;
  return workspaces.value.filter(w => w.name.toLowerCase().includes(q));
});

const workspaceCountText = computed(() => {
  const n = workspaces.value.length;
  if (n === 0) return 'No workspaces';
  if (n === 1) return '1 workspace';
  return `${n} workspaces`;
});

const isCreateFormValid = computed(() =>
  createForm.value.name.trim().length > 0 &&
  createForm.value.referenceId.trim().length > 0,
);

const deleteServerMessage = computed(() =>
  selectedServer.value
    ? `Remove server "${selectedServer.value.name}" (${selectedServer.value.hostname}:${selectedServer.value.port})?`
    : '',
);

const refreshServers = async () => {
  try {
    servers.value = await remoteServerClient.list({ suppressToast: true });
    if (!selectedServerId.value && servers.value.length > 0) {
      selectedServerId.value = servers.value[0].id;
      await refreshWorkspaces();
    } else if (servers.value.length === 0) {
      selectedServerId.value = null;
      workspaces.value = [];
    } else if (!servers.value.some(s => s.id === selectedServerId.value)) {
      selectedServerId.value = servers.value[0].id;
      await refreshWorkspaces();
    }
  } catch (error: any) {
    ToastService.error('Failed to load servers', 'Cannot reach the local backend.');
    servers.value = [];
    selectedServerId.value = null;
  }
};

const refreshWorkspaces = async () => {
  if (!selectedServerId.value) {
    workspaces.value = [];
    return;
  }
  loadingWorkspaces.value = true;
  workspacesError.value = null;
  try {
    const client = new WorkspaceClient(selectedServerId.value);
    workspaces.value = await client.list({ suppressToast: true });
  } catch (error: any) {
    workspacesError.value =
      error?.response?.data?.message ?? error?.message ?? 'Could not load workspaces';
    workspaces.value = [];
  } finally {
    loadingWorkspaces.value = false;
  }
};

const selectServer = (serverId: string) => {
  if (selectedServerId.value === serverId) return;
  selectedServerId.value = serverId;
  closeCreateForm();
  refreshWorkspaces();
};

const handleServerAdded = async () => {
  await refreshServers();
};

const confirmDeleteServer = () => {
  if (!selectedServer.value) return;
  showDeleteServerModal.value = true;
};

const deleteServer = async () => {
  if (!selectedServer.value) return;
  try {
    await remoteServerClient.delete(selectedServer.value.id);
    ToastService.success('Server Removed', `Removed ${selectedServer.value.name}.`);
    selectedServerId.value = null;
    await refreshServers();
  } catch (error: any) {
    ToastService.error('Failed to remove server', error?.message ?? 'Unknown error');
  }
};

const openCreateForm = () => {
  showCreateForm.value = true;
  regenerateReferenceId();
  createForm.value.name = '';
  createError.value = null;
};

const closeCreateForm = () => {
  showCreateForm.value = false;
  createForm.value = { name: '', referenceId: '' };
  createError.value = null;
};

const regenerateReferenceId = () => {
  createForm.value.referenceId = crypto.randomUUID();
};

const submitCreate = async () => {
  if (!selectedServerId.value || !isCreateFormValid.value) return;
  creating.value = true;
  createError.value = null;
  try {
    const client = new WorkspaceClient(selectedServerId.value);
    await client.create({
      referenceId: createForm.value.referenceId.trim(),
      name: createForm.value.name.trim(),
    });
    ToastService.success('Workspace Created', `"${createForm.value.name}" is ready.`);
    closeCreateForm();
    await refreshWorkspaces();
  } catch (error: any) {
    createError.value =
      error?.response?.data?.message ?? error?.message ?? 'Failed to create workspace';
  } finally {
    creating.value = false;
  }
};

const deleteWorkspace = async (workspace: Workspace) => {
  if (!selectedServerId.value) return;
  if (!confirm(`Delete workspace "${workspace.name}"? This is permanent.`)) return;
  try {
    const client = new WorkspaceClient(selectedServerId.value);
    await client.delete(workspace.id);
    ToastService.success('Workspace Deleted', `"${workspace.name}" removed.`);
    await refreshWorkspaces();
  } catch (error: any) {
    ToastService.error(
      'Failed to delete workspace',
      error?.response?.data?.message ?? error?.message ?? 'Unknown error',
    );
  }
};

onMounted(refreshServers);
</script>

<style scoped>
@import '@/styles/shared-components.css';

/* Server switcher (segmented control of tabs) */
.server-switcher {
  display: flex;
  gap: var(--spacing-2);
  align-items: center;
  flex-wrap: wrap;
  padding: var(--spacing-4) var(--spacing-5);
}

.server-tab {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-white);
  color: var(--color-text);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.server-tab:hover {
  background: var(--color-bg-hover);
  border-color: var(--color-muted-separator);
}

.server-tab.active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary-border);
}

.server-tab .tab-meta {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  font-weight: var(--font-weight-medium);
}

.server-tab.active .tab-meta {
  color: var(--color-primary-hover);
}

.server-tab .status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-success);
  display: inline-block;
}

.workspace-context-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-4);
  padding: var(--spacing-3) var(--spacing-5);
  flex-wrap: wrap;
}

.context-bar-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.workspace-name {
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.context-divider {
  color: var(--color-muted-separator);
}

.workspace-meta {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.workspace-created {
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
}

.context-bar-actions {
  display: flex;
  gap: var(--spacing-2);
  align-items: center;
}

.context-search {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  width: 200px;
}

.context-search input {
  border: none;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: var(--font-size-sm);
  flex: 1;
  color: var(--color-text);
}

.context-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-base);
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all var(--transition-base);
}

.context-btn:hover {
  background: var(--color-light);
  color: var(--color-text);
}

.context-btn.danger:hover {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

/* Workspace cards */
.workspace-card {
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-4);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
  transition: all var(--transition-base);
}

.workspace-card:hover {
  border-color: var(--color-primary-border);
  box-shadow: var(--shadow-md);
}

.workspace-card-title {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-md);
  color: var(--color-dark);
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.workspace-card-meta {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.ref-id {
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: var(--font-size-xs);
  background: var(--color-light);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  border: 1px solid var(--color-border);
  display: inline-block;
}

.workspace-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--spacing-3);
  border-top: 1px solid var(--color-border-light);
  font-size: var(--font-size-xs);
  color: var(--color-text-light);
}

/* Create-workspace region */
.create-workspace-region {
  border-top: 1px solid var(--color-border-light);
}

.create-workspace-trigger {
  width: 100%;
  padding: var(--spacing-4);
  border: 1px dashed var(--color-primary-border);
  border-radius: var(--radius-lg);
  background: var(--color-primary-lighter);
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
  transition: all var(--transition-base);
}

.create-workspace-trigger:hover {
  background: var(--color-primary-light);
  border-style: solid;
}

.inline-form-card {
  background: var(--color-white);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--spacing-5);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
}

.inline-form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.inline-form-header h4 {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.inline-form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-4);
}

@media (max-width: 720px) {
  .inline-form-row {
    grid-template-columns: 1fr;
  }
}

.server-readonly {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  color: var(--color-text);
}

.server-readonly .status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-success);
}

.form-actions {
  display: flex;
  gap: var(--spacing-3);
  justify-content: flex-end;
}
</style>
