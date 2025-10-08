<template>
  <div>
    <!-- Workspace Selector (only show in root workspace selection mode) -->
    <div v-if="!isWorkspaceScoped" class="workspace-selector-card mb-4">
      <div class="workspace-selector-content">
        <div class="workspace-cards-container">
          <div class="workspace-cards-header">
            <span class="workspace-label">Workspaces</span>
            <div class="workspace-actions">
              <button class="add-workspace-btn" @click="localWorkspaceModal?.showModal()">
                Add Local
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
            <div
                v-for="workspace in workspaces"
                :key="workspace.id"
                class="workspace-card"
                :class="{
                'active': selectedWorkspace === workspace.id, 
                'sandbox': workspace.type === WorkspaceType.SANDBOX,
                'remote': workspace.type === WorkspaceType.REMOTE,
                'local': workspace.type === WorkspaceType.LOCAL,
                'unavailable': workspace.status !== WorkspaceStatus.AVAILABLE
              }"
                @click="handleWorkspaceClick(workspace.id)">
              <div class="workspace-card-content">
                <div class="workspace-card-header">
                  <div class="workspace-name-container">
                    <i v-if="workspace.type === WorkspaceType.REMOTE" class="bi bi-display workspace-remote-icon"
                       title="Remote Workspace"></i>
                    <i v-else-if="workspace.type === WorkspaceType.SANDBOX" class="bi bi-house workspace-sandbox-icon"
                       title="Sandbox Workspace"></i>
                    <i v-else class="bi bi-folder workspace-local-icon" title="Local Workspace"></i>
                    <h6 class="workspace-name">{{ workspace.name }}</h6>
                  </div>
                  <Badge
                      v-if="workspace.status === WorkspaceStatus.UNAVAILABLE"
                      :value="'UNAVAILABLE'"
                      variant="red"
                      size="s"
                  />
                  <Badge
                      v-else-if="workspace.status === WorkspaceStatus.OFFLINE"
                      :value="'OFFLINE'"
                      variant="red"
                      size="s"
                  />
                  <Badge
                      v-else-if="workspace.status === WorkspaceStatus.UNKNOWN"
                      :value="'UNKNOWN'"
                      variant="yellow"
                      size="s"
                  />
                  <span v-else-if="workspace.status === WorkspaceStatus.AVAILABLE"
                        class="workspace-badge">{{ getWorkspaceProjectCount(workspace.id) }}</span>
                </div>
                <div class="workspace-card-description">
                  {{ getWorkspaceDescription(workspace) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="projects-main-card mb-4">
      <div class="projects-main-content">
        <!-- Workspace Header (different for scoped vs selection mode) -->
        <div v-if="isWorkspaceScoped || getSelectedWorkspace()" class="workspace-header mb-4">
          <div class="workspace-header-main">
            <div class="workspace-header-info">
              <div class="workspace-header-title">
                <i v-if="getSelectedWorkspace()?.type === WorkspaceType.REMOTE"
                   class="bi bi-display workspace-header-icon" title="Remote Workspace"></i>
                <i v-else-if="getSelectedWorkspace()?.type === WorkspaceType.SANDBOX"
                   class="bi bi-house workspace-header-icon" title="Sandbox Workspace"></i>
                <i v-else class="bi bi-folder workspace-header-icon" title="Local Workspace"></i>
                <h4 class="workspace-header-name">{{ getSelectedWorkspace()?.name }}</h4>
                <Badge
                    v-if="getSelectedWorkspace()?.status === WorkspaceStatus.UNAVAILABLE"
                    :value="'UNAVAILABLE'"
                    variant="red"
                    size="s"
                />
                <Badge
                    v-else-if="getSelectedWorkspace()?.status === WorkspaceStatus.OFFLINE"
                    :value="'OFFLINE'"
                    variant="red"
                    size="s"
                />
                <Badge
                    v-else-if="getSelectedWorkspace()?.status === WorkspaceStatus.UNKNOWN"
                    :value="'UNKNOWN'"
                    variant="yellow"
                    size="s"
                />
                <Badge
                    v-else
                    :value="'AVAILABLE'"
                    variant="green"
                    size="s"
                />
              </div>
              <div class="workspace-header-details">
                <span class="workspace-header-description">{{ getWorkspaceDescription(getSelectedWorkspace()) }}</span>
                <span class="workspace-header-divider">•</span>
                <span class="workspace-header-projects">{{ getProjectCountText() }}</span>
                <span v-if="getSelectedWorkspace()?.type === WorkspaceType.REMOTE"
                      class="workspace-header-divider">•</span>
                <span v-if="getSelectedWorkspace()?.type === WorkspaceType.REMOTE" class="workspace-header-sync">Remote workspace</span>
              </div>
            </div>
            <div class="workspace-header-actions">
              <button
                  class="new-project-header-btn"
                  @click="createProjectModal?.showModal()"
                  :disabled="!canCreateProjectInWorkspace(selectedWorkspace)"
                  :title="getCreateProjectTooltip(selectedWorkspace)"
              >
                <i class="bi bi-plus-lg"></i>
                New Project
              </button>
              <button
                  class="delete-workspace-btn"
                  @click="handleDeleteWorkspace()"
                  :disabled="getSelectedWorkspace()?.status !== WorkspaceStatus.AVAILABLE"
                  :title="getDeleteTooltip()"
              >
                <i class="bi bi-trash"></i>
                Delete
              </button>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-else class="workspace-header-empty mb-4">
          <div class="workspace-header-empty-content">
            <i class="bi bi-folder"></i>
            <span>Select a workspace to view projects</span>
          </div>
        </div>

        <div class="d-flex align-items-center mb-4 gap-3">
          <div class="search-box">
            <div class="input-group input-group-sm phoenix-search">
              <span class="input-group-text border-0 ps-3 pe-0 search-icon-container">
                <i class="bi bi-search text-primary"></i>
              </span>
              <input
                  type="text"
                  class="form-control border-0 py-2"
                  v-model="searchQuery"
                  placeholder="Search projects..."
                  @input="filterProjects"
              >
            </div>
          </div>
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

        <!-- Projects grid -->
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
        <div v-else class="text-center py-5">
          <i class="bi bi-folder-plus fs-1 text-muted mb-3"></i>
          <h5>No projects found</h5>
          <p v-if="getSelectedWorkspaceType() === WorkspaceType.SANDBOX" class="text-muted">Click the "New Project"
            button to create your first project</p>
          <p v-else-if="getSelectedWorkspaceType() === WorkspaceType.REMOTE" class="text-muted">Projects in this remote
            workspace are synchronized from external source</p>
          <p v-else class="text-muted">Projects in this workspace are managed by the server</p>
        </div>
      </div>
    </div>
  </div>

  <!-- Modal Components -->
  <localWorkspaceModal
      ref="localWorkspaceModal"
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
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import ProjectCard from '@/components/ProjectCard.vue';
import LocalWorkspaceModal from '@/components/projects/LocalWorkspaceModal.vue';
import RemoteWorkspaceModal from '@/components/projects/RemoteWorkspaceModal.vue';
import CreateProjectModal from '@/components/projects/CreateProjectModal.vue';
import Badge from '@/components/Badge.vue';
import ToastService from '@/services/ToastService';
import ProjectsClient from "@/services/ProjectsClient.ts";
import WorkspaceProjectsClient from "@/services/workspace/WorkspaceProjectsClient.ts";
import Project from "@/services/model/Project.ts";
import WorkspaceClient from "@/services/workspace/WorkspaceClient.ts";
import Workspace from "@/services/workspace/model/Workspace.ts";
import WorkspaceType from "@/services/workspace/model/WorkspaceType.ts";
import WorkspaceStatus from "@/services/workspace/model/WorkspaceStatus.ts";
import CreateWorkspaceRequest from "@/services/workspace/model/CreateWorkspaceRequest.ts";
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
const localWorkspaceModal = ref<InstanceType<typeof LocalWorkspaceModal>>();
const remoteWorkspaceModal = ref<InstanceType<typeof RemoteWorkspaceModal>>();
const createProjectModal = ref<InstanceType<typeof CreateProjectModal>>();

const getSelectedWorkspaceType = (): WorkspaceType | undefined => {
  const workspace = workspaces.value.find(w => w.id === selectedWorkspace.value);
  return workspace?.type;
};



// Fetch workspaces function
const refreshWorkspaces = async () => {
  try {
    // All workspaces come from the backend now
    workspaces.value = await WorkspaceClient.list();

    // Set selected workspace: first available workspace
    if (!selectedWorkspace.value && workspaces.value.length > 0) {
      // Find first available workspace
      const availableWorkspace = workspaces.value.find(w => w.status === WorkspaceStatus.AVAILABLE);

      if (availableWorkspace) {
        selectedWorkspace.value = availableWorkspace.id;
      } else {
        // If no workspace is available, just select the first one
        selectedWorkspace.value = workspaces.value[0].id;
      }
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

      if (workspace.status !== WorkspaceStatus.AVAILABLE) {
        // Don't load projects from unavailable workspace
        projects.value = [];
        filteredProjects.value = [];
        let statusMessage = 'Cannot load projects.';
        if (workspace.status === WorkspaceStatus.UNAVAILABLE) {
          statusMessage = 'Workspace is unavailable. It may have been deleted from the server.';
        } else if (workspace.status === WorkspaceStatus.OFFLINE) {
          statusMessage = 'Server is offline. Cannot load projects.';
        } else if (workspace.status === WorkspaceStatus.UNKNOWN) {
          statusMessage = 'Workspace status is unknown. Cannot load projects.';
        }
        errorMessage.value = statusMessage;
        return;
      }

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
  // Don't allow selection of unavailable workspaces
  const workspace = workspaces.value.find(w => w.id === workspaceId);
  if (workspace && workspace.status !== WorkspaceStatus.AVAILABLE) {
    let title = 'Workspace Unavailable';
    let message = ' Cannot connect to this workspace.';

    if (workspace.status === WorkspaceStatus.UNAVAILABLE) {
      title = 'Workspace Not Found';
      message = 'This workspace may have been deleted from the server.';
    } else if (workspace.status === WorkspaceStatus.OFFLINE) {
      title = 'Server Offline';
      message = 'Cannot connect to the server. Please check your connection.';
    } else if (workspace.status === WorkspaceStatus.UNKNOWN) {
      title = 'Unknown Status';
      message = 'Workspace status is unclear.';
    }

    ToastService.error(title, message);
    return;
  }

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

// Get delete button tooltip
const getDeleteTooltip = (): string => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return '';

  if (workspace.status !== WorkspaceStatus.AVAILABLE) {
    return 'Cannot delete unavailable workspace';
  }

  if (workspace.type === WorkspaceType.SANDBOX) {
    return 'Delete sandbox workspace and all its projects';
  }

  if (workspace.type === WorkspaceType.REMOTE) {
    return 'Remove remote workspace (does not affect remote source)';
  }

  return 'Delete server workspace';
};

// Handle workspace deletion
const handleDeleteWorkspace = async () => {
  const workspace = getSelectedWorkspace();
  if (!workspace) return;

  const projectCount = projects.value.length;
  let confirmMessage = '';

  if (workspace.type === WorkspaceType.SANDBOX) {
    confirmMessage = `Delete sandbox workspace "${workspace.name}"?\n\nThis will permanently delete ${projectCount > 0 ? `${projectCount} project${projectCount > 1 ? 's' : ''}` : 'the workspace'} and cannot be undone.`;
  } else if (workspace.type === WorkspaceType.REMOTE) {
    confirmMessage = `Remove remote workspace "${workspace.name}"?\n\nThis will remove the local copy but won't affect the remote source.`;
  } else {
    confirmMessage = `Delete
    server workspace "${workspace.name}"?\n\nThis will permanently remove the workspace from the server.`;
  }

  if (!confirm(confirmMessage)) {
    return;
  }

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
.search-box {
  flex: 1;
  max-width: 600px;
}

.phoenix-search {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.12);
  overflow: hidden;
  border-radius: 12px;
  height: 48px;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.05),
  0 1px 2px rgba(0, 0, 0, 0.02);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:focus-within {
    border-color: rgba(94, 100, 255, 0.3);
    box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.05),
    0 4px 12px rgba(94, 100, 255, 0.1),
    0 0 0 3px rgba(94, 100, 255, 0.05);
    transform: translateY(-1px);
  }

  .search-icon-container {
    width: 48px;
    display: flex;
    justify-content: center;
    background: transparent;
    border: none;
    color: #6c757d;
  }

  .form-control {
    height: 46px;
    font-size: 0.9rem;
    background: transparent;
    border: none;
    color: #374151;
    font-weight: 500;

    &::placeholder {
      color: #9ca3af;
      font-weight: 400;
    }

    &:focus {
      box-shadow: none;
      background: transparent;
    }
  }
}

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

/* Workspace Header Styling */
.workspace-header {
  background: linear-gradient(135deg, #f8fafb, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 24px;
}

.workspace-header-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.workspace-header-info {
  flex: 1;
  min-width: 0;
}

.workspace-header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.workspace-header-icon {
  font-size: 1.1rem;
  color: #5e64ff;
  opacity: 0.8;
}

.workspace-header-name {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
  letter-spacing: -0.02em;
}

.workspace-header-details {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 0.9rem;
  color: #6b7280;
}

.workspace-header-description {
  color: #374151;
  font-weight: 500;
}

.workspace-header-divider {
  color: #d1d5db;
  font-weight: bold;
}

.workspace-header-projects {
  color: #5e64ff;
  font-weight: 600;
}

.workspace-header-sync {
  color: #059669;
  font-weight: 500;
}

.workspace-header-actions {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.new-project-header-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border: 1px solid rgba(94, 100, 255, 0.3);
  border-radius: 8px;
  color: #1a237e;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #5e64ff, #4c52ff);
    color: white;
    border-color: #4c52ff;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(94, 100, 255, 0.3);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;

    &:hover {
      background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
      color: #1a237e;
      transform: none;
      box-shadow: none;
    }
  }

  i {
    font-size: 0.8rem;
  }
}

.delete-workspace-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 8px;
  color: #dc2626;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #dc2626, #b91c1c);
    color: white;
    border-color: #b91c1c;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(220, 38, 38, 0.3);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;

    &:hover {
      background: linear-gradient(135deg, #fef2f2, #fee2e2);
      color: #dc2626;
      transform: none;
      box-shadow: none;
    }
  }

  i {
    font-size: 0.8rem;
  }
}

.workspace-header-empty {
  background: linear-gradient(135deg, #f9fafb, #ffffff);
  border: 2px dashed rgba(156, 163, 175, 0.3);
  border-radius: 12px;
  padding: 32px 24px;
  text-align: center;
}

.workspace-header-empty-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #9ca3af;
  font-size: 1rem;
  font-weight: 500;

  i {
    font-size: 1.5rem;
  }
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .workspace-header-main {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }

  .workspace-header-title {
    flex-wrap: wrap;
  }

  .workspace-header-name {
    font-size: 1.25rem;
  }

  .workspace-header-details {
    font-size: 0.85rem;
  }

  .workspace-header-actions {
    align-self: flex-end;
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

.workspace-card {
  &.local {
    background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
    border: 1px solid rgba(94, 100, 255, 0.3);

    .workspace-name {
      color: #1a237e;
    }

    .workspace-card-description {
      color: #283593;
    }

    .workspace-badge {
      background: linear-gradient(135deg, #5e64ff, #4c52ff);
      color: white;
    }

    .workspace-local-icon {
      color: #5e64ff;
    }

    &:hover:not(.active) {
      background: linear-gradient(135deg, #e8eaf6, #c5cae9);
      border-color: rgba(94, 100, 255, 0.4);
      box-shadow: 0 6px 16px rgba(94, 100, 255, 0.15),
      0 2px 8px rgba(94, 100, 255, 0.1);
      transform: translateY(-2px);
    }

    &.active {
      background: linear-gradient(135deg, #5e64ff, #4c52ff);
      border-color: #4c52ff;
      transform: translateY(-1px);
      box-shadow: 0 6px 20px rgba(94, 100, 255, 0.3),
      0 2px 8px rgba(94, 100, 255, 0.2);

      .workspace-name {
        color: white;
      }

      .workspace-card-description {
        color: rgba(255, 255, 255, 0.8);
      }

      .workspace-badge {
        background: rgba(255, 255, 255, 0.2);
        color: white;
      }

      .workspace-local-icon {
        color: white;
      }
    }
  }

  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  position: relative; /* Ensure proper containing block for absolute positioned badge */
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04),
  0 1px 2px rgba(0, 0, 0, 0.02);

  &:hover:not(.active) {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06),
    0 2px 8px rgba(94, 100, 255, 0.1);
    border-color: rgba(94, 100, 255, 0.2);
  }

  &.active {
    background: linear-gradient(135deg, #5e64ff, #4c52ff);
    border-color: #5e64ff;
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(94, 100, 255, 0.3),
    0 2px 8px rgba(94, 100, 255, 0.2);

    .workspace-name {
      color: white;
    }

    .workspace-card-description {
      color: rgba(255, 255, 255, 0.8);
    }

    .workspace-badge {
      background: rgba(255, 255, 255, 0.2);
      color: white;
    }

    .workspace-local-icon {
      color: white;
    }
  }

  &.sandbox {
    &:not(.active) {
      background: linear-gradient(135deg, #fff9e6, #fef3cd);
      border-color: rgba(255, 193, 7, 0.3);

      .workspace-name {
        color: #856404;
      }

      .workspace-card-description {
        color: #a67c00;
      }

      .workspace-badge {
        background: linear-gradient(135deg, #ffc107, #ffb300);
        color: #212529;
      }
    }

    &:hover:not(.active) {
      background: linear-gradient(135deg, #fff3cd, #ffeaa7);
      border-color: rgba(255, 193, 7, 0.4);
      box-shadow: 0 6px 16px rgba(255, 193, 7, 0.15),
      0 2px 8px rgba(255, 193, 7, 0.1);
    }

    &.active {
      background: linear-gradient(135deg, #ffc107, #ffb300);
      border-color: #ffb300;

      .workspace-name {
        color: #212529;
      }

      .workspace-card-description {
        color: rgba(33, 37, 41, 0.8);
      }

      .workspace-badge {
        background: rgba(33, 37, 41, 0.15);
        color: #212529;
      }
    }
  }

  &.remote {
    &:not(.active) {
      background: linear-gradient(135deg, #e6fffa, #b2f5ea);
      border-color: rgba(56, 178, 172, 0.3);

      .workspace-name {
        color: #234e52;
      }

      .workspace-card-description {
        color: #285e61;
      }

      .workspace-badge {
        background: linear-gradient(135deg, #38b2ac, #319795);
        color: white;
      }

      .workspace-remote-icon {
        color: #38b2ac;
      }
    }

    &:hover:not(.active) {
      background: linear-gradient(135deg, #b2f5ea, #81e6d9);
      border-color: rgba(56, 178, 172, 0.4);
      box-shadow: 0 6px 16px rgba(56, 178, 172, 0.15),
      0 2px 8px rgba(56, 178, 172, 0.1);
    }

    &.active {
      background: linear-gradient(135deg, #38b2ac, #319795);
      border-color: #319795;

      .workspace-name {
        color: white;
      }

      .workspace-card-description {
        color: rgba(255, 255, 255, 0.8);
      }

      .workspace-badge {
        background: rgba(255, 255, 255, 0.2);
        color: white;
      }

      .workspace-remote-icon {
        color: white;
      }
    }
  }

  &.sandbox {
    &.active {
      .workspace-sandbox-icon {
        color: #212529;
      }
    }
  }

  &.remote {
    &.active {
      .workspace-remote-icon {
        color: white;
      }
    }
  }
}

.workspace-card-content {
  width: 100%;
  position: relative; /* Ensure badge is positioned relative to content */
}

.workspace-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.workspace-name-container {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workspace-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
  letter-spacing: 0.01em;
}

.workspace-remote-icon {
  font-size: 0.8rem;
  opacity: 0.8;
  transition: all 0.2s ease;
}

.workspace-sandbox-icon {
  font-size: 0.8rem;
  color: #856404;
  opacity: 0.8;
  transition: all 0.2s ease;
}

.workspace-local-icon {
  font-size: 0.8rem;
  color: #5e64ff;
  opacity: 0.8;
  transition: all 0.2s ease;
}

.workspace-card-description {
  font-size: 0.75rem;
  color: #6b7280;
  line-height: 1.4;
  margin: 0;
}

.workspace-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 6px;
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;

}

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

/* Workspace card unavailable state */
.workspace-card.unavailable {
  opacity: 0.7;
}
</style>
