<template>
  <div class="projects-layout">
    <!-- Vertical icon rail: connected Jeffrey servers + add-server affordance -->
    <aside class="server-rail">
      <button
        v-for="server in servers"
        :key="server.id"
        class="rail-avatar"
        :class="[{ active: selectedServerId === server.id }, `is-${serverStatuses[server.id] || 'unknown'}`]"
        :title="server.name"
        @click="selectServer(server.id)"
      >
        <span class="avatar-text">{{ initials(server.name) }}</span>
        <span class="status-dot"></span>
        <span class="rail-tooltip">
          <strong>{{ server.name }}</strong>
          <small>{{ server.hostname }}:{{ server.port }}</small>
          <small>{{ statusLabelFor(server.id) }}</small>
        </span>
      </button>

      <div v-if="servers.length > 0" class="rail-divider"></div>

      <button class="rail-add" title="Add Jeffrey Server" @click="showAddServerModal = true">
        <i class="bi bi-plus-lg"></i>
        <span class="rail-tooltip">
          <strong>Add Jeffrey Server</strong>
          <small>Connect a new gRPC endpoint</small>
        </span>
      </button>
    </aside>

    <!-- Empty state across both panels when no servers are connected -->
    <div v-if="servers.length === 0" class="empty-canvas">
      <div class="empty-hero-content">
        <div class="empty-hero-icon"><i class="bi bi-hdd-network"></i></div>
        <h2>Connect your first server</h2>
        <p>Microscope analyzes profiles served by Jeffrey servers. Add a remote server to browse its workspaces and projects.</p>
        <div class="empty-hero-actions">
          <button class="btn btn-primary btn-lg" @click="showAddServerModal = true">
            <i class="bi bi-plus-lg me-1"></i> Add Server
          </button>
        </div>
      </div>
    </div>

    <template v-else-if="selectedServer">
      <!-- Workspace column: header for selected server + workspace list + create form -->
      <section class="workspace-column">
        <header class="ws-head">
          <div class="ws-head-row">
            <h3 class="ws-head-name">
              <i class="bi bi-server text-primary"></i>
              {{ selectedServer.name }}
            </h3>
            <button
              class="ws-head-btn danger"
              title="Remove server (does not delete server-side data)"
              @click="confirmDeleteServer"
            >
              <i class="bi bi-trash"></i>
            </button>
          </div>
        </header>

        <div class="ws-body">
          <div class="ws-search">
            <i class="bi bi-search"></i>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Search workspaces…"
            />
          </div>

          <LoadingState v-if="loadingWorkspaces" message="Loading workspaces…" />
          <ErrorState
            v-else-if="workspacesError"
            :message="workspacesError"
            @retry="refreshWorkspaces"
          />

          <template v-else>
            <div class="ws-section-title">Workspaces</div>

            <button
              v-for="workspace in filteredWorkspaces"
              :key="workspace.id"
              class="ws-item"
              :class="{ active: workspace.id === selectedWorkspaceId }"
              @click="selectWorkspace(workspace.id)"
            >
              <i class="bi bi-folder"></i>
              <span class="ws-item-name">{{ workspace.name }}</span>
              <Badge
                :value="workspace.projectCount"
                variant="secondary"
                size="xs"
              />
            </button>

            <div
              v-if="filteredWorkspaces.length === 0 && !showCreateForm"
              class="ws-empty"
            >
              <i class="bi bi-folder-x"></i>
              <span>No workspaces yet</span>
            </div>

            <!-- Inline "Create Workspace" trigger / form (variant 1, kept) -->
            <button class="ws-create-trigger" @click="openCreateForm">
              <i class="bi bi-plus-lg"></i> Create Workspace
            </button>
          </template>
        </div>
      </section>

      <!-- Main area: project grid for the selected workspace -->
      <main class="project-main">
        <template v-if="selectedWorkspace">
          <header class="main-head">
            <div class="main-head-info">
              <h2>
                <i class="bi bi-folder-fill text-primary"></i>
                {{ selectedWorkspace.name }}
              </h2>
              <Badge
                key-label="Projects"
                :value="projects.length"
                variant="secondary"
                size="s"
                :uppercase="false"
                :borderless="true"
              />
            </div>
            <div class="main-head-actions">
              <div class="search">
                <i class="bi bi-search"></i>
                <input
                  v-model="projectSearchQuery"
                  type="text"
                  placeholder="Search projects…"
                />
              </div>
              <button
                class="icon-btn"
                title="Workspace info"
                @click="showWorkspaceInfo = true"
              >
                <i class="bi bi-info-circle"></i>
              </button>
              <button
                class="icon-btn"
                title="Refresh"
                @click="refreshProjects"
              >
                <i class="bi bi-arrow-clockwise"></i>
              </button>
              <button
                class="icon-btn danger"
                title="Delete workspace"
                @click="deleteWorkspace(selectedWorkspace)"
              >
                <i class="bi bi-trash"></i>
              </button>
            </div>
          </header>

          <LoadingState v-if="loadingProjects" message="Loading projects…" />
          <ErrorState
            v-else-if="projectsError"
            :message="projectsError"
            @retry="refreshProjects"
          />
          <EmptyState
            v-else-if="filteredProjects.length === 0"
            icon="bi-folder-plus"
            title="No projects in this workspace"
            description="Projects appear here when jeffrey-cli reports them via workspace events."
          />
          <div v-else class="project-grid">
            <ProjectCard
              v-for="project in filteredProjects"
              :key="project.id"
              :project="project"
              :server-id="selectedServerId!"
              :workspace-id="selectedWorkspace.id"
              @restore="handleRestoreProject"
            />
          </div>
        </template>

        <!-- Server has no workspaces yet -->
        <div v-else class="main-empty">
          <i class="bi bi-folder-plus"></i>
          <h5>No workspace selected</h5>
          <p>
            Create a workspace in the panel on the left — its
            <strong>Reference ID</strong> becomes
            <code>jeffrey-cli</code>'s <code>project.workspace-ref-id</code>.
          </p>
        </div>
      </main>
    </template>

    <!-- Drawers -->
    <AddServerModal v-model:show="showAddServerModal" @server-added="handleServerAdded" />

    <LeftDrawer
      v-model:show="showCreateForm"
      title="Create Workspace"
      icon="bi-folder-plus"
      @submit="submitCreate"
    >
      <div v-if="selectedServer" class="drawer-section">
        <div class="drawer-section-label">
          <i class="bi bi-hdd-network"></i>
          Target Server
        </div>
        <div class="server-context-card">
          <div class="server-context-name">{{ selectedServer.name }}</div>
          <span class="server-context-pill">
            <span class="status-dot"></span>
            Active
          </span>
        </div>
      </div>

      <div class="drawer-section">
        <div class="drawer-section-label">
          <i class="bi bi-folder-plus"></i>
          Workspace
        </div>

        <div class="field-group">
          <label class="field-label">
            Workspace Name
            <span class="field-required">*</span>
          </label>
          <div class="field-wrap" :class="{ 'is-disabled': creating }">
            <input
              v-model="createForm.name"
              type="text"
              class="field-input"
              placeholder="e.g. dev-pb"
              :disabled="creating"
            />
          </div>
          <div class="field-hint">Shown in the workspace list and project header.</div>
        </div>

        <div class="field-group">
          <label class="field-label">
            Reference ID
            <span class="field-required">*</span>
          </label>
          <div class="field-wrap" :class="{ 'is-disabled': creating, 'is-invalid': !!referenceIdError }">
            <input
              v-model="createForm.referenceId"
              type="text"
              class="field-input is-mono"
              placeholder="e.g. jeffrey-testapp-1"
              :disabled="creating"
            />
          </div>
          <div v-if="referenceIdError" class="field-error">
            <i class="bi bi-exclamation-circle"></i>
            <span>{{ referenceIdError }}</span>
          </div>
          <div v-else class="field-hint">
            Used by <strong>jeffrey-cli</strong>'s
            <code>project.workspace-ref-id</code>. Must be unique on this server.
            {{ WORKSPACE_REF_ID_HINT }}
          </div>
        </div>
      </div>

      <div v-if="createError" class="field-alert" role="alert">
        <i class="bi bi-exclamation-triangle"></i>
        <span>{{ createError }}</span>
      </div>

      <template #footer>
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
      </template>
    </LeftDrawer>

    <LeftDrawer
      v-if="selectedWorkspace"
      v-model:show="showWorkspaceInfo"
      title="Workspace Info"
      icon="bi-info-circle"
    >
      <div class="drawer-section">
        <div class="drawer-section-label">
          <i class="bi bi-folder"></i>
          Workspace
        </div>

        <div class="info-rows">
          <div class="info-row">
            <div class="info-row-label">Name</div>
            <div class="info-row-value">{{ selectedWorkspace.name }}</div>
          </div>

          <div class="info-row">
            <div class="info-row-label">Reference ID</div>
            <div class="info-row-value is-mono">
              <span class="info-row-text">{{ selectedWorkspace.referenceId || '—' }}</span>
              <button
                v-if="selectedWorkspace.referenceId"
                class="info-copy-btn"
                title="Copy"
                @click="copyText(selectedWorkspace.referenceId)"
              >
                <i class="bi bi-clipboard"></i>
              </button>
            </div>
          </div>

          <div class="info-row">
            <div class="info-row-label">Workspace ID</div>
            <div class="info-row-value is-mono">
              <span class="info-row-text">{{ selectedWorkspace.id }}</span>
              <button
                class="info-copy-btn"
                title="Copy"
                @click="copyText(selectedWorkspace.id)"
              >
                <i class="bi bi-clipboard"></i>
              </button>
            </div>
          </div>

          <div class="info-row">
            <div class="info-row-label">Projects</div>
            <div class="info-row-value">
              {{ selectedWorkspace.projectCount }}
            </div>
          </div>
        </div>
      </div>

      <div v-if="selectedServer" class="drawer-section">
        <div class="drawer-section-label">
          <i class="bi bi-hdd-network"></i>
          Server
        </div>

        <div class="info-rows">
          <div class="info-row">
            <div class="info-row-label">Name</div>
            <div class="info-row-value">{{ selectedServer.name }}</div>
          </div>

          <div class="info-row">
            <div class="info-row-label">Address</div>
            <div class="info-row-value is-mono">
              {{ selectedServer.hostname }}:{{ selectedServer.port }}
            </div>
          </div>
        </div>
      </div>
    </LeftDrawer>

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
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import AddServerModal from '@/components/projects/AddServerModal.vue';
import LeftDrawer from '@/components/LeftDrawer.vue';
import ProjectCard from '@/components/ProjectCard.vue';
import Badge from '@/components/Badge.vue';
import ToastService from '@/services/ToastService';
import RemoteServerClient from '@/services/api/RemoteServerClient';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import WorkspaceProjectsClient from '@/services/api/WorkspaceProjectsClient';
import ProjectClient from '@/services/api/ProjectClient';
import RemoteServer from '@/services/api/model/RemoteServer';
import Workspace from '@/services/api/model/Workspace';
import Project from '@/services/api/model/Project';

type ServerStatus = 'online' | 'offline' | 'unknown';

const remoteServerClient = new RemoteServerClient();

const servers = ref<RemoteServer[]>([]);
const selectedServerId = ref<string | null>(null);
const serverStatuses = ref<Record<string, ServerStatus>>({});

const workspaces = ref<Workspace[]>([]);
const selectedWorkspaceId = ref<string | null>(null);
const loadingWorkspaces = ref(false);
const workspacesError = ref<string | null>(null);
const searchQuery = ref('');

const projects = ref<Project[]>([]);
const loadingProjects = ref(false);
const projectsError = ref<string | null>(null);
const projectSearchQuery = ref('');

const showAddServerModal = ref(false);
const showDeleteServerModal = ref(false);
const showWorkspaceInfo = ref(false);

const showCreateForm = ref(false);
const creating = ref(false);
const createError = ref<string | null>(null);
const createForm = ref({ name: '', referenceId: '' });

const WORKSPACE_REF_ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9-]{1,62}[a-zA-Z0-9]$/;
const WORKSPACE_REF_ID_HINT = '3-64 characters, alphanumeric and dashes only, no leading or trailing dash.';

const selectedServer = computed(() =>
  servers.value.find(s => s.id === selectedServerId.value),
);

const selectedWorkspace = computed(() =>
  workspaces.value.find(w => w.id === selectedWorkspaceId.value),
);

const filteredWorkspaces = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return workspaces.value;
  return workspaces.value.filter(w => w.name.toLowerCase().includes(q));
});

const filteredProjects = computed(() => {
  const q = projectSearchQuery.value.trim().toLowerCase();
  if (!q) return projects.value;
  return projects.value.filter(p => p.name.toLowerCase().includes(q));
});

const referenceIdError = computed(() => {
  const ref = createForm.value.referenceId.trim();
  if (ref.length === 0) return null;
  return WORKSPACE_REF_ID_PATTERN.test(ref) ? null : WORKSPACE_REF_ID_HINT;
});

const isCreateFormValid = computed(() =>
  createForm.value.name.trim().length > 0 &&
  WORKSPACE_REF_ID_PATTERN.test(createForm.value.referenceId.trim()),
);

const deleteServerMessage = computed(() =>
  selectedServer.value
    ? `Remove server "${selectedServer.value.name}" (${selectedServer.value.hostname}:${selectedServer.value.port})?`
    : '',
);

const statusLabelFor = (serverId: string): string => {
  switch (serverStatuses.value[serverId]) {
    case 'online': return '● Online';
    case 'offline': return '● Unreachable';
    default: return '● Checking…';
  }
};

const probeServer = async (serverId: string) => {
  try {
    await new WorkspaceClient(serverId).list({ suppressToast: true });
    serverStatuses.value = { ...serverStatuses.value, [serverId]: 'online' };
  } catch {
    serverStatuses.value = { ...serverStatuses.value, [serverId]: 'offline' };
  }
};

// Initials for the rail avatar: take the first letter of up to two words, uppercase.
const initials = (name: string): string => {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
};

const refreshServers = async () => {
  try {
    servers.value = await remoteServerClient.list({ suppressToast: true });

    // Probe each server's gRPC endpoint in parallel; "unknown" until the probe returns.
    const initial: Record<string, ServerStatus> = {};
    for (const s of servers.value) initial[s.id] = serverStatuses.value[s.id] ?? 'unknown';
    serverStatuses.value = initial;
    Promise.all(servers.value.map(s => probeServer(s.id)));

    if (!selectedServerId.value && servers.value.length > 0) {
      selectedServerId.value = servers.value[0].id;
      await refreshWorkspaces();
    } else if (servers.value.length === 0) {
      selectedServerId.value = null;
      workspaces.value = [];
      selectedWorkspaceId.value = null;
      projects.value = [];
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
    selectedWorkspaceId.value = null;
    projects.value = [];
    return;
  }
  loadingWorkspaces.value = true;
  workspacesError.value = null;
  const sid = selectedServerId.value;
  try {
    const client = new WorkspaceClient(sid);
    workspaces.value = await client.list({ suppressToast: true });
    serverStatuses.value = { ...serverStatuses.value, [sid]: 'online' };
    if (
      !selectedWorkspaceId.value ||
      !workspaces.value.some(w => w.id === selectedWorkspaceId.value)
    ) {
      selectedWorkspaceId.value = workspaces.value[0]?.id ?? null;
    }
    if (selectedWorkspaceId.value) {
      await refreshProjects();
    } else {
      projects.value = [];
    }
  } catch (error: any) {
    workspacesError.value =
      error?.response?.data?.message ?? error?.message ?? 'Could not load workspaces';
    workspaces.value = [];
    selectedWorkspaceId.value = null;
    projects.value = [];
    serverStatuses.value = { ...serverStatuses.value, [sid]: 'offline' };
  } finally {
    loadingWorkspaces.value = false;
  }
};

const refreshProjects = async () => {
  if (!selectedServerId.value || !selectedWorkspaceId.value) {
    projects.value = [];
    return;
  }
  loadingProjects.value = true;
  projectsError.value = null;
  try {
    const client = new WorkspaceProjectsClient(selectedServerId.value, selectedWorkspaceId.value);
    projects.value = await client.list();
  } catch (error: any) {
    projectsError.value =
      error?.response?.data?.message ?? error?.message ?? 'Could not load projects';
    projects.value = [];
  } finally {
    loadingProjects.value = false;
  }
};

const selectServer = (serverId: string) => {
  if (selectedServerId.value === serverId) return;
  selectedServerId.value = serverId;
  selectedWorkspaceId.value = null;
  projects.value = [];
  closeCreateForm();
  refreshWorkspaces();
};

const selectWorkspace = (workspaceId: string) => {
  if (selectedWorkspaceId.value === workspaceId) return;
  selectedWorkspaceId.value = workspaceId;
  projectSearchQuery.value = '';
  refreshProjects();
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
    selectedWorkspaceId.value = null;
    await refreshServers();
  } catch (error: any) {
    ToastService.error('Failed to remove server', error?.message ?? 'Unknown error');
  }
};

const copyText = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value);
    ToastService.success('Copied', value);
  } catch {
    ToastService.error('Copy failed', 'Clipboard not available');
  }
};

const openCreateForm = () => {
  showCreateForm.value = true;
  createForm.value = { name: '', referenceId: crypto.randomUUID() };
  createError.value = null;
};

const closeCreateForm = () => {
  showCreateForm.value = false;
  createForm.value = { name: '', referenceId: '' };
  createError.value = null;
};


const submitCreate = async () => {
  if (!selectedServerId.value || !isCreateFormValid.value) return;
  creating.value = true;
  createError.value = null;
  try {
    const client = new WorkspaceClient(selectedServerId.value);
    const created = await client.create({
      referenceId: createForm.value.referenceId.trim(),
      name: createForm.value.name.trim(),
    });
    ToastService.success('Workspace Created', `"${createForm.value.name}" is ready.`);
    closeCreateForm();
    await refreshWorkspaces();
    if (created?.id) {
      selectedWorkspaceId.value = created.id;
      await refreshProjects();
    }
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
    if (selectedWorkspaceId.value === workspace.id) {
      selectedWorkspaceId.value = null;
      projects.value = [];
    }
    await refreshWorkspaces();
  } catch (error: any) {
    ToastService.error(
      'Failed to delete workspace',
      error?.response?.data?.message ?? error?.message ?? 'Unknown error',
    );
  }
};

const handleRestoreProject = async (projectId: string) => {
  if (!selectedServerId.value || !selectedWorkspaceId.value) return;
  try {
    const client = new ProjectClient(selectedServerId.value, selectedWorkspaceId.value, projectId);
    await client.restore();
    ToastService.success('Project Restored', 'Project has been restored.');
    await refreshProjects();
  } catch (error: any) {
    ToastService.error(
      'Failed to restore project',
      error?.response?.data?.message ?? error?.message ?? 'Unknown error',
    );
  }
};

onMounted(refreshServers);
</script>

<style scoped>
@import '@/styles/shared-components.css';

/* ============== Layout ============== */
.projects-layout {
  display: grid;
  grid-template-columns: 72px 280px 1fr;
  gap: var(--spacing-3);
  align-items: stretch;
  min-height: calc(100vh - 220px);
}

/* ============== Server rail (Slack/Discord-style) ============== */
.server-rail {
  background: var(--color-white);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  padding: var(--spacing-3);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2);
}

.rail-avatar,
.rail-add {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-weight: var(--font-weight-bold);
  font-size: var(--font-size-md);
  color: var(--color-text);
  background: var(--color-light);
  border: 2px solid transparent;
  position: relative;
  transition: all var(--transition-base);
  flex-shrink: 0;
}

.rail-avatar:hover {
  border-radius: 12px;
  background: var(--color-primary-lighter);
}

.rail-avatar.active {
  background: var(--color-primary);
  color: var(--color-white);
  border-radius: 12px;
}

.rail-avatar.active::before {
  content: '';
  position: absolute;
  left: -16px;
  top: 8px;
  bottom: 8px;
  width: 4px;
  background: var(--color-primary);
  border-radius: 0 4px 4px 0;
}

.rail-avatar .avatar-text {
  pointer-events: none;
}

.rail-avatar .status-dot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 14px;
  height: 14px;
  background: var(--color-warning);
  border-radius: 50%;
  border: 2px solid var(--color-white);
}

.rail-avatar.is-online .status-dot { background: var(--color-success); }
.rail-avatar.is-offline .status-dot { background: var(--color-danger); }
.rail-avatar.is-unknown .status-dot { background: var(--color-warning); }

.rail-divider {
  width: 32px;
  height: 2px;
  background: var(--color-border-light);
  border-radius: 1px;
  margin: 4px 0;
  flex-shrink: 0;
}

.rail-add {
  background: var(--color-primary-lighter);
  border: 1px dashed var(--color-primary-border);
  color: var(--color-primary);
  font-size: var(--font-size-lg);
  border-radius: 12px;
}

.rail-add:hover {
  border-style: solid;
  background: var(--color-primary-light);
}

.rail-add i {
  font-size: 1.1rem;
}

/* Tooltip on hover */
.rail-tooltip {
  position: absolute;
  left: 100%;
  margin-left: 12px;
  top: 50%;
  transform: translateY(-50%);
  background: var(--color-dark);
  color: var(--color-white);
  padding: 8px 12px;
  border-radius: var(--radius-base);
  font-size: var(--font-size-xs);
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 0.15s, visibility 0s linear 0.15s;
  z-index: 10;
  font-weight: var(--font-weight-normal);
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
}

.rail-tooltip strong {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
}

.rail-tooltip small {
  color: var(--color-text-light);
  font-size: 11px;
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
}

.rail-avatar:hover .rail-tooltip,
.rail-add:hover .rail-tooltip {
  opacity: 1;
  visibility: visible;
  transition: opacity 0.15s, visibility 0s linear 0s;
}

/* ============== Workspace column ============== */
.workspace-column {
  background: var(--color-white);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ws-head {
  padding: var(--spacing-4) var(--spacing-4);
  border-bottom: 1px solid var(--color-border-light);
}

.ws-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-2);
  min-height: 32px;
}

.ws-head-name {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.ws-head-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-base);
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-base);
}

.ws-head-btn:hover {
  background: var(--color-light);
}

.ws-head-btn.danger:hover {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.ws-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px var(--spacing-3);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
  margin-bottom: var(--spacing-2);
}

.ws-search input {
  border: none;
  outline: none;
  background: transparent;
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.ws-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--spacing-3);
  flex: 1;
  overflow-y: auto;
}

.ws-section-title {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-light);
  font-weight: var(--font-weight-semibold);
  padding: var(--spacing-2) var(--spacing-2) var(--spacing-3);
}

.ws-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-base);
  border: none;
  background: transparent;
  width: 100%;
  text-align: left;
  color: var(--color-text);
}

.ws-item:hover {
  background: var(--color-light);
}

.ws-item.active {
  background: var(--color-primary);
  color: var(--color-white);
}

.ws-item-name {
  flex: 1;
  font-weight: var(--font-weight-medium);
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-item.active .ws-item-name {
  font-weight: var(--font-weight-semibold);
}


.ws-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: var(--spacing-4) var(--spacing-2);
  color: var(--color-text-light);
  font-size: var(--font-size-sm);
}

.ws-empty i {
  font-size: 1.5rem;
}

.ws-create-trigger {
  margin-top: var(--spacing-2);
  padding: var(--spacing-3);
  border: 1px dashed var(--color-primary-border);
  border-radius: var(--radius-md);
  background: var(--color-primary-lighter);
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
  text-align: center;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all var(--transition-base);
}

.ws-create-trigger:hover {
  border-style: solid;
  background: var(--color-primary-light);
}

/* ============== Project main area ============== */
.project-main {
  background: var(--color-white);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-head {
  padding: var(--spacing-4) var(--spacing-5);
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
  flex-wrap: wrap;
}

.main-head-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
}

.main-head h2 {
  margin: 0;
  font-size: var(--font-size-md);
  color: var(--color-dark);
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-head-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px var(--spacing-3);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
  width: 220px;
}

.search input {
  border: none;
  outline: none;
  background: transparent;
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.icon-btn {
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

.icon-btn:hover {
  background: var(--color-light);
  color: var(--color-text);
}

.icon-btn.danger:hover {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.project-grid {
  padding: var(--spacing-5);
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--spacing-4);
}

/* ============== Empty states ============== */
.empty-canvas {
  grid-column: 2 / 4;
  background: var(--color-white);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-8);
  position: relative;
  overflow: hidden;
}

.empty-canvas::after {
  content: '';
  position: absolute;
  right: -120px;
  top: -120px;
  width: 320px;
  height: 320px;
  background: var(--color-primary-light);
  border-radius: 50%;
  pointer-events: none;
}

.empty-hero-content {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 480px;
}

.empty-hero-icon {
  width: 56px;
  height: 56px;
  background: var(--color-primary);
  border-radius: var(--radius-md);
  color: var(--color-white);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.625rem;
  margin-bottom: var(--spacing-5);
}

.empty-hero-content h2 {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 0 0 var(--spacing-2) 0;
  color: var(--color-dark);
}

.empty-hero-content p {
  font-size: var(--font-size-md);
  color: var(--color-text-muted);
  margin: 0 0 var(--spacing-6) 0;
  line-height: 1.5;
}

.empty-hero-actions {
  display: flex;
  gap: var(--spacing-3);
}

.main-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  flex: 1;
  padding: var(--spacing-8);
  gap: var(--spacing-3);
  color: var(--color-text-muted);
}

.main-empty i {
  font-size: 3rem;
  color: var(--color-text-light);
}

.main-empty h5 {
  margin: 0;
  color: var(--color-dark);
}

.main-empty p {
  margin: 0;
  color: var(--color-text-muted);
  max-width: 420px;
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.main-empty code {
  background: var(--color-light);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  color: var(--color-code-text);
}

/* ============== Workspace Info drawer ============== */
.info-rows {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-light);
}

.info-row:last-child {
  border-bottom: none;
}

.info-row-label {
  flex-shrink: 0;
  width: 130px;
  font-size: 0.78rem;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-muted);
}

.info-row-value {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
}

.info-row-value.is-mono {
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-regular);
}

.info-row-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  border-radius: var(--radius-sm);
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--transition-base);
}

.info-copy-btn:hover {
  background: var(--color-light);
  color: var(--color-primary);
}
</style>
