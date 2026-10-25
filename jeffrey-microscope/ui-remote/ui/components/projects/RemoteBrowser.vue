<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<template>
  <div class="projects-layout">
    <div v-if="loadingRemote" class="remote-state">
      <LoadingState message="Reading the remote…" />
    </div>
    <div v-else-if="remoteError" class="remote-state">
      <ErrorState :message="remoteError" @retry="refreshRemote" />
    </div>

    <!-- Empty state across both panels when configuration declares no remote -->
    <div v-else-if="!remote" class="empty-canvas">
      <div class="empty-hero-content">
        <div class="empty-hero-icon"><i class="bi bi-hdd-network"></i></div>
        <h2>No remote configured</h2>
        <p>{{ appDescription }}</p>
        <p>
          The remote is declared in Microscope's configuration only, under
          <code>jeffrey.microscope.remote.*</code>, and read at startup: add it there and restart.
        </p>
      </div>
    </div>

    <template v-else>
      <!-- Workspace column: the remote's header + workspace list + create form -->
      <section class="workspace-column">
        <header class="ws-head">
          <div class="ws-head-row">
            <h3 class="ws-head-name">
              <StatusDot :tone="STATUS_TONES[status]" size="l" :label="statusLabel" />
              {{ remote.name }}
              <Badge v-if="isAzureFiles" value="Azure Files" variant="secondary" size="xs" />
              <Badge v-if="remote.readOnly" value="Read-only" variant="warning" size="xs" />
            </h3>
          </div>
          <div class="ws-head-location">{{ remote.location }}</div>
        </header>

        <div class="ws-body">
          <div class="ws-search">
            <i class="bi bi-search"></i>
            <input v-model="searchQuery" type="text" placeholder="Search workspaces…" />
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
              <Badge :value="workspace.projectCount" variant="secondary" size="xs" />
            </button>

            <div v-if="filteredWorkspaces.length === 0 && !showCreateForm" class="ws-empty">
              <i class="bi bi-folder-x"></i>
              <span>No workspaces yet</span>
            </div>

            <!-- Inline "Create Workspace" trigger / form -->
            <button v-if="!remote.readOnly" class="ws-create-trigger" @click="openCreateForm">
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
                <input v-model="projectSearchQuery" type="text" placeholder="Search projects…" />
              </div>
              <button class="icon-btn" title="Workspace info" @click="showWorkspaceInfo = true">
                <i class="bi bi-info-circle"></i>
              </button>
              <button class="icon-btn" title="Refresh" @click="refreshProjects">
                <i class="bi bi-arrow-clockwise"></i>
              </button>
              <button
                v-if="!remote.readOnly"
                class="icon-btn danger"
                title="Delete workspace"
                @click="deleteWorkspace(selectedWorkspace)"
              >
                <i class="bi bi-trash"></i>
              </button>
            </div>
          </header>

          <LoadingState v-if="loadingProjects" message="Loading projects…" />
          <ErrorState v-else-if="projectsError" :message="projectsError" @retry="refreshProjects" />
          <EmptyState
            v-else-if="filteredProjects.length === 0"
            icon="bi-folder-plus"
            title="No projects in this workspace"
            description="Projects appear here when provisioner reports them via workspace events."
          />
          <div v-else class="project-grid">
            <slot
              v-for="project in filteredProjects"
              :key="project.id"
              name="project"
              :project="project"
              :workspace-id="selectedWorkspace.id"
            />
          </div>
        </template>

        <!-- The remote has no workspaces yet -->
        <div v-else class="main-empty">
          <i class="bi bi-folder-plus"></i>
          <h5>No workspace selected</h5>
          <p>
            Create a workspace in the panel on the left — its
            <strong>Reference ID</strong> becomes <code>provisioner</code>'s
            <code>project.workspace-ref-id</code>.
          </p>
        </div>
      </main>
    </template>

    <!-- Drawers -->

    <LeftDrawer
      v-model:show="showCreateForm"
      title="Create Workspace"
      icon="bi-folder-plus"
      @submit="submitCreate"
    >
      <DrawerSection v-if="remote" label="Remote" icon="bi-hdd-network">
        <div class="server-context-card">
          <div class="server-context-name">{{ remote.name }}</div>
          <span class="server-context-pill">
            <StatusDot tone="success" size="xs" />
            Active
          </span>
        </div>
      </DrawerSection>

      <DrawerSection label="Workspace" icon="bi-folder-plus">
        <DrawerField
          label="Workspace Name"
          required
          hint="Shown in the workspace list and project header."
          :disabled="creating"
        >
          <input
            v-model="createForm.name"
            type="text"
            class="field-input"
            placeholder="e.g. dev-pb"
            :disabled="creating"
          />
        </DrawerField>

        <DrawerField label="Reference ID" required :error="referenceIdError ?? undefined" :disabled="creating">
          <input
            v-model="createForm.referenceId"
            type="text"
            class="field-input is-mono"
            placeholder="e.g. jeffrey-testapp-1"
            :disabled="creating"
          />
          <template #hint>
            Used by <strong>provisioner</strong>'s <code>project.workspace-ref-id</code>. Must be
            unique on this remote.
            {{ WORKSPACE_REF_ID_HINT }}
          </template>
        </DrawerField>
      </DrawerSection>

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
      <DrawerSection label="Workspace" icon="bi-folder">
        <div class="info-rows">
          <InfoRow label="Name">{{ selectedWorkspace.name }}</InfoRow>

          <InfoRow label="Reference ID" mono>
            <span class="info-row-text">{{ selectedWorkspace.referenceId || '—' }}</span>
            <button
              v-if="selectedWorkspace.referenceId"
              class="info-copy-btn"
              title="Copy"
              @click="copyText(selectedWorkspace.referenceId)"
            >
              <i class="bi bi-clipboard"></i>
            </button>
          </InfoRow>

          <InfoRow label="Workspace ID" mono>
            <span class="info-row-text">{{ selectedWorkspace.id }}</span>
            <button class="info-copy-btn" title="Copy" @click="copyText(selectedWorkspace.id)">
              <i class="bi bi-clipboard"></i>
            </button>
          </InfoRow>

          <InfoRow label="Projects">{{ selectedWorkspace.projectCount }}</InfoRow>
        </div>
      </DrawerSection>

      <DrawerSection v-if="remote" label="Remote" icon="bi-hdd-network">
        <div class="info-rows">
          <InfoRow label="Name">{{ remote.name }}</InfoRow>
          <InfoRow label="Address" mono>{{ remote.location }}</InfoRow>
          <InfoRow v-if="remote.readOnly" label="Access">
            Read-only — declared so in configuration; nothing is changed through it
          </InfoRow>
        </div>
      </DrawerSection>
    </LeftDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import LeftDrawer from '@shared/components/LeftDrawer.vue';
import DrawerSection from '@shared/components/drawer/DrawerSection.vue';
import DrawerField from '@shared/components/drawer/DrawerField.vue';
import InfoRow from '@shared/components/drawer/InfoRow.vue';
import Badge from '@shared/components/Badge.vue';
import StatusDot from '@shared/components/StatusDot.vue';
import ToastService from '@shared/services/ToastService';
import RemoteClient from '@remote/services/api/RemoteClient';
import WorkspaceClient from '@remote/services/api/WorkspaceClient';
import WorkspaceProjectsClient from '@remote/services/api/WorkspaceProjectsClient';
import Remote from '@remote/services/api/model/Remote';
import type { StatusDotTone } from '@shared/types/ui';
import Workspace from '@remote/services/api/model/Workspace';
import Project from '@remote/services/api/model/Project';

const props = withDefaults(
  defineProps<{
    appDescription: string;
    // Optional deep-link: preselect a workspace on first load (e.g. from a breadcrumb). Only
    // seeds the INITIAL default; manual selection still wins afterwards.
    initialWorkspaceId?: string | null;
  }>(),
  {
    initialWorkspaceId: null
  }
);

const emit = defineEmits<{
  (e: 'workspace-change', payload: { workspaceId: string | null }): void;
}>();

type RemoteStatus = 'online' | 'offline' | 'unknown';

const remoteClient = new RemoteClient();
const workspaceClient = new WorkspaceClient();

const preferredWorkspaceId = (): string | null => {
  const wanted = props.initialWorkspaceId;
  if (wanted && workspaces.value.some(w => w.id === wanted)) {
    return wanted;
  }
  return workspaces.value[0]?.id ?? null;
};

const remote = ref<Remote | null>(null);
const loadingRemote = ref(true);
const remoteError = ref<string | null>(null);
const status = ref<RemoteStatus>('unknown');

const workspaces = ref<Workspace[]>([]);
const selectedWorkspaceId = ref<string | null>(null);
const loadingWorkspaces = ref(false);
const workspacesError = ref<string | null>(null);
const searchQuery = ref('');

const projects = ref<Project[]>([]);
const loadingProjects = ref(false);
const projectsError = ref<string | null>(null);
const projectSearchQuery = ref('');

const showWorkspaceInfo = ref(false);

const showCreateForm = ref(false);
const creating = ref(false);
const createError = ref<string | null>(null);
const createForm = ref({ name: '', referenceId: '' });

const WORKSPACE_REF_ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9-]{1,62}[a-zA-Z0-9]$/;
const WORKSPACE_REF_ID_HINT =
  '3-64 characters, alphanumeric and dashes only, no leading or trailing dash.';

const STATUS_TONES: Record<RemoteStatus, StatusDotTone> = {
  online: 'success',
  offline: 'danger',
  unknown: 'warning'
};

const STATUS_LABELS: Record<RemoteStatus, string> = {
  online: 'Online',
  offline: 'Unreachable',
  unknown: 'Checking…'
};

const selectedWorkspace = computed(() =>
  workspaces.value.find(w => w.id === selectedWorkspaceId.value)
);

const filteredWorkspaces = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) {
    return workspaces.value;
  }
  return workspaces.value.filter(w => w.name.toLowerCase().includes(q));
});

const filteredProjects = computed(() => {
  const q = projectSearchQuery.value.trim().toLowerCase();
  if (!q) {
    return projects.value;
  }
  return projects.value.filter(p => p.name.toLowerCase().includes(q));
});

const referenceIdError = computed(() => {
  const ref = createForm.value.referenceId.trim();
  if (ref.length === 0) {
    return null;
  }
  return WORKSPACE_REF_ID_PATTERN.test(ref) ? null : WORKSPACE_REF_ID_HINT;
});

const isCreateFormValid = computed(
  () =>
    createForm.value.name.trim().length > 0 &&
    WORKSPACE_REF_ID_PATTERN.test(createForm.value.referenceId.trim())
);

const isAzureFiles = computed(() => remote.value?.kind === 'AZURE_FILES');

const statusLabel = computed(() => STATUS_LABELS[status.value]);

const clearWorkspaces = () => {
  workspaces.value = [];
  selectedWorkspaceId.value = null;
  projects.value = [];
};

const refreshRemote = async () => {
  loadingRemote.value = true;
  remoteError.value = null;
  try {
    remote.value = await remoteClient.current({ suppressToast: true });
  } catch (error: any) {
    // An unread remote is not an absent one: offering to connect a remote here would be wrong.
    remoteError.value =
      error?.response?.data?.message ?? error?.message ?? 'Could not read the remote';
    remote.value = null;
  } finally {
    loadingRemote.value = false;
  }
  if (remote.value) {
    status.value = 'unknown';
    await refreshWorkspaces();
  } else {
    clearWorkspaces();
  }
};

const refreshWorkspaces = async () => {
  if (!remote.value) {
    clearWorkspaces();
    return;
  }
  loadingWorkspaces.value = true;
  workspacesError.value = null;
  try {
    workspaces.value = await workspaceClient.list({ suppressToast: true });
    status.value = 'online';
    if (
      !selectedWorkspaceId.value ||
      !workspaces.value.some(w => w.id === selectedWorkspaceId.value)
    ) {
      selectedWorkspaceId.value = preferredWorkspaceId();
    }
    if (selectedWorkspaceId.value) {
      await refreshProjects();
    } else {
      projects.value = [];
    }
  } catch (error: any) {
    workspacesError.value =
      error?.response?.data?.message ?? error?.message ?? 'Could not load workspaces';
    clearWorkspaces();
    status.value = 'offline';
  } finally {
    loadingWorkspaces.value = false;
  }
};

const refreshProjects = async () => {
  if (!selectedWorkspaceId.value) {
    projects.value = [];
    return;
  }
  loadingProjects.value = true;
  projectsError.value = null;
  try {
    const client = new WorkspaceProjectsClient(selectedWorkspaceId.value);
    projects.value = await client.list();
  } catch (error: any) {
    projectsError.value =
      error?.response?.data?.message ?? error?.message ?? 'Could not load projects';
    projects.value = [];
  } finally {
    loadingProjects.value = false;
  }
};

const selectWorkspace = (workspaceId: string) => {
  if (selectedWorkspaceId.value === workspaceId) {
    return;
  }
  selectedWorkspaceId.value = workspaceId;
  projectSearchQuery.value = '';
  emit('workspace-change', { workspaceId });
  refreshProjects();
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
  if (!remote.value || !isCreateFormValid.value) {
    return;
  }
  creating.value = true;
  createError.value = null;
  try {
    const created = await workspaceClient.create({
      referenceId: createForm.value.referenceId.trim(),
      name: createForm.value.name.trim()
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
  if (!remote.value) {
    return;
  }
  if (!confirm(`Delete workspace "${workspace.name}"? This is permanent.`)) {
    return;
  }
  try {
    await workspaceClient.delete(workspace.id);
    ToastService.success('Workspace Deleted', `"${workspace.name}" removed.`);
    if (selectedWorkspaceId.value === workspace.id) {
      selectedWorkspaceId.value = null;
      projects.value = [];
    }
    await refreshWorkspaces();
  } catch (error: any) {
    ToastService.error(
      'Failed to delete workspace',
      error?.response?.data?.message ?? error?.message ?? 'Unknown error'
    );
  }
};

onMounted(refreshRemote);
</script>

<style scoped>
@import '@shared/styles/shared-components.css';

/* ============== Layout ============== */
.projects-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--spacing-3);
  align-items: stretch;
  min-height: calc(100vh - 220px);
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

.ws-head-location {
  margin-top: var(--spacing-1);
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: var(--spacing-5);
}

/* ============== Empty states ============== */
.remote-state {
  grid-column: 1 / -1;
}

.empty-canvas {
  grid-column: 1 / -1;
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

.empty-hero-content code,
.main-empty code {
  background: var(--color-light);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  color: var(--color-code-text);
}

/* ============== Workspace Info drawer ============== */
/* .info-rows / .info-row* now live in shared-components.css (used by InfoRow.vue) */
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
