<template>
  <div class="w-100 bg-light">
    <!-- Header -->
    <div class="bg-white border-bottom py-4 mb-4 shadow-sm">
      <div class="container-fluid">
        <div class="d-flex justify-content-between align-items-center">
          <div class="d-flex align-items-center">
            <img src="/jeffrey_small.png" alt="Jeffrey Logo" height="60" class="me-3 header-logo">
            <div>
              <h1 class="mb-0 text-primary fw-bold">Jeffrey</h1>
              <p class="text-muted mb-0">JDK Flight Recorder Analysis Tool</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Projects List -->
    <div class="container-fluid">
      <div class="card mb-4">
        <div class="card-header">
          <div class="d-flex justify-content-between align-items-center">
            <h5 class="card-title mb-0">Projects</h5>
            <button class="btn btn-sm btn-primary" @click="showCreateProjectModal = true">
              <i class="bi bi-plus-lg me-1"></i>New Project
            </button>
          </div>
        </div>
        <div class="card-body">
          <div class="search-box mb-3">
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
            <div v-for="project in filteredProjects" :key="project.id" class="col-12 col-md-6 col-lg-4 col-xl-3">
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
  </div>

  <!-- Create Project Modal -->
  <div class="modal fade" id="createProjectModal" tabindex="-1"
       :class="{ 'show': showCreateProjectModal }"
       :style="{ display: showCreateProjectModal ? 'block' : 'none' }">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Create a New Project</h5>
          <button type="button" class="btn-close" @click="showCreateProjectModal = false"></button>
        </div>
        <div class="modal-body">
          <div class="mb-3">
            <label for="projectName" class="form-label">Project Name</label>
            <input
                type="text"
                class="form-control"
                id="projectName"
                v-model="newProjectName"
                @keyup.enter="createProject"
                placeholder="Enter project name"
                ref="projectNameInput"
            >
            <div v-if="errorMessage" class="alert alert-danger mt-2">
              {{ errorMessage }}
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="showCreateProjectModal = false">
            Cancel
          </button>
          <button type="button" class="btn btn-primary" @click="createProject" :disabled="creatingProject">
            <span v-if="creatingProject" class="spinner-border spinner-border-sm me-2" role="status"></span>
            Create
          </button>
        </div>
      </div>
    </div>
  </div>
  <div class="modal-backdrop fade show" v-if="showCreateProjectModal"></div>

  <!-- Toast for success message -->
  <div class="position-fixed bottom-0 end-0 p-3" style="z-index: 11">
    <div id="successToast" class="toast align-items-center text-white bg-success border-0"
         role="alert" aria-live="assertive" aria-atomic="true">
      <div class="d-flex">
        <div class="toast-body">
          {{ toastMessage }}
        </div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto"
                data-bs-dismiss="toast" aria-label="Close"></button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import ProjectCard from '@/components/ProjectCard.vue';
import ToastService from '@/services/ToastService';
import ProjectsClient from "@/services/ProjectsClient.ts";
import Project from "@/services/model/Project.ts";

// State
const projects = ref<Project[]>([]);
const filteredProjects = ref<Project[]>([]);
const searchQuery = ref('');
const showCreateProjectModal = ref(false);
const newProjectName = ref('');
const errorMessage = ref('');
const toastMessage = ref('Operation successful!');
const loading = ref(true);
const creatingProject = ref(false);
const projectNameInput = ref<HTMLInputElement | null>(null);

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
    toastMessage.value = 'Failed to load projects';
    showToast();
  } finally {
    loading.value = false;
  }
};

// Fetch projects on component mount
onMounted(() => {
  refreshProjects();
});

// Focus input field when modal opens
watch(showCreateProjectModal, (newValue) => {
  if (newValue) {
    // Use nextTick to ensure DOM is updated before focusing
    setTimeout(() => {
      projectNameInput.value?.focus();
    }, 100);
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
    errorMessage.value = 'Project name cannot be empty';
    return;
  }

  errorMessage.value = '';
  creatingProject.value = true;

  try {
    await ProjectsClient.create(newProjectName.value);

    // Refresh project list to include the new project
    await refreshProjects();

    // Reset and close modal
    newProjectName.value = '';
    showCreateProjectModal.value = false;

    // Show success toast
    toastMessage.value = 'Project created successfully!';
    showToast();
  } catch (error) {
    console.error('Failed to create project:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Failed to create project';
  } finally {
    creatingProject.value = false;
  }
};

const showToast = () => {
  ToastService.show('successToast', toastMessage.value);
};
</script>

<style scoped>
.modal {
  background-color: rgba(0, 0, 0, 0.5);
}

.header-logo {
  border-radius: 8px;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.1);
  transition: transform 0.3s ease;
}

.header-logo:hover {
  transform: scale(1.05);
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

.phoenix-search {
  border: 1px solid #e0e5eb;
  border-radius: 0.375rem;
  overflow: hidden;

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
</style>
