<template>
  <div>
    <!-- Workspace Selector (only show in root workspace selection mode) -->
    <MainCard v-if="!isWorkspaceScoped">
      <template #header>
        <MainCardHeader icon="bi bi-collection" title="Workspaces" :badge="workspaces.length">
          <template #actions>
            <button class="page-header-btn" @click="showRemoteWorkspaceModal = true">
              <i class="bi bi-plus-lg"></i>
              Add Workspace
            </button>
          </template>
        </MainCardHeader>
      </template>
      <div class="workspace-cards-grid">
        <WorkspaceSelectionCard
          v-for="workspace in workspaces"
          :key="workspace.id"
          :name="workspace.name ?? workspace.id"
          :description="getWorkspaceDescription(workspace)"
          :selected="selectedWorkspace === workspace.id"
          :badge-value="getWorkspaceProjectCount(workspace.id)"
          :status="workspace.status"
          :show-status-badges="true"
          @select="handleWorkspaceClick(workspace.id)"
        />
      </div>
    </MainCard>

    <MainCard>
      <template #header>
        <!-- Compact Workspace Context Bar (sticky header) -->
        <div
          v-if="isWorkspaceScoped || getSelectedWorkspace()"
          class="workspace-context-bar"
          :class="getContextBarClass"
        >
          <div class="context-bar-info">
            <span class="workspace-name">{{
              getSelectedWorkspace()?.name ?? getSelectedWorkspace()?.id
            }}</span>
            <span class="context-divider">•</span>
            <span class="workspace-meta">{{ getProjectCountText() }}</span>
            <span class="context-divider">•</span>
            <span class="workspace-created"
              >Created
              {{ FormattingService.formatRelativeTime(getSelectedWorkspace()?.createdAt) }}</span
            >
          </div>
          <div class="context-bar-actions">
            <div class="context-search">
              <i class="bi bi-search"></i>
              <input
                type="text"
                v-model="searchQuery"
                placeholder="Search..."
                @input="filterProjects"
              />
            </div>
            <button
              v-if="hasBlockedProjects"
              class="context-btn"
              :class="{ active: showBlockedProjects }"
              @click="toggleBlockedProjects"
              :title="showBlockedProjects ? 'Hide blocked projects' : 'Show blocked projects'"
            >
              <i class="bi bi-eye-slash"></i>
            </button>
            <button
              class="context-btn"
              :class="getStreamingButtonClass()"
              @click="cycleWorkspaceStreaming"
              :title="getStreamingTooltip()"
            >
              <i class="bi bi-broadcast"></i>
            </button>
            <button
              class="context-btn danger"
              @click="handleDeleteWorkspace()"
              :disabled="!canDeleteWorkspace()"
              :title="getDeleteTooltip()"
            >
              <i class="bi bi-trash"></i>
            </button>
          </div>
        </div>

        <!-- Empty State (no workspace selected) -->
        <div v-else class="workspace-context-empty">
          <i class="bi bi-folder"></i>
          <span>Select a workspace to view projects</span>
        </div>
      </template>
      <!-- Offline Workspace Info -->
      <div
        v-if="getSelectedWorkspace()?.status === WorkspaceStatus.OFFLINE"
        class="workspace-offline-info mb-4"
      >
        <div class="alert alert-danger d-flex align-items-center">
          <i class="bi bi-wifi-off me-2 fs-5"></i>
          <div>
            <strong>Remote workspace is offline</strong><br />
            <small class="text-muted"
              >Existing live projects are shown below. Virtual projects cannot be loaded from the
              remote Jeffrey instance until connection is restored.</small
            >
          </div>
        </div>
      </div>

      <!-- Unavailable Workspace — removed from server -->
      <div
        v-if="getSelectedWorkspace()?.status === WorkspaceStatus.UNAVAILABLE"
        class="workspace-status-banner unavailable-banner mb-4"
      >
        <div class="banner-content">
          <i class="bi bi-folder-x banner-icon"></i>
          <div class="banner-text">
            <strong>This workspace has been removed from the remote server</strong>
            <span class="banner-hint"
              >All local data (profiles, recordings) will be deleted when you remove this
              workspace.</span
            >
          </div>
        </div>
        <button class="btn-action btn-action-secondary" @click="handleDeleteWorkspace()">
          <i class="bi bi-trash3"></i>
          Delete Workspace
        </button>
      </div>

      <!-- Offline Workspace — server unreachable -->
      <div
        v-else-if="getSelectedWorkspace()?.status === WorkspaceStatus.OFFLINE"
        class="workspace-status-banner offline-banner mb-4"
      >
        <div class="banner-content">
          <i class="bi bi-wifi-off banner-icon"></i>
          <div class="banner-text">
            <strong>Cannot reach the remote server</strong>
            <span class="banner-hint"
              >The server may be down or there is a network issue. Projects cannot be loaded.</span
            >
          </div>
        </div>
        <button class="btn-action btn-action-secondary" @click="refreshProjects">
          <i class="bi bi-arrow-clockwise"></i>
          Retry
        </button>
      </div>

      <!-- Loading indicator -->
      <div v-if="loading" class="text-center py-4">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
        <p class="mt-2">Loading projects from server...</p>
      </div>

      <!-- Error state -->
      <div v-else-if="errorMessage" class="text-center py-4">
        <i class="bi bi-exclamation-triangle-fill fs-1 text-warning mb-3"></i>
        <h5>Failed to load projects</h5>
        <p class="text-muted">{{ errorMessage }}</p>
        <button class="btn btn-primary mt-2" @click="refreshProjects">
          <i class="bi bi-arrow-clockwise me-2"></i>Retry
        </button>
      </div>

      <!-- Projects Grid -->
      <div v-else-if="filteredProjects.length > 0" class="row g-4">
        <div v-for="project in filteredProjects" :key="project.id" class="col-12 col-md-6 col-lg-4">
          <ProjectCard
            :project="project"
            :workspace-id="isWorkspaceScoped ? workspaceId : selectedWorkspace"
          />
        </div>
      </div>

      <!-- Empty state -->
      <EmptyState
        v-else-if="!errorMessage"
        icon="bi-folder-plus"
        title="No projects found"
        :description="
          getSelectedWorkspace()?.status === WorkspaceStatus.OFFLINE
            ? 'Remote workspace is offline. Projects would be shown here when the connection is restored.'
            : 'Projects in this workspace are synchronized from external source'
        "
      />
    </MainCard>
  </div>

  <!-- Modal Components -->
  <RemoteWorkspaceModal
    v-model:show="showRemoteWorkspaceModal"
    @workspace-added="handleWorkspaceAdded"
  />

  <ConfirmationDialog
    v-model:show="showDeleteWorkspaceModal"
    title="Delete Workspace"
    :message="deleteWorkspaceMessage"
    :sub-message="deleteWorkspaceSubMessage"
    confirm-label="Delete"
    confirm-button-class="btn-danger"
    @confirm="confirmDeleteWorkspace"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import ProjectCard from '@/components/ProjectCard.vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import RemoteWorkspaceModal from '@/components/projects/RemoteWorkspaceModal.vue';
import WorkspaceSelectionCard from '@/components/settings/WorkspaceSelectionCard.vue';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import EmptyState from '@/components/EmptyState.vue';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import ProjectsClient from '@/services/api/ProjectsClient.ts';
import WorkspaceProjectsClient from '@/services/api/WorkspaceProjectsClient.ts';
import Project from '@/services/api/model/Project.ts';
import WorkspaceClient from '@/services/api/WorkspaceClient.ts';

const workspaceClient = new WorkspaceClient();
const projectsClient = new ProjectsClient();
import Workspace from '@/services/api/model/Workspace.ts';
import WorkspaceStatus from '@/services/api/model/WorkspaceStatus.ts';
import { useRoute } from 'vue-router';

// Get workspace context directly from route params (not useNavigation, which has a profileStore fallback)
const route = useRoute();
const workspaceId = computed(() => route.params.workspaceId as string);

// Determine if we're in workspace-scoped mode
const isWorkspaceScoped = computed(() => !!workspaceId.value);

// State for Workspaces
const workspaces = ref<Workspace[]>([]);
const selectedWorkspace = ref<string>('');

// State for Projects
const projects = ref<Project[]>([]);
const filteredProjects = ref<Project[]>([]);
const searchQuery = ref('');
const showBlockedProjects = ref(false);
const hasBlockedProjects = computed(() => projects.value.some(p => p.isBlocked));
const errorMessage = ref('');
const loading = ref(true);

// Modal state
const showRemoteWorkspaceModal = ref(false);
const showDeleteWorkspaceModal = ref(false);
const deleteWorkspaceMessage = ref('');
const deleteWorkspaceSubMessage = ref('');

// Workspace header styling computed properties
const getWorkspaceHeaderIcon = computed(() => 'bi bi-cloud-fill');

const getWorkspaceHeaderLabel = computed(() => 'REMOTE');

// Context bar styling computed properties
const getContextBarClass = computed(() => 'context-bar-remote');

const getTypeBadgeClass = computed(() => 'badge-remote');

// Fetch workspaces function
const refreshWorkspaces = async () => {
  try {
    // All workspaces come from the backend now
    workspaces.value = await workspaceClient.list();

    // Set selected workspace: first workspace in the list
    if (!selectedWorkspace.value && workspaces.value.length > 0) {
      selectedWorkspace.value = workspaces.value[0].id;
    }
  } catch (error) {
    console.error('Failed to load workspaces:', error);
    ToastService.error('Failed to load workspaces', 'Cannot load workspaces from the server.');
    // Clear workspaces on error
    workspaces.value = [];
    selectedWorkspace.value = '';
  }
};

// Resolve actual workspace statuses by calling the single-workspace endpoint in parallel
const resolveWorkspaceStatuses = async () => {
  const resolvePromises = workspaces.value.map(async (workspace, index) => {
    try {
      const resolved = await workspaceClient.getById(workspace.id);
      workspaces.value[index] = resolved;
    } catch (error) {
      console.error('Failed to resolve workspace status:', error);
    }
  });
  await Promise.all(resolvePromises);
};

// Fetch projects function
const refreshProjects = async () => {
  loading.value = true;
  errorMessage.value = '';

  try {
    // In workspace-scoped mode, use the workspace from route
    const targetWorkspaceId = isWorkspaceScoped.value ? workspaceId.value : selectedWorkspace.value;

    // Check if workspace is selected
    if (!targetWorkspaceId) {
      projects.value = [];
      filteredProjects.value = [];
      errorMessage.value = '';
      return;
    }

    // In workspace-scoped mode, we might not have workspace details loaded yet
    if (isWorkspaceScoped.value) {
      // Use workspace-scoped client
      const workspaceProjectsClient = new WorkspaceProjectsClient(targetWorkspaceId);
      projects.value = await workspaceProjectsClient.list();
      filteredProjects.value = [...projects.value];
    } else {
      // Check if workspace is offline before trying to load projects (only in selection mode)
      const workspace = workspaces.value.find(w => w.id === targetWorkspaceId);

      if (!workspace) {
        projects.value = [];
        filteredProjects.value = [];
        errorMessage.value = 'Selected workspace not found.';
        return;
      }

      // Handle different workspace statuses gracefully
      if (workspace.status === WorkspaceStatus.UNAVAILABLE) {
        projects.value = [];
        filteredProjects.value = [];
        errorMessage.value = 'Workspace is unavailable. It may have been deleted from the server.';
        return;
      }

      // For offline remote workspaces, still try to load existing projects
      // This allows displaying already created projects that contain data
      // The alert will inform users about limitations for virtual projects

      // Use standard API for workspace selection mode
      projects.value = await projectsClient.list(targetWorkspaceId);
      filteredProjects.value = [...projects.value];
    }
  } catch (error) {
    console.error('Failed to load projects:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Could not connect to server';
    ToastService.error('Failed to load projects', 'Cannot load projects from the server.');
  } finally {
    loading.value = false;
  }
};

// Select workspace
const selectWorkspace = (workspaceId: string) => {
  selectedWorkspace.value = workspaceId;
  // Refresh projects when workspace changes
  refreshProjects();
};

// Handle workspace added
const handleWorkspaceAdded = async () => {
  // Refresh workspaces to get updated list including the new remote workspace
  await refreshWorkspaces();
  resolveWorkspaceStatuses();
  await refreshProjects();
};

// Get project count for workspace
const getWorkspaceProjectCount = (workspaceId: string) => {
  const workspace = workspaces.value.find(w => w.id === workspaceId);
  return workspace?.projectCount ?? '?';
};

// Get workspace description
const getWorkspaceDescription = (workspace: Workspace | undefined) => {
  if (!workspace) {
    return 'No workspace selected';
  }
  if (workspace.status === WorkspaceStatus.OFFLINE) {
    return 'Remote workspace is offline';
  } else if (workspace.status === WorkspaceStatus.UNAVAILABLE) {
    return 'Workspace unavailable';
  } else if (workspace.description) {
    return workspace.description;
  } else if (workspace.name) {
    return `Projects for ${workspace.name}`;
  } else {
    return 'Remote workspace';
  }
};

// Fetch projects on component mount
onMounted(async () => {
  // In workspace-scoped mode, we only need to load projects
  if (isWorkspaceScoped.value) {
    await refreshProjects();
  } else {
    // In workspace selection mode, load workspaces first, then projects
    await refreshWorkspaces();
    await refreshProjects();
    // Resolve actual workspace statuses in parallel (non-blocking for UI)
    resolveWorkspaceStatuses();
  }
});

const filterProjects = () => {
  let result = projects.value;

  // Filter blocked projects unless toggle is on
  if (!showBlockedProjects.value) {
    result = result.filter(project => !project.isBlocked);
  }

  // Apply search filter
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(project => project.name.toLowerCase().includes(query));
  }

  filteredProjects.value = result;
};

const toggleBlockedProjects = () => {
  showBlockedProjects.value = !showBlockedProjects.value;
  filterProjects();
};

const handleWorkspaceClick = (workspaceId: string) => {
  // Allow all workspace clicks - status information is now shown via visual indicators
  // In workspace selection mode, update the selected workspace
  selectWorkspace(workspaceId);
};

// Get selected workspace object
const getSelectedWorkspace = (): Workspace | undefined => {
  return workspaces.value.find(w => w.id === selectedWorkspace.value);
};

// Get project count text
const getProjectCountText = (): string => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return 'No projects';

  const count = workspace.projectCount;
  if (count === 0) return 'No projects';
  if (count === 1) return '1 project';
  return `${count} projects`;
};

// Check if workspace can be deleted
const canDeleteWorkspace = (): boolean => {
  const workspace = getSelectedWorkspace();
  return !!workspace;
};

// Get delete button tooltip
const getDeleteTooltip = (): string => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return '';

  return 'Remove workspace (does not affect remote source)';
};

// Handle workspace deletion - show confirmation modal
const handleDeleteWorkspace = () => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return;

  deleteWorkspaceMessage.value = `Remove workspace "${workspace.name}"?`;
  deleteWorkspaceSubMessage.value = `This will remove the local reference but won't affect the remote source.`;

  showDeleteWorkspaceModal.value = true;
};

// Get workspace streaming button CSS class
const getStreamingButtonClass = (): string => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return '';
  if (workspace.streamingEnabled === true) return 'streaming-enabled';
  if (workspace.streamingEnabled === false) return 'streaming-disabled';
  return '';
};

// Get workspace streaming tooltip
const getStreamingTooltip = (): string => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return '';
  if (workspace.streamingEnabled === true) return 'Streaming: enabled (click to disable)';
  if (workspace.streamingEnabled === false)
    return 'Streaming: disabled (click to reset to inherited)';
  return 'Streaming: inherited from global (click to enable)';
};

// Cycle workspace streaming: null -> true -> false -> null
const cycleWorkspaceStreaming = async () => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return;

  let nextState: boolean | null;
  if (workspace.streamingEnabled === null || workspace.streamingEnabled === undefined) {
    nextState = true;
  } else if (workspace.streamingEnabled === true) {
    nextState = false;
  } else {
    nextState = null;
  }

  try {
    await workspaceClient.updateStreaming(workspace.id, nextState);
    workspace.streamingEnabled = nextState;

    if (nextState === true) {
      ToastService.success('Streaming Enabled', 'Workspace streaming has been enabled.');
    } else if (nextState === false) {
      ToastService.success('Streaming Disabled', 'Workspace streaming has been disabled.');
    } else {
      ToastService.success('Streaming Reset', 'Workspace streaming reset to inherit from global.');
    }
  } catch (error) {
    console.error('Failed to update workspace streaming:', error);
    ToastService.error('Error', 'Failed to update workspace streaming setting.');
  }
};

// Confirm workspace deletion
const confirmDeleteWorkspace = async () => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return;

  try {
    await workspaceClient.delete(workspace.id);

    // Refresh workspaces and select a new one
    await refreshWorkspaces();
    resolveWorkspaceStatuses();

    ToastService.success(
      'Workspace Deleted',
      `Workspace "${workspace.name}" has been deleted successfully.`
    );
  } catch (error) {
    console.error('Failed to delete workspace:', error);
    ToastService.error('Failed to delete workspace', 'Could not delete workspace.');
  }
};
</script>

<style scoped>
@import '@/styles/shared-components.css';

/* Workspace Status Banners */
.workspace-status-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-radius: 10px;
  border: 1px solid;
}

.banner-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.banner-icon {
  font-size: 1.2rem;
  flex-shrink: 0;
}

.banner-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 0.85rem;
}

.banner-hint {
  font-size: 0.78rem;
  opacity: 0.7;
}

.unavailable-banner {
  background: linear-gradient(135deg, #f9fafb, #f3f4f6);
  border-color: rgba(156, 163, 175, 0.25);
  color: var(--color-text);
}

.unavailable-banner .banner-icon {
  color: var(--color-text-light);
}

.offline-banner {
  background: linear-gradient(135deg, #fffbeb, #fef3c7);
  border-color: rgba(245, 158, 11, 0.2);
  color: #92400e;
}

.offline-banner .banner-icon {
  color: #f59e0b;
}

.btn-action-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 0.8rem;
  font-weight: 600;
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  cursor: pointer;
  background: white;
  color: var(--color-text);
  transition: all 0.2s ease;
  white-space: nowrap;
}

.btn-action-secondary:hover {
  background: var(--color-light);
  border-color: rgba(0, 0, 0, 0.15);
}

/* Compact Workspace Context Bar */
.workspace-context-bar {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  border-left: 3px solid var(--color-primary);
  border-radius: 16px 16px 0 0;
  gap: 16px;
  flex-wrap: wrap;
}

.context-bar-remote {
  border-left-color: var(--color-primary);
}

.context-bar-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.workspace-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.workspace-type-badge i {
  font-size: 0.7rem;
}

.badge-remote {
  background: linear-gradient(135deg, #e6fffa, #b2f5ea);
  color: #234e52;
}

.workspace-name {
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.workspace-meta {
  font-size: 0.8rem;
  color: var(--color-primary);
  font-weight: 500;
  white-space: nowrap;
}

.workspace-created {
  font-size: 0.8rem;
  color: var(--color-text-light);
  white-space: nowrap;
}

.context-divider {
  color: #d1d5db;
  font-weight: bold;
}

.context-bar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.context-search {
  display: flex;
  align-items: center;
  background: var(--color-light);
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 6px 10px;
  width: 160px;
  transition: all 0.2s ease;
}

.context-search:hover {
  border-color: #d1d5db;
}

.context-search:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(94, 100, 255, 0.1);
  background: #fff;
}

.context-search i {
  font-size: 0.8rem;
  color: var(--color-text-light);
  margin-right: 6px;
}

.context-search:focus-within i {
  color: var(--color-primary);
}

.context-search input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 0.8rem;
  color: var(--color-text);
  width: 100%;
  padding: 0;
}

.context-search input::placeholder {
  color: var(--color-text-light);
}

.context-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  white-space: nowrap;
}

.context-btn.danger {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  border-color: rgba(239, 68, 68, 0.3);
  color: var(--color-danger-hover);

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, var(--color-danger), var(--color-danger-hover));
    color: white;
    border-color: var(--color-danger-hover);
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(220, 38, 38, 0.25);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;

    &:hover {
      background: linear-gradient(135deg, #fef2f2, #fee2e2);
      color: var(--color-danger-hover);
      transform: none;
      box-shadow: none;
    }
  }
}

.context-btn.streaming-enabled {
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  border-color: rgba(34, 197, 94, 0.3);
  color: #16a34a;

  &:hover {
    background: linear-gradient(135deg, #16a34a, #15803d);
    color: white;
    border-color: #15803d;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(22, 163, 74, 0.25);
  }
}

.context-btn.streaming-disabled {
  background: linear-gradient(135deg, #f9fafb, #f3f4f6);
  border-color: rgba(156, 163, 175, 0.3);
  color: var(--color-text-light);

  &:hover {
    background: linear-gradient(135deg, #6b7280, #4b5563);
    color: white;
    border-color: var(--color-text);
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(107, 114, 128, 0.25);
  }
}

.context-btn i {
  font-size: 0.8rem;
}

/* Empty state for context bar */
.workspace-context-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px 20px;
  background: var(--color-light);
  border-bottom: 1px dashed #d1d5db;
  border-radius: 16px 16px 0 0;
  color: var(--color-text-light);
  font-size: 0.9rem;
  font-weight: 500;
}

.workspace-context-empty i {
  font-size: 1.1rem;
}

/* Responsive adjustments for context bar */
@media (max-width: 768px) {
  .workspace-context-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .context-bar-info {
    justify-content: flex-start;
  }

  .workspace-name {
    max-width: 150px;
  }

  .context-bar-actions {
    justify-content: flex-end;
  }

  .context-search {
    width: 120px;
  }
}
</style>
