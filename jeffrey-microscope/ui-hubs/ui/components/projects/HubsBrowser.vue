<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
  <div class="projects-layout">
    <!-- Vertical icon rail: connected Jeffrey Hubs + add-hub affordance -->
    <aside class="hub-rail">
      <button
        v-for="hub in hubs"
        :key="hub.id"
        class="rail-avatar"
        :class="[
          { active: selectedHubId === hub.id },
          `is-${hubStatuses[hub.id] || 'unknown'}`
        ]"
        :title="hub.name"
        @click="selectHub(hub.id)"
      >
        <span class="avatar-text">{{ initials(hub.name) }}</span>
        <span class="status-dot"></span>
        <span class="rail-tooltip">
          <strong>{{ hub.name }}</strong>
          <small>{{ hub.hostname }}:{{ hub.port }}</small>
          <small>{{ statusLabelFor(hub.id) }}</small>
        </span>
      </button>

      <div v-if="hubs.length > 0" class="rail-divider"></div>

      <button class="rail-add" title="Add Jeffrey Hub" @click="showAddHubModal = true">
        <i class="bi bi-plus-lg"></i>
        <span class="rail-tooltip">
          <strong>Add Jeffrey Hub</strong>
          <small>Connect a new gRPC endpoint</small>
        </span>
      </button>
    </aside>

    <!-- Empty state across both panels when no hubs are connected -->
    <div v-if="hubs.length === 0" class="empty-canvas">
      <div class="empty-hero-content">
        <div class="empty-hero-icon"><i class="bi bi-hdd-network"></i></div>
        <h2>Connect your first hub</h2>
        <p>{{ appDescription }}</p>
        <div class="empty-hero-actions">
          <button class="btn btn-primary btn-lg" @click="showAddHubModal = true">
            <i class="bi bi-plus-lg me-1"></i> Add Hub
          </button>
        </div>
      </div>
    </div>

    <template v-else-if="selectedHub">
      <!-- Workspace column: header for selected hub + workspace list + create form -->
      <section class="workspace-column">
        <header class="ws-head">
          <div class="ws-head-row">
            <h3 class="ws-head-name">
              <i class="bi bi-hdd-network text-primary"></i>
              {{ selectedHub.name }}
              <Badge v-if="isConfigManaged(selectedHub)" value="Config" variant="info" size="xs" />
            </h3>
            <button
              class="ws-head-btn danger"
              :disabled="isConfigManaged(selectedHub)"
              :title="
                isConfigManaged(selectedHub)
                  ? 'Declared in jeffrey.microscope.hubs — remove it from the configuration and restart to delete'
                  : 'Remove hub (does not delete data on the hub)'
              "
              @click="confirmDeleteHub"
            >
              <i class="bi bi-trash"></i>
            </button>
          </div>
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
                v-if="activeTabId === 'projects'"
                key-label="Projects"
                :value="projects.length"
                variant="secondary"
                size="s"
                :uppercase="false"
                :borderless="true"
              />
            </div>
            <div class="main-head-actions">
              <div v-if="activeTabId === 'projects'" class="search">
                <i class="bi bi-search"></i>
                <input
                  v-model="projectSearchQuery"
                  type="text"
                  placeholder="Search projects…"
                />
              </div>
              <slot
                v-if="activeTabId !== 'projects'"
                name="header-controls"
                :active-tab-id="activeTabId"
                :is-projects-tab="false"
              />
              <div
                v-if="extraTabs.length > 0"
                class="view-switcher"
                role="tablist"
                aria-label="View"
              >
                <button
                  v-for="tab in allTabs"
                  :key="tab.id"
                  type="button"
                  role="tab"
                  :aria-selected="activeTabId === tab.id"
                  :class="{ active: activeTabId === tab.id }"
                  @click="selectTab(tab.id)"
                >
                  <i class="bi" :class="tab.icon"></i>
                  {{ tab.label }}
                </button>
              </div>
              <button class="icon-btn" title="Workspace info" @click="showWorkspaceInfo = true">
                <i class="bi bi-info-circle"></i>
              </button>
              <button
                v-if="isActiveTabRefreshable"
                class="icon-btn"
                title="Refresh"
                @click="refreshActiveView"
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

          <template v-if="activeTabId === 'projects'">
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
              description="Projects appear here when provisioner reports them via workspace events."
            />
            <div v-else class="project-grid">
              <slot
                v-for="project in filteredProjects"
                :key="project.id"
                name="project"
                :project="project"
                :hub-id="selectedHubId!"
                :workspace-id="selectedWorkspace.id"
                :restore="handleRestoreProject"
              />
            </div>
          </template>

          <template v-for="tab in extraTabs" :key="tab.id">
            <slot
              v-if="activeTabId === tab.id"
              :name="`tab-${tab.id}`"
              :hub-id="selectedHubId!"
              :workspace-id="selectedWorkspace.id"
              :workspace-name="selectedWorkspace.name"
            />
          </template>
        </template>

        <!-- Hub has no workspaces yet -->
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
    <AddHubModal v-model:show="showAddHubModal" @hub-added="handleHubAdded" />

    <LeftDrawer
      v-model:show="showCreateForm"
      title="Create Workspace"
      icon="bi-folder-plus"
      @submit="submitCreate"
    >
      <DrawerSection v-if="selectedHub" label="Target Hub" icon="bi-hdd-network">
        <div class="hub-context-card">
          <div class="hub-context-name">{{ selectedHub.name }}</div>
          <span class="hub-context-pill">
            <span class="status-dot"></span>
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
            unique on this hub.
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

      <DrawerSection v-if="selectedHub" label="Hub" icon="bi-hdd-network">
        <div class="info-rows">
          <InfoRow label="Name">{{ selectedHub.name }}</InfoRow>
          <InfoRow label="Address" mono>{{ selectedHub.hostname }}:{{ selectedHub.port }}</InfoRow>
        </div>
      </DrawerSection>
    </LeftDrawer>

    <ConfirmationDialog
      v-model:show="showDeleteHubModal"
      title="Remove Hub"
      :message="deleteHubMessage"
      sub-message="This removes the local pointer; data on the hub is not affected."
      confirm-label="Remove"
      confirm-button-class="btn-danger"
      @confirm="deleteHub"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ConfirmationDialog from '@shared/components/ConfirmationDialog.vue';
import AddHubModal from '@hubs/components/projects/AddHubModal.vue';
import LeftDrawer from '@shared/components/LeftDrawer.vue';
import DrawerSection from '@shared/components/drawer/DrawerSection.vue';
import DrawerField from '@shared/components/drawer/DrawerField.vue';
import InfoRow from '@shared/components/drawer/InfoRow.vue';
import Badge from '@shared/components/Badge.vue';
import ToastService from '@shared/services/ToastService';
import HubClient from '@hubs/services/api/HubClient';
import WorkspaceClient from '@hubs/services/api/WorkspaceClient';
import WorkspaceProjectsClient from '@hubs/services/api/WorkspaceProjectsClient';
import ProjectClient from '@hubs/services/api/ProjectClient';
import Hub from '@hubs/services/api/model/Hub';
import Workspace from '@hubs/services/api/model/Workspace';
import Project from '@hubs/services/api/model/Project';

interface ExtraTab {
  id: string;
  label: string;
  icon: string;
  refreshable?: boolean;
}

const props = withDefaults(
  defineProps<{
    appDescription: string;
    extraTabs?: ExtraTab[];
    // Optional deep-link: preselect a hub + workspace on first load (e.g. from a
    // breadcrumb). Only seeds the INITIAL default; manual selection still wins afterwards.
    initialHubId?: string | null;
    initialWorkspaceId?: string | null;
  }>(),
  {
    extraTabs: () => [],
    initialHubId: null,
    initialWorkspaceId: null
  }
);

const emit = defineEmits<{
  (e: 'refresh-tab', tabId: string): void;
  (e: 'tab-change', tabId: string): void;
  (e: 'workspace-change', payload: { hubId: string | null; workspaceId: string | null }): void;
}>();

type HubStatus = 'online' | 'offline' | 'unknown';

const PROJECTS_TAB_ID = 'projects';

const hubClient = new HubClient();

const preferredHubId = (): string | null => {
  const wanted = props.initialHubId;
  if (wanted && hubs.value.some(s => s.id === wanted)) {
    return wanted;
  }
  return hubs.value[0]?.id ?? null;
};

const preferredWorkspaceId = (): string | null => {
  const wanted = props.initialWorkspaceId;
  if (wanted && workspaces.value.some(w => w.id === wanted)) {
    return wanted;
  }
  return workspaces.value[0]?.id ?? null;
};

const hubs = ref<Hub[]>([]);
const selectedHubId = ref<string | null>(null);
const hubStatuses = ref<Record<string, HubStatus>>({});

const workspaces = ref<Workspace[]>([]);
const selectedWorkspaceId = ref<string | null>(null);
const loadingWorkspaces = ref(false);
const workspacesError = ref<string | null>(null);
const searchQuery = ref('');

const projects = ref<Project[]>([]);
const loadingProjects = ref(false);
const projectsError = ref<string | null>(null);
const projectSearchQuery = ref('');

const activeTabId = ref(PROJECTS_TAB_ID);

const showAddHubModal = ref(false);
const showDeleteHubModal = ref(false);
const showWorkspaceInfo = ref(false);

const showCreateForm = ref(false);
const creating = ref(false);
const createError = ref<string | null>(null);
const createForm = ref({ name: '', referenceId: '' });

const WORKSPACE_REF_ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9-]{1,62}[a-zA-Z0-9]$/;
const WORKSPACE_REF_ID_HINT =
  '3-64 characters, alphanumeric and dashes only, no leading or trailing dash.';

const allTabs = computed(() => [
  { id: PROJECTS_TAB_ID, label: 'Projects', icon: 'bi-folder2' },
  ...props.extraTabs
]);

const isActiveTabRefreshable = computed(() => {
  if (activeTabId.value === PROJECTS_TAB_ID) {
    return true;
  }
  return props.extraTabs.find(t => t.id === activeTabId.value)?.refreshable === true;
});

const selectedHub = computed(() => hubs.value.find(s => s.id === selectedHubId.value));

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

const deleteHubMessage = computed(() =>
  selectedHub.value
    ? `Remove hub "${selectedHub.value.name}" (${selectedHub.value.hostname}:${selectedHub.value.port})?`
    : ''
);

// A hub declared in configuration is recreated on every startup, so deleting it here would only
// look like it worked. The optional `source` means a backend that does not send it stays editable.
const isConfigManaged = (hub: Hub | null | undefined): boolean =>
  hub?.source === 'CONFIG';

const statusLabelFor = (hubId: string): string => {
  switch (hubStatuses.value[hubId]) {
    case 'online':
      return '● Online';
    case 'offline':
      return '● Unreachable';
    default:
      return '● Checking…';
  }
};

const probeHub = async (hubId: string) => {
  try {
    await new WorkspaceClient(hubId).list({ suppressToast: true });
    hubStatuses.value = { ...hubStatuses.value, [hubId]: 'online' };
  } catch {
    hubStatuses.value = { ...hubStatuses.value, [hubId]: 'offline' };
  }
};

// Initials for the rail avatar: take the first letter of up to two words, uppercase.
const initials = (name: string): string => {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return '?';
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[1][0]).toUpperCase();
};

const refreshServers = async () => {
  try {
    hubs.value = await hubClient.list({ suppressToast: true });

    // Probe each hub's gRPC endpoint in parallel; "unknown" until the probe returns.
    const initial: Record<string, HubStatus> = {};
    for (const s of hubs.value) {
      initial[s.id] = hubStatuses.value[s.id] ?? 'unknown';
    }
    hubStatuses.value = initial;
    Promise.all(hubs.value.map(s => probeHub(s.id)));

    if (!selectedHubId.value && hubs.value.length > 0) {
      selectedHubId.value = preferredHubId();
      await refreshWorkspaces();
    } else if (hubs.value.length === 0) {
      selectedHubId.value = null;
      workspaces.value = [];
      selectedWorkspaceId.value = null;
      projects.value = [];
    } else if (!hubs.value.some(s => s.id === selectedHubId.value)) {
      selectedHubId.value = preferredHubId();
      await refreshWorkspaces();
    }
  } catch {
    ToastService.error('Failed to load hubs', 'Cannot reach the local backend.');
    hubs.value = [];
    selectedHubId.value = null;
  }
};

const refreshWorkspaces = async () => {
  if (!selectedHubId.value) {
    workspaces.value = [];
    selectedWorkspaceId.value = null;
    projects.value = [];
    return;
  }
  loadingWorkspaces.value = true;
  workspacesError.value = null;
  const sid = selectedHubId.value;
  try {
    const client = new WorkspaceClient(sid);
    workspaces.value = await client.list({ suppressToast: true });
    hubStatuses.value = { ...hubStatuses.value, [sid]: 'online' };
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
    workspaces.value = [];
    selectedWorkspaceId.value = null;
    projects.value = [];
    hubStatuses.value = { ...hubStatuses.value, [sid]: 'offline' };
  } finally {
    loadingWorkspaces.value = false;
  }
};

const refreshProjects = async () => {
  if (!selectedHubId.value || !selectedWorkspaceId.value) {
    projects.value = [];
    return;
  }
  loadingProjects.value = true;
  projectsError.value = null;
  try {
    const client = new WorkspaceProjectsClient(selectedHubId.value, selectedWorkspaceId.value);
    projects.value = await client.list();
  } catch (error: any) {
    projectsError.value =
      error?.response?.data?.message ?? error?.message ?? 'Could not load projects';
    projects.value = [];
  } finally {
    loadingProjects.value = false;
  }
};

const selectHub = (hubId: string) => {
  if (selectedHubId.value === hubId) {
    return;
  }
  selectedHubId.value = hubId;
  selectedWorkspaceId.value = null;
  projects.value = [];
  closeCreateForm();
  refreshWorkspaces();
};

const selectTab = (tabId: string) => {
  activeTabId.value = tabId;
  emit('tab-change', tabId);
};

const selectWorkspace = (workspaceId: string) => {
  if (selectedWorkspaceId.value === workspaceId) {
    return;
  }
  selectedWorkspaceId.value = workspaceId;
  projectSearchQuery.value = '';
  activeTabId.value = PROJECTS_TAB_ID;
  emit('tab-change', PROJECTS_TAB_ID);
  emit('workspace-change', {
    hubId: selectedHubId.value,
    workspaceId
  });
  refreshProjects();
};

const refreshActiveView = () => {
  if (activeTabId.value === PROJECTS_TAB_ID) {
    refreshProjects();
  } else {
    emit('refresh-tab', activeTabId.value);
  }
};

const handleHubAdded = async () => {
  await refreshServers();
};

const confirmDeleteHub = () => {
  if (!selectedHub.value || isConfigManaged(selectedHub.value)) {
    return;
  }
  showDeleteHubModal.value = true;
};

const deleteHub = async () => {
  if (!selectedHub.value) {
    return;
  }
  try {
    await hubClient.delete(selectedHub.value.id);
    ToastService.success('Hub Removed', `Removed ${selectedHub.value.name}.`);
    selectedHubId.value = null;
    selectedWorkspaceId.value = null;
    await refreshServers();
  } catch (error: any) {
    ToastService.error('Failed to remove hub', error?.message ?? 'Unknown error');
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
  if (!selectedHubId.value || !isCreateFormValid.value) {
    return;
  }
  creating.value = true;
  createError.value = null;
  try {
    const client = new WorkspaceClient(selectedHubId.value);
    const created = await client.create({
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
  if (!selectedHubId.value) {
    return;
  }
  if (!confirm(`Delete workspace "${workspace.name}"? This is permanent.`)) {
    return;
  }
  try {
    const client = new WorkspaceClient(selectedHubId.value);
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
      error?.response?.data?.message ?? error?.message ?? 'Unknown error'
    );
  }
};

const handleRestoreProject = async (projectId: string) => {
  if (!selectedHubId.value || !selectedWorkspaceId.value) {
    return;
  }
  try {
    const client = new ProjectClient(selectedHubId.value, selectedWorkspaceId.value, projectId);
    await client.restore();
    ToastService.success('Project Restored', 'Project has been restored.');
    await refreshProjects();
  } catch (error: any) {
    ToastService.error(
      'Failed to restore project',
      error?.response?.data?.message ?? error?.message ?? 'Unknown error'
    );
  }
};

onMounted(refreshServers);
</script>

<style scoped>
@import '@shared/styles/shared-components.css';

/* ============== Layout ============== */
.projects-layout {
  display: grid;
  grid-template-columns: 72px 280px 1fr;
  gap: var(--spacing-3);
  align-items: stretch;
  min-height: calc(100vh - 220px);
}

/* ============== Hub rail (Slack/Discord-style) ============== */
.hub-rail {
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

.rail-avatar.is-online .status-dot {
  background: var(--color-success);
}
.rail-avatar.is-offline .status-dot {
  background: var(--color-danger);
}
.rail-avatar.is-unknown .status-dot {
  background: var(--color-warning);
}

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
  transition:
    opacity 0.15s,
    visibility 0s linear 0.15s;
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
  transition:
    opacity 0.15s,
    visibility 0s linear 0s;
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

.view-switcher {
  display: inline-flex;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 3px;
  height: 32px;
}

.view-switcher button {
  border: none;
  background: transparent;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text);
  padding: 0 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition:
    color var(--transition-base),
    background var(--transition-base);
}

.view-switcher button.active {
  background: var(--color-card, var(--color-white));
  color: var(--color-primary-hover);
  box-shadow: var(--shadow-sm);
}

.view-switcher button:not(.active):hover {
  color: var(--color-text-dark);
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
