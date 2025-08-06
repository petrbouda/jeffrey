<template>
  <div>
    <!-- Workspace Selector -->
    <div class="workspace-selector-card mb-4">
      <div class="workspace-selector-content">
        <div class="workspace-cards-container">
          <div class="workspace-cards-header">
            <span class="workspace-label">Workspaces</span>
            <button class="add-workspace-btn" @click="showAddWorkspaceModal = true">
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
            @click="showCreateProjectModal = true" 
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

  <!-- Add Workspace Modal -->
  <div class="modal fade" id="addWorkspaceModal" tabindex="-1"
       aria-labelledby="addWorkspaceModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered modal-lg">
      <div class="modal-content modern-modal-content shadow">
        <div class="modal-header modern-modal-header border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-plus-circle fs-4 me-2 text-primary"></i>
            <h5 class="modal-title mb-0 text-dark" id="addWorkspaceModalLabel">Add New Workspace</h5>
          </div>
          <button type="button" class="btn-close" @click="closeAddWorkspaceModal"></button>
        </div>
        <div class="modal-body pt-4 pb-0">
          <p class="text-muted mb-4">
            Create a new workspace to organize your projects by environment or team.
          </p>
          
          <div class="mb-4 row">
            <label for="workspaceName" class="col-sm-3 col-form-label fw-medium">Name</label>
            <div class="col-sm-9">
              <div class="input-group">
                <span class="input-group-text border-end-0"><i class="bi bi-grid-3x3-gap"></i></span>
                <input 
                  type="text" 
                  id="workspaceName" 
                  class="form-control border-start-0" 
                  v-model="newWorkspaceName"
                  @input="updateWorkspaceId"
                  @keyup.enter="addWorkspace"
                  placeholder="Enter workspace name"
                  autocomplete="off"
                />
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="workspaceId" class="col-sm-3 col-form-label fw-medium">Workspace ID</label>
            <div class="col-sm-9">
              <div class="input-group">
                <span class="input-group-text border-end-0"><i class="bi bi-hash"></i></span>
                <input 
                  type="text" 
                  id="workspaceId" 
                  class="form-control border-start-0" 
                  v-model="newWorkspaceId"
                  @input="validateWorkspaceId"
                  @keyup.enter="addWorkspace"
                  placeholder="ID to reference this workspace"
                  autocomplete="off"
                />
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="workspaceDescription" class="col-sm-3 col-form-label fw-medium">Description</label>
            <div class="col-sm-9">
              <div class="input-group">
                <span class="input-group-text border-end-0"><i class="bi bi-file-text"></i></span>
                <input 
                  type="text" 
                  id="workspaceDescription" 
                  class="form-control border-start-0" 
                  v-model="newWorkspaceDescription"
                  placeholder="Enter workspace description"
                  autocomplete="off"
                />
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="workspacePath" class="col-sm-3 col-form-label fw-medium">Directory Path</label>
            <div class="col-sm-9">
              <div class="mb-2">
                <div class="form-check">
                  <input 
                    class="form-check-input" 
                    type="checkbox" 
                    id="customPathCheck" 
                    v-model="useCustomPath"
                  >
                  <label class="form-check-label small" for="customPathCheck">
                    Use <span style="color: #dc3545;">JEFFREY_HOME</span> as default directory
                  </label>
                </div>
              </div>
              <div class="input-group">
                <span class="input-group-text border-end-0"><i class="bi bi-folder"></i></span>
                <input 
                  type="text" 
                  id="workspacePath" 
                  class="form-control border-start-0" 
                  v-model="newWorkspacePath"
                  :disabled="useCustomPath"
                  :placeholder="useCustomPath ? 'Using JEFFREY_HOME as default' : 'Enter custom directory path'"
                  autocomplete="off"
                />
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-light" @click="closeAddWorkspaceModal">
            Cancel
          </button>
          <button type="button" class="btn btn-primary" @click="addWorkspace" :disabled="!newWorkspaceName.trim() || !newWorkspaceId.trim() || (!useCustomPath && !newWorkspacePath.trim())">
            <i class="bi bi-plus-lg me-1"></i> Add Workspace
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Create Project Modal -->
  <div class="modal fade" id="createProjectModal" tabindex="-1"
       aria-labelledby="createProjectModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered modal-lg">
      <div class="modal-content modern-modal-content shadow">
        <div class="modal-header modern-modal-header border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-folder-plus fs-4 me-2 text-primary"></i>
            <h5 class="modal-title mb-0 text-dark" id="createProjectModalLabel">Create a New Project</h5>
          </div>
          <button type="button" class="btn-close" @click="closeCreateProjectModal"></button>
        </div>
        <div class="modal-body pt-4 pb-0">
          <p class="text-muted mb-3">
            Enter a name for your new project and optionally select a project template to get started quickly.
          </p>
          
          <div class="mb-4 row">
            <label for="projectName" class="col-sm-3 col-form-label fw-medium">Project Name</label>
            <div class="col-sm-9">
              <div class="input-group">
                <span class="input-group-text border-end-0"><i class="bi bi-folder"></i></span>
                <input 
                  type="text" 
                  id="projectName" 
                  class="form-control border-start-0" 
                  v-model="newProjectName"
                  @keyup.enter="createProject"
                  placeholder="Enter project name"
                  ref="projectNameInput"
                  autocomplete="off"
                />
              </div>
            </div>
          </div>

          <div class="mb-4 row" v-if="projectTemplates.length > 0">
            <label class="col-sm-3 col-form-label fw-medium">Project Template</label>
            <div class="col-sm-9">
              <div class="d-flex flex-wrap gap-2">
                <div v-for="template in projectTemplates" :key="template.id" 
                    class="template-option p-2 rounded-3 border"
                    :class="{'selected': selectedTemplate === template.id}"
                    @click="selectTemplate(template.id)">
                  <div class="d-flex align-items-center">
                    <i class="bi bi-file-earmark-code text-primary me-2"></i>
                    <span>{{ template.name }}</span>
                  </div>
                </div>
              </div>
              <div class="text-muted small mt-2">
                <i class="bi bi-info-circle me-1"></i>Templates provide pre-configured settings
              </div>
            </div>
          </div>
          
          <div v-if="dialogProjectMessages.length > 0" class="alert alert-danger mx-3 mb-0 mt-3">
            <div v-for="(msg, idx) in dialogProjectMessages" :key="idx">
              <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ msg.content }}
            </div>
          </div>
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-light" @click="closeCreateProjectModal">
            Cancel
          </button>
          <button type="button" class="btn btn-primary" @click="createProject" :disabled="creatingProject">
            <span v-if="creatingProject" class="spinner-border spinner-border-sm me-2" role="status"></span>
            <i class="bi bi-save me-1"></i> Create Project
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {nextTick, onMounted, ref, watch} from 'vue';
import ProjectCard from '@/components/ProjectCard.vue';
import ToastService from '@/services/ToastService';
import ProjectsClient from "@/services/ProjectsClient.ts";
import Project from "@/services/model/Project.ts";
import ProjectTemplateInfo from "@/services/project/model/ProjectTemplateInfo.ts";
import TemplateTarget from "@/services/model/TemplateTarget.ts";
import WorkspaceClient from "@/services/workspace/WorkspaceClient.ts";
import Workspace from "@/services/workspace/model/Workspace.ts";
import CreateWorkspaceRequest from "@/services/workspace/model/CreateWorkspaceRequest.ts";
import * as bootstrap from 'bootstrap';

// State for Workspaces
const workspaces = ref<Workspace[]>([]);
const loadingWorkspaces = ref(true);
const workspaceErrorMessage = ref('');

const selectedWorkspace = ref<string>('local');
const showAddWorkspaceModal = ref(false);
const newWorkspaceName = ref('');
const newWorkspaceDescription = ref('');
const newWorkspaceId = ref('');
const newWorkspacePath = ref('');
const useCustomPath = ref(true);

// State for Projects
const projects = ref<Project[]>([]);
const filteredProjects = ref<Project[]>([]);
const searchQuery = ref('');
const showCreateProjectModal = ref(false);
const newProjectName = ref('');
const errorMessage = ref('');
const loading = ref(true);
const creatingProject = ref(false);
const projectNameInput = ref<HTMLInputElement | null>(null);
const projectTemplates = ref<ProjectTemplateInfo[]>([]);
const selectedTemplate = ref<string | null>(null);
const dialogProjectMessages = ref<{severity: string, content: string}[]>([]);

// Modal references for bootstrap
let createProjectModalInstance: bootstrap.Modal | null = null;
let addWorkspaceModalInstance: bootstrap.Modal | null = null;

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

// Load project templates
const loadTemplates = async () => {
  try {
    projectTemplates.value = await ProjectsClient.templates(TemplateTarget.PROJECT);
  } catch (error) {
    console.error('Failed to load project templates:', error);
  }
};

// Select workspace
const selectWorkspace = (workspaceId: string) => {
  selectedWorkspace.value = workspaceId;
  // Refresh projects when workspace changes
  refreshProjects();
};

// Select template
const selectTemplate = (templateId: string) => {
  selectedTemplate.value = selectedTemplate.value === templateId ? null : templateId;
};

// Functions to close the modals
const closeCreateProjectModal = () => {
  if (createProjectModalInstance) {
    createProjectModalInstance.hide();
  }
  showCreateProjectModal.value = false;
  resetProjectForm();
};

const closeAddWorkspaceModal = () => {
  if (addWorkspaceModalInstance) {
    addWorkspaceModalInstance.hide();
  }
  showAddWorkspaceModal.value = false;
  newWorkspaceName.value = '';
  newWorkspaceDescription.value = '';
  newWorkspaceId.value = '';
  newWorkspacePath.value = '';
  useCustomPath.value = true;
};

// Add new workspace
const addWorkspace = async () => {
  if (!newWorkspaceName.value.trim() || !newWorkspaceId.value.trim()) {
    return;
  }
  
  if (!useCustomPath.value && !newWorkspacePath.value.trim()) {
    return;
  }

  try {
    const request = new CreateWorkspaceRequest(
      newWorkspaceId.value.trim(),
      newWorkspaceName.value.trim(),
      newWorkspaceDescription.value.trim() || undefined,
      useCustomPath.value ? undefined : newWorkspacePath.value.trim() || undefined
    );

    const createdWorkspace = await WorkspaceClient.create(request);
    
    // Refresh workspaces to get updated list
    await refreshWorkspaces();
    
    selectedWorkspace.value = createdWorkspace.id;
    
    ToastService.success('Workspace Added!', `"${createdWorkspace.name}" workspace has been created`);
    
    closeAddWorkspaceModal();
    filterProjects();
  } catch (error) {
    console.error('Failed to create workspace:', error);
    ToastService.error('Failed to create workspace', error instanceof Error ? error.message : 'Could not create workspace on the server.');
  }
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

// Reset the form to default values
function resetProjectForm() {
  newProjectName.value = '';
  selectedTemplate.value = projectTemplates.value.length > 0 ? projectTemplates.value[0].id : null;
  dialogProjectMessages.value = [];
}

// Fetch projects on component mount
onMounted(() => {
  refreshWorkspaces();
  refreshProjects();
  loadTemplates();
  
  // Initialize Bootstrap modals after the DOM is ready
  nextTick(() => {
    const createProjectModalEl = document.getElementById('createProjectModal');
    if (createProjectModalEl) {
      createProjectModalEl.addEventListener('hidden.bs.modal', () => {
        showCreateProjectModal.value = false;
      });
      
      const closeButton = createProjectModalEl.querySelector('.btn-close');
      if (closeButton) {
        closeButton.addEventListener('click', closeCreateProjectModal);
      }
    }

    const addWorkspaceModalEl = document.getElementById('addWorkspaceModal');
    if (addWorkspaceModalEl) {
      addWorkspaceModalEl.addEventListener('hidden.bs.modal', () => {
        showAddWorkspaceModal.value = false;
      });
      
      const closeButton = addWorkspaceModalEl.querySelector('.btn-close');
      if (closeButton) {
        closeButton.addEventListener('click', closeAddWorkspaceModal);
      }
    }
  });
});

// Watch for changes to modal visibility flags
watch(showCreateProjectModal, (isVisible) => {
  if (isVisible) {
    if (!createProjectModalInstance) {
      const modalEl = document.getElementById('createProjectModal');
      if (modalEl) {
        createProjectModalInstance = new bootstrap.Modal(modalEl);
      }
    }
    
    if (createProjectModalInstance) {
      resetProjectForm();
      createProjectModalInstance.show();
      // Focus the input field after modal is shown
      setTimeout(() => {
        projectNameInput.value?.focus();
      }, 500);
    }
  } else {
    if (createProjectModalInstance) {
      createProjectModalInstance.hide();
    }
  }
});

watch(showAddWorkspaceModal, (isVisible) => {
  if (isVisible) {
    if (!addWorkspaceModalInstance) {
      const modalEl = document.getElementById('addWorkspaceModal');
      if (modalEl) {
        addWorkspaceModalInstance = new bootstrap.Modal(modalEl);
      }
    }
    
    if (addWorkspaceModalInstance) {
      newWorkspaceName.value = '';
      newWorkspaceDescription.value = '';
      newWorkspaceId.value = '';
      newWorkspacePath.value = '';
      useCustomPath.value = true;
      addWorkspaceModalInstance.show();
    }
  } else {
    if (addWorkspaceModalInstance) {
      addWorkspaceModalInstance.hide();
    }
  }
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

// Auto-generate workspace ID from name
const updateWorkspaceId = () => {
  if (newWorkspaceName.value) {
    const generated = newWorkspaceName.value
      .toLowerCase()
      .replace(/[^a-z0-9\s-]/g, '') // Remove non-alphanumeric characters except spaces and dashes
      .replace(/\s+/g, '-') // Replace spaces with dashes
      .replace(/-+/g, '-') // Replace multiple consecutive dashes with single dash
      .replace(/^-|-$/g, ''); // Remove leading/trailing dashes
    newWorkspaceId.value = generated;
  } else {
    // Clear workspace ID when name is empty
    newWorkspaceId.value = '';
  }
};

// Validate workspace ID input (only alphanumeric and dash allowed)
const validateWorkspaceId = () => {
  const cleaned = newWorkspaceId.value
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, '') // Remove anything that's not alphanumeric or dash
    .replace(/-+/g, '-') // Replace multiple consecutive dashes with single dash
    .replace(/^-|-$/g, ''); // Remove leading/trailing dashes
  newWorkspaceId.value = cleaned;
};

const createProject = async () => {
  if (!newProjectName.value || newProjectName.value.trim() === '') {
    dialogProjectMessages.value = [{severity: 'error', content: 'Project name cannot be empty'}];
    return;
  }

  dialogProjectMessages.value = [];
  creatingProject.value = true;

  try {
    // Pass the selected template ID if one is selected
    await ProjectsClient.create(newProjectName.value, selectedTemplate.value || undefined);

    // Refresh project list to include the new project
    await refreshProjects();

    // Show success toast
    ToastService.success('New Project Created!', `Project "${newProjectName.value}" successfully created`);
    
    // Reset form and close modal
    resetProjectForm();
    closeCreateProjectModal();
  } catch (error) {
    console.error('Failed to create project:', error);
    dialogProjectMessages.value = [{
      severity: 'error', 
      content: error instanceof Error ? error.message : 'Failed to create project'
    }];
  } finally {
    creatingProject.value = false;
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

.template-option {
  cursor: pointer;
  transition: all 0.2s ease;
  background-color: #f8f9fa;
  
  &:hover {
    background-color: #eef2ff;
    border-color: #d1d9ff !important;
  }
  
  &.selected {
    background-color: #eef2ff;
    border-color: #5e64ff !important;
    box-shadow: 0 0 0 1px rgba(94, 100, 255, 0.15);
  }
}

/* Modern Modal Buttons */
.btn-phoenix-primary {
  color: #5e64ff;
  background-color: #eaebff;
  border-color: transparent;
  font-weight: 500;

  &:hover {
    color: #fff;
    background-color: #5e64ff;
  }
}

.btn-light {
  background: linear-gradient(135deg, #f8f9fa, #e9ecef);
  border: 1px solid rgba(108, 117, 125, 0.2);
  color: #6c757d;
  font-weight: 500;
  border-radius: 10px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:hover {
    background: linear-gradient(135deg, #e9ecef, #dee2e6);
    color: #495057;
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
}

.btn-primary {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border: none;
  font-weight: 600;
  border-radius: 10px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4px 12px rgba(94, 100, 255, 0.3),
    0 2px 4px rgba(94, 100, 255, 0.2);
  
  &:hover {
    background: linear-gradient(135deg, #4c52ff, #3f46ff);
    transform: translateY(-2px);
    box-shadow: 
      0 6px 16px rgba(94, 100, 255, 0.4),
      0 3px 6px rgba(94, 100, 255, 0.3);
  }

  &:active {
    transform: translateY(-1px);
  }
}

/* Modal input styling */
.modal .input-group-text {
  background-color: #fff;
  color: #6c757d;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  border: 1px solid #ced4da;
}

.modal .form-control {
  border: 1px solid #ced4da;
  height: 38px;
}

.modal .form-control:focus {
  box-shadow: none;
  border-color: #ced4da;
}

.modal .input-group {
  flex-wrap: nowrap;
}

/* Modern Modal Styling */
.modern-modal-content {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 
    0 20px 40px rgba(0, 0, 0, 0.08),
    0 8px 24px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(10px);
}

.modern-modal-header {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.05), rgba(94, 100, 255, 0.08));
  border-radius: 16px 16px 0 0;
  padding: 20px 24px;
}

.bg-primary-soft {
  background-color: rgba(13, 110, 253, 0.15);
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
