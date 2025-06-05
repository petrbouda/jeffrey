<template>
  <div>
    <div class="card mb-4">
      <div class="card-body">
        <div class="d-flex justify-content-between align-items-center mb-4">
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
          <button class="btn btn-primary new-project-btn" @click="showCreateProjectModal = true">
            <i class="bi bi-plus-lg me-1"></i>New Project
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
          <p class="text-muted">Click the "New Project" button to create your first project</p>
        </div>
      </div>
    </div>
  </div>

  <!-- Create Project Modal -->
  <div class="modal fade" id="createProjectModal" tabindex="-1"
       aria-labelledby="createProjectModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered modal-lg">
      <div class="modal-content rounded-1 shadow">
        <div class="modal-header bg-primary-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-folder-plus fs-4 me-2 text-primary"></i>
            <h5 class="modal-title mb-0 text-dark" id="createProjectModalLabel">Create a New Project</h5>
          </div>
          <button type="button" class="btn-close" @click="closeCreateProjectModal"></button>
        </div>
        <div class="modal-body pt-4">
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
import {onMounted, ref, watch, nextTick} from 'vue';
import ProjectCard from '@/components/ProjectCard.vue';
import ToastService from '@/services/ToastService';
import ProjectsClient from "@/services/ProjectsClient.ts";
import Project from "@/services/model/Project.ts";
import ProjectTemplateInfo from "@/services/project/model/ProjectTemplateInfo.ts";
import * as bootstrap from 'bootstrap';

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

// Fetch projects function
const refreshProjects = async () => {
  loading.value = true;
  errorMessage.value = '';

  try {
    const data = await ProjectsClient.list();
    projects.value = data;
    filteredProjects.value = [...projects.value];
  } catch (error) {
    console.error('Failed to load projects:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Could not connect to server';
    ToastService.error('Failed to load projects')
  } finally {
    loading.value = false;
  }
};

// Load project templates
const loadTemplates = async () => {
  try {
    projectTemplates.value = await ProjectsClient.templates();
  } catch (error) {
    console.error('Failed to load project templates:', error);
  }
};

// Select template
const selectTemplate = (templateId: string) => {
  selectedTemplate.value = selectedTemplate.value === templateId ? null : templateId;
};

// Functions to close the modal
const closeCreateProjectModal = () => {
  if (createProjectModalInstance) {
    createProjectModalInstance.hide();
  }
  showCreateProjectModal.value = false;
  resetProjectForm();
};

// Reset the form to default values
function resetProjectForm() {
  newProjectName.value = '';
  selectedTemplate.value = null;
  dialogProjectMessages.value = [];
}

// Fetch projects on component mount
onMounted(() => {
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

const filterProjects = () => {
  if (!searchQuery.value.trim()) {
    filteredProjects.value = [...projects.value];
    return;
  }

  const query = searchQuery.value.toLowerCase();
  filteredProjects.value = projects.value.filter(project =>
      project.name.toLowerCase().includes(query)
  );
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
  border: 1px solid #e0e5eb;
  overflow: hidden;
  border-radius: 6px;
  height: 42px;
  box-shadow: none;

  .search-icon-container {
    width: 40px;
    display: flex;
    justify-content: center;
    background-color: transparent;
  }

  .form-control {
    height: 40px;
    font-size: 0.9rem;

    &:focus {
      box-shadow: none;
    }
  }
}

.new-project-btn {
  padding: 0.55rem 1.2rem;
  font-size: 0.9rem;
  font-weight: 500;
  border-radius: 8px;
  white-space: nowrap;
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
  background-color: #f8f9fa;
  border-color: #e9ecef;
  
  &:hover {
    background-color: #e9ecef;
  }
}

.btn-primary {
  transition: all 0.2s ease;
  
  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
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

.bg-primary-soft {
  background-color: rgba(13, 110, 253, 0.15);
}
</style>
