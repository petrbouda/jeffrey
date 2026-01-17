<template>
  <div>
    <!-- Workspace Selector (only show in root workspace selection mode) -->
    <div v-if="!isWorkspaceScoped" class="workspace-selector-card mb-4">
      <div class="workspace-selector-content">
        <div class="workspace-cards-container">
          <div class="workspace-cards-header">
            <span class="workspace-label">Workspaces</span>
            <div class="workspace-actions">
              <button class="add-workspace-btn" @click="liveWorkspaceModal?.showModal()">
                Add Live
              </button>
              <button class="remote-workspace-btn" @click="remoteWorkspaceModal?.showModal()">
                Add Remote
              </button>
              <button class="sandbox-workspace-btn" @click="handleCreateSandboxWorkspace()">
                Add Sandbox
              </button>
            </div>
          </div>
          <div class="workspace-cards-grid">
            <WorkspaceSelectionCard
              v-for="workspace in workspaces"
              :key="workspace.id"
              :name="workspace.name"
              :description="getWorkspaceDescription(workspace)"
              :selected="selectedWorkspace === workspace.id"
              :workspace-type="workspace.type"
              :badge-value="getWorkspaceProjectCount(workspace.id)"
              :status="workspace.status"
              :show-status-badges="true"
              @select="handleWorkspaceClick(workspace.id)"
            />
          </div>
        </div>
      </div>
    </div>

    <div class="projects-main-card mb-4">
      <div class="projects-main-content">
        <!-- Offline Remote Workspace Info -->
        <div v-if="getSelectedWorkspace()?.status === WorkspaceStatus.OFFLINE && getSelectedWorkspace()?.type === WorkspaceType.REMOTE" class="workspace-offline-info mb-4">
          <div class="alert alert-danger d-flex align-items-center">
            <i class="bi bi-wifi-off me-2 fs-5"></i>
            <div>
              <strong>Remote workspace is offline</strong><br>
              <small class="text-muted">Existing live projects are shown below. Virtual projects cannot be loaded from the remote Jeffrey instance until connection is restored.</small>
            </div>
          </div>
        </div>

        <!-- Unavailable Workspace Info -->
        <div v-if="getSelectedWorkspace()?.status === WorkspaceStatus.UNAVAILABLE" class="workspace-unavailable-info mb-4">
          <div class="alert alert-danger d-flex align-items-center">
            <i class="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
            <div>
              <strong>Workspace cannot find the source path with its projects</strong><br>
              <small class="text-muted">The workspace directory may have been moved, deleted, or is no longer accessible. Check the workspace path and file permissions.</small>
            </div>
          </div>
        </div>

        <!-- Compact Workspace Context Bar -->
        <div v-if="isWorkspaceScoped || getSelectedWorkspace()" class="workspace-context-bar mb-4" :class="getContextBarClass">
          <div class="context-bar-info">
            <span class="workspace-type-badge" :class="getTypeBadgeClass">
              <i :class="getWorkspaceHeaderIcon"></i>
              {{ getWorkspaceHeaderLabel }}
            </span>
            <span class="workspace-name">{{ getSelectedWorkspace()?.name }}</span>
            <span class="context-divider">•</span>
            <span class="workspace-meta">{{ getProjectCountText() }}</span>
            <span class="context-divider">•</span>
            <span class="workspace-created">Created {{ FormattingService.formatRelativeTime(getSelectedWorkspace()?.createdAt) }}</span>
          </div>
          <div class="context-bar-actions">
            <div class="context-search">
              <i class="bi bi-search"></i>
              <input
                  type="text"
                  v-model="searchQuery"
                  placeholder="Search..."
                  @input="filterProjects"
              >
            </div>
            <button
                class="context-btn primary"
                @click="createProjectModal?.showModal()"
                :disabled="!canCreateProjectInWorkspace(selectedWorkspace)"
                :title="getCreateProjectTooltip(selectedWorkspace)"
            >
              <i class="bi bi-plus-lg"></i>
              New
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

        <!-- Empty State -->
        <div v-else class="workspace-context-empty mb-4">
          <i class="bi bi-folder"></i>
          <span>Select a workspace to view projects</span>
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
              :is-orphaned="project.isOrphaned"
            />
          </div>
        </div>

        <!-- Empty state -->
        <div v-else-if="!errorMessage" class="text-center py-5">
          <i class="bi bi-folder-plus fs-1 text-muted mb-3"></i>
          <h5>No projects found</h5>
          <p v-if="getSelectedWorkspaceType() === WorkspaceType.SANDBOX" class="text-muted">Click the "New Project"
            button to create your first project</p>
          <p v-else-if="getSelectedWorkspaceType() === WorkspaceType.REMOTE && getSelectedWorkspace()?.status === WorkspaceStatus.OFFLINE" class="text-muted">Remote workspace is offline. Live projects would be shown here when the connection is restored.</p>
          <p v-else-if="getSelectedWorkspaceType() === WorkspaceType.REMOTE" class="text-muted">Projects in this remote
            workspace are synchronized from external source</p>
          <p v-else class="text-muted">Projects in this workspace are managed by the server</p>
        </div>
      </div>
    </div>
  </div>

  <!-- Modal Components -->
  <LiveWorkspaceModal
      ref="liveWorkspaceModal"
      @workspace-created="handleWorkspaceCreated"
  />

  <RemoteWorkspaceModal
      ref="remoteWorkspaceModal"
      @workspace-added="handleWorkspaceAdded"
  />

  <CreateProjectModal
      ref="createProjectModal"
      :selected-workspace="selectedWorkspace"
      @project-created="handleProjectCreated"
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
import {computed, onMounted, ref} from 'vue';
import ProjectCard from '@/components/ProjectCard.vue';
import LiveWorkspaceModal from '@/components/projects/LiveWorkspaceModal.vue';
import RemoteWorkspaceModal from '@/components/projects/RemoteWorkspaceModal.vue';
import CreateProjectModal from '@/components/projects/CreateProjectModal.vue';
import WorkspaceSelectionCard from '@/components/settings/WorkspaceSelectionCard.vue';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import ProjectsClient from "@/services/api/ProjectsClient.ts";
import WorkspaceProjectsClient from "@/services/api/WorkspaceProjectsClient.ts";
import Project from "@/services/api/model/Project.ts";
import WorkspaceClient from "@/services/api/WorkspaceClient.ts";
import Workspace from "@/services/api/model/Workspace.ts";
import WorkspaceType from "@/services/api/model/WorkspaceType.ts";
import WorkspaceStatus from "@/services/api/model/WorkspaceStatus.ts";
import CreateWorkspaceRequest from "@/services/api/model/CreateWorkspaceRequest.ts";
import {useNavigation} from '@/composables/useNavigation';

// Get workspace context from route
const {workspaceId} = useNavigation();

// Determine if we're in workspace-scoped mode
const isWorkspaceScoped = computed(() => !!workspaceId.value);

// State for Workspaces
const workspaces = ref<Workspace[]>([]);
const selectedWorkspace = ref<string>('');

// State for Projects
const projects = ref<Project[]>([]);
const filteredProjects = ref<Project[]>([]);
const searchQuery = ref('');
const errorMessage = ref('');
const loading = ref(true);

// Modal component references
const liveWorkspaceModal = ref<InstanceType<typeof LiveWorkspaceModal>>();
const remoteWorkspaceModal = ref<InstanceType<typeof RemoteWorkspaceModal>>();
const createProjectModal = ref<InstanceType<typeof CreateProjectModal>>();

// Delete workspace modal state
const showDeleteWorkspaceModal = ref(false);
const deleteWorkspaceMessage = ref('');
const deleteWorkspaceSubMessage = ref('');

const getSelectedWorkspaceType = (): WorkspaceType | undefined => {
  const workspace = workspaces.value.find(w => w.id === selectedWorkspace.value);
  return workspace?.type;
};

// Workspace header styling computed properties
const getWorkspaceHeaderClass = computed(() => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return 'header-live';

  if (workspace.type === WorkspaceType.REMOTE) return 'header-remote';
  if (workspace.type === WorkspaceType.SANDBOX) return 'header-sandbox';
  return 'header-live';
});

const getWorkspaceHeaderIcon = computed(() => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return 'bi bi-folder-fill';

  if (workspace.type === WorkspaceType.REMOTE) return 'bi bi-cloud-fill';
  if (workspace.type === WorkspaceType.SANDBOX) return 'bi bi-house-fill';
  return 'bi bi-folder-fill';
});

const getWorkspaceHeaderLabel = computed(() => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return 'LIVE';

  if (workspace.type === WorkspaceType.REMOTE) return 'REMOTE';
  if (workspace.type === WorkspaceType.SANDBOX) return 'SANDBOX';
  return 'LIVE';
});

// Context bar styling computed properties
const getContextBarClass = computed(() => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return 'context-bar-live';

  if (workspace.type === WorkspaceType.REMOTE) return 'context-bar-remote';
  if (workspace.type === WorkspaceType.SANDBOX) return 'context-bar-sandbox';
  return 'context-bar-live';
});

const getTypeBadgeClass = computed(() => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return 'badge-live';

  if (workspace.type === WorkspaceType.REMOTE) return 'badge-remote';
  if (workspace.type === WorkspaceType.SANDBOX) return 'badge-sandbox';
  return 'badge-live';
});

// Fetch workspaces function
const refreshWorkspaces = async () => {
  try {
    // All workspaces come from the backend now
    workspaces.value = await WorkspaceClient.list();

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
      } else if (workspace.status === WorkspaceStatus.UNKNOWN) {
        projects.value = [];
        filteredProjects.value = [];
        errorMessage.value = 'Workspace status is unknown. Cannot load projects at this time.';
        return;
      }

      // For offline remote workspaces, still try to load existing projects
      // This allows displaying already created projects that contain data
      // The alert will inform users about limitations for virtual projects

      // Use standard API for workspace selection mode
      projects.value = await ProjectsClient.list(targetWorkspaceId);
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

// Handle workspace created
const handleWorkspaceCreated = async () => {
  // Refresh workspaces to get updated list
  await refreshWorkspaces();
  filterProjects();
};

// Handle creating a sandbox workspace
const handleCreateSandboxWorkspace = async () => {
  try {
    // Create the workspace request with SANDBOX type
    const request = new CreateWorkspaceRequest('sandbox', WorkspaceType.SANDBOX);

    // Call the backend to create the workspace and get the returned workspace
    const createdWorkspace = await WorkspaceClient.create(request);

    // Refresh workspaces to get the updated list
    await refreshWorkspaces();

    // Select the newly created workspace using the ID from the backend response
    selectedWorkspace.value = createdWorkspace.id;
    await refreshProjects();

    ToastService.success('Sandbox Workspace Created', 'New sandbox workspace has been created successfully.');
  } catch (error) {
    console.error('Failed to create sandbox workspace:', error);
    ToastService.error('Failed to create workspace', 'Could not create sandbox workspace.');
  }
};


// Handle workspace added
const handleWorkspaceAdded = async () => {
  // Refresh workspaces to get updated list including the new remote workspace
  await refreshWorkspaces();
  filterProjects();
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
  if (workspace.description) {
    return workspace.description;
  } else {
    return `Projects for ${workspace.name}`;
  }
};

// Handle project created
const handleProjectCreated = async () => {
  // Refresh project list to include the new project
  await refreshProjects();
  // Refresh workspaces to update project counts
  await refreshWorkspaces();
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
  }
});


// Check if workspace allows project creation
const canCreateProjectInWorkspace = (workspaceId: string) => {
  const workspace = workspaces.value.find(w => w.id === workspaceId);
  return workspace?.type === WorkspaceType.SANDBOX;
};

// Get tooltip for create project button
const getCreateProjectTooltip = (workspaceId: string) => {
  const workspace = workspaces.value.find(w => w.id === workspaceId);

  if (workspace?.type === WorkspaceType.SANDBOX) {
    return '';
  }

  if (workspace?.type === WorkspaceType.REMOTE) {
    return 'New projects cannot be created in remote workspaces';
  }

  return 'New projects can only be created in Sandbox workspaces';
};


const filterProjects = () => {
  // Apply search filter to all projects (workspace filtering is now handled by backend)
  if (!searchQuery.value.trim()) {
    filteredProjects.value = [...projects.value];
    return;
  }

  const query = searchQuery.value.toLowerCase();
  filteredProjects.value = projects.value.filter(project =>
      project.name.toLowerCase().includes(query)
  );
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

  if (workspace.type === WorkspaceType.REMOTE) {
    return 'Remove remote workspace (does not affect remote source)';
  }

  if (workspace.type === WorkspaceType.SANDBOX) {
    return 'Delete sandbox workspace and all its projects';
  }

  return 'Delete live workspace';
};

// Handle workspace deletion - show confirmation modal
const handleDeleteWorkspace = () => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return;

  const projectCount = projects.value.length;

  if (workspace.type === WorkspaceType.SANDBOX) {
    deleteWorkspaceMessage.value = `Delete sandbox workspace "${workspace.name}"?`;
    deleteWorkspaceSubMessage.value = `This will permanently delete ${projectCount > 0 ? `${projectCount} project${projectCount > 1 ? 's' : ''}` : 'the workspace'} and cannot be undone.`;
  } else if (workspace.type === WorkspaceType.REMOTE) {
    deleteWorkspaceMessage.value = `Remove remote workspace "${workspace.name}"?`;
    deleteWorkspaceSubMessage.value = `This will remove the local reference but won't affect the remote source.`;
  } else {
    deleteWorkspaceMessage.value = `Delete workspace "${workspace.name}"?`;
    deleteWorkspaceSubMessage.value = `This will permanently remove the workspace from the server.`;
  }

  showDeleteWorkspaceModal.value = true;
};

// Confirm workspace deletion
const confirmDeleteWorkspace = async () => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return;

  try {
    await WorkspaceClient.delete(workspace.id);

    // Refresh workspaces and select a new one
    await refreshWorkspaces();

    const actionText = workspace.type === WorkspaceType.SANDBOX ? 'deleted' : 'removed';
    ToastService.success('Workspace ' + actionText.charAt(0).toUpperCase() + actionText.slice(1), `Workspace "${workspace.name}" has been ${actionText} successfully.`);
  } catch (error) {
    console.error('Failed to delete workspace:', error);
    ToastService.error('Failed to delete workspace', 'Could not delete workspace.');
  }
};
</script>

<style scoped>

.new-project-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border: none;
  border-radius: 12px;
  color: white;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.3),
  0 2px 4px rgba(94, 100, 255, 0.2);

  .new-project-plus {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    background: rgba(255, 255, 255, 0.2);
    color: white;
    border-radius: 6px;
    font-size: 14px;
    font-weight: 700;
    line-height: 1;
  }

  &:hover {
    background: linear-gradient(135deg, #4c52ff, #3f46ff);
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(94, 100, 255, 0.4),
    0 3px 6px rgba(94, 100, 255, 0.3);

    .new-project-plus {
      background: rgba(255, 255, 255, 0.3);
    }
  }

  &:active {
    transform: translateY(-1px);
  }

  &:disabled {
    background: linear-gradient(135deg, #9ca3af, #6b7280);
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
    opacity: 0.6;

    .new-project-plus {
      background: rgba(255, 255, 255, 0.1);
    }

    &:hover {
      background: linear-gradient(135deg, #9ca3af, #6b7280);
      transform: none;
      box-shadow: none;

      .new-project-plus {
        background: rgba(255, 255, 255, 0.1);
      }
    }
  }
}

/* Compact Workspace Context Bar */
.workspace-context-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  border-left: 3px solid #5e64ff;
  gap: 16px;
  flex-wrap: wrap;
}

.context-bar-live {
  border-left-color: #5e64ff;
}

.context-bar-remote {
  border-left-color: #38b2ac;
}

.context-bar-sandbox {
  border-left-color: #f59e0b;
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

.badge-live {
  background: linear-gradient(135deg, #eef0ff, #e0e3ff);
  color: #4c52ff;
}

.badge-remote {
  background: linear-gradient(135deg, #e6fffa, #b2f5ea);
  color: #234e52;
}

.badge-sandbox {
  background: linear-gradient(135deg, #fff9e6, #fef3cd);
  color: #92400e;
}

.workspace-name {
  font-weight: 600;
  font-size: 0.95rem;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.workspace-meta {
  font-size: 0.8rem;
  color: #5e64ff;
  font-weight: 500;
  white-space: nowrap;
}

.workspace-created {
  font-size: 0.8rem;
  color: #9ca3af;
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
  background: #f9fafb;
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
  border-color: #5e64ff;
  box-shadow: 0 0 0 2px rgba(94, 100, 255, 0.1);
  background: #fff;
}

.context-search i {
  font-size: 0.8rem;
  color: #9ca3af;
  margin-right: 6px;
}

.context-search:focus-within i {
  color: #5e64ff;
}

.context-search input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 0.8rem;
  color: #374151;
  width: 100%;
  padding: 0;
}

.context-search input::placeholder {
  color: #9ca3af;
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

.context-btn.primary {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: rgba(94, 100, 255, 0.3);
  color: #4c52ff;

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #5e64ff, #4c52ff);
    color: white;
    border-color: #4c52ff;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(94, 100, 255, 0.25);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;

    &:hover {
      background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
      color: #4c52ff;
      transform: none;
      box-shadow: none;
    }
  }
}

.context-btn.danger {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  border-color: rgba(239, 68, 68, 0.3);
  color: #dc2626;

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #dc2626, #b91c1c);
    color: white;
    border-color: #b91c1c;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(220, 38, 38, 0.25);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;

    &:hover {
      background: linear-gradient(135deg, #fef2f2, #fee2e2);
      color: #dc2626;
      transform: none;
      box-shadow: none;
    }
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
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  color: #9ca3af;
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

/* Modern Projects Main Card Styling */
.projects-main-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04),
  0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.projects-main-content {
  padding: 24px 28px;
}

/* Modern Workspace Selector Styling */
.workspace-selector-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04),
  0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.workspace-selector-content {
  padding: 20px 24px;
}

.workspace-label {
  color: #374151;
  font-size: 0.9rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  opacity: 0.7;
}

/* Modern Workspace Cards Styling */
.workspace-cards-container {
  width: 100%;
}

.workspace-cards-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.workspace-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.workspace-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 300px));
  gap: 12px;
}

/* Workspace card styles moved to WorkspaceSelectionCard component */

/* Add Workspace Button Styling */
.add-workspace-btn {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border: 1px solid rgba(94, 100, 255, 0.3);
  border-radius: 8px;
  color: #1a237e;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.1),
  0 1px 3px rgba(94, 100, 255, 0.05);


  &:hover {
    background: linear-gradient(135deg, #5e64ff, #4c52ff);
    color: white;
    border-color: #4c52ff;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(94, 100, 255, 0.3),
    0 2px 6px rgba(94, 100, 255, 0.2);

  }

  &:active {
    transform: translateY(0);
  }
}

/* Sandbox Workspace Button Styling */
.sandbox-workspace-btn {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: linear-gradient(135deg, #fff9e6, #fef3cd);
  border: 1px solid rgba(255, 193, 7, 0.3);
  border-radius: 8px;
  color: #856404;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(255, 193, 7, 0.1),
  0 1px 3px rgba(255, 193, 7, 0.05);


  &:hover {
    background: linear-gradient(135deg, #ffc107, #ffb300);
    color: white;
    border-color: #ffb300;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(255, 193, 7, 0.3),
    0 2px 6px rgba(255, 193, 7, 0.2);

  }

  &:active {
    transform: translateY(0);
  }
}

/* Remote Workspace Button Styling */
.remote-workspace-btn {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: linear-gradient(135deg, #e6fffa, #b2f5ea);
  border: 1px solid rgba(56, 178, 172, 0.3);
  border-radius: 8px;
  color: #234e52;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(56, 178, 172, 0.1),
  0 1px 3px rgba(56, 178, 172, 0.05);


  &:hover {
    background: linear-gradient(135deg, #38b2ac, #319795);
    color: white;
    border-color: #319795;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(56, 178, 172, 0.3),
    0 2px 6px rgba(56, 178, 172, 0.2);

  }

  &:active {
    transform: translateY(0);
  }
}



</style>
