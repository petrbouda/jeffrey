<template>
  <div>
    <!-- Workspace Selector -->
    <div class="workspace-selector-card mb-4">
      <div class="workspace-selector-content">
        <div class="workspace-cards-container">
          <div class="workspace-cards-header">
            <span class="workspace-label">Workspaces</span>
            <button class="add-workspace-btn" @click="addWorkspaceModal?.showModal()">
              <span class="add-workspace-plus">+</span>
              Add Workspace
            </button>
          </div>
          <div class="workspace-cards-grid">
            <div 
              v-for="workspace in workspaces"
              :key="workspace.id"
              class="workspace-card"
              :class="{ 'active': selectedWorkspace === workspace.id, 'local': workspace.id === 'local' }"
              @click="selectWorkspace(workspace.id)"
            >
              <div class="workspace-card-content">
                <div class="workspace-card-header">
                  <h6 class="workspace-name">{{ workspace.name }}</h6>
                  <span class="workspace-badge">{{ getWorkspaceProjectCount(workspace.id) }}</span>
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
          <button 
            class="new-project-btn" 
            @click="createProjectModal?.showModal()" 
            :disabled="selectedWorkspace !== 'local'"
            :title="selectedWorkspace !== 'local' ? 'New project can be created only in Local Workspace' : ''"
          >
            <span class="new-project-plus">+</span>
            New Project
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

        <!-- Projects grid -->
        <div v-else-if="filteredProjects.length > 0" class="row g-4">
          <div v-for="project in filteredProjects" :key="project.id" class="col-12 col-md-6 col-lg-4">
            <ProjectCard :project="project"/>
          </div>
        </div>

        <!-- Empty state -->
        <div v-else class="text-center py-5">
          <i class="bi bi-folder-plus fs-1 text-muted mb-3"></i>
          <h5>No projects found</h5>
          <p v-if="selectedWorkspace === 'local'" class="text-muted">Click the "New Project" button to create your first project</p>
          <p v-else class="text-muted">Projects in this workspace will be generated automatically</p>
        </div>
      </div>
    </div>
  </div>

  <!-- Modal Components -->
  <AddWorkspaceModal
    ref="addWorkspaceModal"
    @workspace-created="handleWorkspaceCreated"
    @modal-closed="handleAddWorkspaceModalClosed"
  />

  <CreateProjectModal
    ref="createProjectModal"
    :selected-workspace="selectedWorkspace"
    @project-created="handleProjectCreated"
    @modal-closed="handleCreateProjectModalClosed"
  />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import ProjectCard from '@/components/ProjectCard.vue';
import AddWorkspaceModal from '@/components/projects/AddWorkspaceModal.vue';
import CreateProjectModal from '@/components/projects/CreateProjectModal.vue';
import ToastService from '@/services/ToastService';
import ProjectsClient from "@/services/ProjectsClient.ts";
import Project from "@/services/model/Project.ts";
import WorkspaceClient from "@/services/workspace/WorkspaceClient.ts";
import Workspace from "@/services/workspace/model/Workspace.ts";

// State for Workspaces
const workspaces = ref<Workspace[]>([]);
const loadingWorkspaces = ref(true);
const workspaceErrorMessage = ref('');

const selectedWorkspace = ref<string>('local');

// State for Projects
const projects = ref<Project[]>([]);
const filteredProjects = ref<Project[]>([]);
const searchQuery = ref('');
const errorMessage = ref('');
const loading = ref(true);

// Modal component references
const addWorkspaceModal = ref<InstanceType<typeof AddWorkspaceModal>>();
const createProjectModal = ref<InstanceType<typeof CreateProjectModal>>();

// Create LOCAL workspace object
const createLocalWorkspace = (): Workspace => ({
  id: 'local',
  name: 'LOCAL',
  description: 'Local development projects',
  path: undefined,
  enabled: true,
  createdAt: new Date().toISOString(),
  projectCount: 0 // Will be updated with actual count
});

// Fetch workspaces function
const refreshWorkspaces = async () => {
  loadingWorkspaces.value = true;
  workspaceErrorMessage.value = '';

  try {
    const serverWorkspaces = await WorkspaceClient.list();
    
    // Get LOCAL workspace project count
    const localProjects = await ProjectsClient.list(null);
    const localWorkspace = createLocalWorkspace();
    localWorkspace.projectCount = localProjects.length;
    
    // Always add LOCAL workspace as the default, regardless of server workspaces
    workspaces.value = [...serverWorkspaces, localWorkspace];
  } catch (error) {
    console.error('Failed to load workspaces:', error);
    workspaceErrorMessage.value = error instanceof Error ? error.message : 'Could not connect to server';
    ToastService.error('Failed to load workspaces', 'Cannot load workspaces from the server. Using default workspaces.');
    // Fallback to default workspaces - still include LOCAL
    workspaces.value = [createLocalWorkspace()];
  } finally {
    loadingWorkspaces.value = false;
  }
};

// Fetch projects function
const refreshProjects = async () => {
  loading.value = true;
  errorMessage.value = '';

  try {
    // Pass workspace ID to the API, or null for local workspace
    const workspaceId = selectedWorkspace.value === 'local' ? null : selectedWorkspace.value;
    projects.value = await ProjectsClient.list(workspaceId);
    filteredProjects.value = [...projects.value];
  } catch (error) {
    console.error('Failed to load projects:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Could not connect to server';
    ToastService.error('Failed to load projects', 'Cannot load projects from the server. Please try again later.');
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

// Handle add workspace modal closed
const handleAddWorkspaceModalClosed = () => {
  // Nothing specific needed here
};

// Get project count for workspace
const getWorkspaceProjectCount = (workspaceId: string) => {
  const workspace = workspaces.value.find(w => w.id === workspaceId);
  return workspace?.projectCount ?? '?';
};

// Get workspace description
const getWorkspaceDescription = (workspace: Workspace) => {
  if (workspace?.description) {
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

// Handle create project modal closed
const handleCreateProjectModalClosed = () => {
  // Nothing specific needed here
};

// Fetch projects on component mount
onMounted(() => {
  refreshWorkspaces();
  refreshProjects();
});


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
  box-shadow: 
    inset 0 1px 3px rgba(0, 0, 0, 0.05),
    0 1px 2px rgba(0, 0, 0, 0.02);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:focus-within {
    border-color: rgba(94, 100, 255, 0.3);
    box-shadow: 
      inset 0 1px 3px rgba(0, 0, 0, 0.05),
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
  box-shadow: 
    0 4px 12px rgba(94, 100, 255, 0.3),
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
    box-shadow: 
      0 6px 16px rgba(94, 100, 255, 0.4),
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





/* Modern Projects Main Card Styling */
.projects-main-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 
    0 4px 20px rgba(0, 0, 0, 0.04),
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
  box-shadow: 
    0 4px 20px rgba(0, 0, 0, 0.04),
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

.workspace-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 300px));
  gap: 12px;
}

.workspace-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.02);

  &:hover:not(.active) {
    transform: translateY(-2px);
    box-shadow: 
      0 6px 16px rgba(0, 0, 0, 0.06),
      0 2px 8px rgba(94, 100, 255, 0.1);
    border-color: rgba(94, 100, 255, 0.2);
  }

  &.active {
    background: linear-gradient(135deg, #5e64ff, #4c52ff);
    border-color: #5e64ff;
    transform: translateY(-1px);
    box-shadow: 
      0 6px 20px rgba(94, 100, 255, 0.3),
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
  }

  &.local {
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
      box-shadow: 
        0 6px 16px rgba(255, 193, 7, 0.15),
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
}

.workspace-card-content {
  width: 100%;
}

.workspace-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.workspace-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
  letter-spacing: 0.01em;
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
  gap: 8px;
  padding: 8px 14px;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border: none;
  border-radius: 8px;
  color: white;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
  box-shadow: 
    0 2px 8px rgba(94, 100, 255, 0.2),
    0 1px 3px rgba(94, 100, 255, 0.15);

  .add-workspace-plus {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    background: rgba(255, 255, 255, 0.2);
    color: white;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 700;
    line-height: 1;
  }

  &:hover {
    background: linear-gradient(135deg, #4c52ff, #3f46ff);
    transform: translateY(-1px);
    box-shadow: 
      0 4px 12px rgba(94, 100, 255, 0.3),
      0 2px 6px rgba(94, 100, 255, 0.2);

    .add-workspace-plus {
      background: rgba(255, 255, 255, 0.3);
    }
  }

  &:active {
    transform: translateY(0);
  }
}
</style>
