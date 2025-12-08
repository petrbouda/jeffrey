<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <GenericModal
    :show="show"
    @update:show="emit('update:show', $event)"
    modal-id="secondary-profile-selection"
    title="Select Secondary Profile"
    icon="bi-layers"
    size="xl"
    :show-footer="true"
    class="profile-selection-modal"
  >
    <!-- Breadcrumb Navigation -->
    <div class="breadcrumb-nav mb-4">
      <div class="breadcrumb-item">
        <i class="bi bi-house-door me-1"></i>
        <span class="text-primary">Current</span>
      </div>
      <i class="bi bi-chevron-right mx-2 text-muted"></i>
      <div class="breadcrumb-item">
        <i class="workspace-icon me-1" :class="getWorkspaceIcon(currentWorkspace?.type)"></i>
        <span>{{ currentWorkspace?.name || 'Loading...' }}</span>
      </div>
      <i class="bi bi-chevron-right mx-2 text-muted"></i>
      <div class="breadcrumb-item">
        <i class="bi bi-folder me-1"></i>
        <span>{{ selectedProject?.name || currentProject?.name || 'Select Project' }}</span>
      </div>
      <template v-if="selectedProfile">
        <i class="bi bi-chevron-right mx-2 text-muted"></i>
        <div class="breadcrumb-item">
          <i class="bi bi-file-earmark-bar-graph me-1"></i>
          <span class="text-success">{{ selectedProfile.name }}</span>
        </div>
      </template>
    </div>

    <!-- Three-Panel Selection Interface -->
    <div class="selection-container">
      <!-- Panel 1: Workspaces -->
      <div class="selection-panel">
        <div class="panel-header">
          <h6 class="panel-title">
            <i class="bi bi-building me-2"></i>
            Workspaces
          </h6>
          <div v-if="loadingWorkspaces" class="spinner-border spinner-border-sm text-primary ms-auto" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>

        <div class="panel-content">
          <!-- Current Workspace (Contains Primary) -->
          <div class="workspace-card current-workspace"
               :class="{ 'selected': selectedWorkspace?.id === currentWorkspace?.id }"
               @click="selectWorkspace(currentWorkspace)">
            <div class="workspace-header">
              <i class="workspace-icon me-2" :class="getWorkspaceIcon(currentWorkspace?.type)"></i>
              <div class="workspace-info">
                <div class="workspace-name">{{ currentWorkspace?.name }}</div>
                <div class="workspace-meta">
                  <span>Current Workspace</span>
                  <span class="primary-indicator">• Contains Primary</span>
                </div>
              </div>
              <div class="selection-indicator" v-if="selectedWorkspace?.id === currentWorkspace?.id">
                <i class="bi bi-check-circle-fill text-primary"></i>
              </div>
            </div>
            <div class="workspace-stats">
              <span class="stat-item">
                <i class="bi bi-folder me-1"></i>
                {{ currentWorkspace?.projectCount || 0 }} projects
              </span>
            </div>
          </div>

          <!-- Other Available Workspaces -->
          <div v-for="workspace in otherWorkspaces"
               :key="workspace.id"
               class="workspace-card"
               :class="{ 'selected': selectedWorkspace?.id === workspace.id }"
               @click="selectWorkspace(workspace)">
            <div class="workspace-header">
              <i class="workspace-icon me-2" :class="getWorkspaceIcon(workspace.type)"></i>
              <div class="workspace-info">
                <div class="workspace-name">{{ workspace.name }}</div>
                <div class="workspace-meta">{{ formatWorkspaceType(workspace.type) }}</div>
              </div>
              <div class="selection-indicator" v-if="selectedWorkspace?.id === workspace.id">
                <i class="bi bi-check-circle-fill text-primary"></i>
              </div>
            </div>
            <div class="workspace-stats">
              <span class="stat-item">
                <i class="bi bi-folder me-1"></i>
                {{ workspace.projectCount }} projects
              </span>
            </div>
          </div>

          <!-- Empty State -->
          <div v-if="!loadingWorkspaces && allWorkspaces.length === 0" class="empty-state">
            <i class="bi bi-building text-muted mb-2"></i>
            <p class="text-muted mb-0">No workspaces available</p>
          </div>
        </div>
      </div>

      <!-- Panel 2: Projects -->
      <div class="selection-panel">
        <div class="panel-header">
          <h6 class="panel-title">
            <i class="bi bi-folder me-2"></i>
            Projects
          </h6>
          <div v-if="loadingProjects" class="spinner-border spinner-border-sm text-primary ms-auto" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>

        <div class="panel-content">
          <!-- Search Projects -->
          <div class="search-box mb-3" v-if="availableProjects.length > 5">
            <i class="bi bi-search search-icon"></i>
            <input
              type="text"
              class="form-control"
              placeholder="Search projects..."
              v-model="projectSearchQuery"
            />
          </div>

          <!-- Current Project (Contains Primary) -->
          <div v-if="currentProject && effectiveWorkspace?.id === currentWorkspaceId"
               class="project-card current-project"
               :class="{ 'selected': selectedProject?.id === currentProject.id }"
               @click="selectProject(currentProject)">
            <div class="project-header">
              <i class="bi bi-folder-fill me-2 text-primary"></i>
              <div class="project-info">
                <div class="project-name">{{ currentProject.name }}</div>
                <div class="project-meta">
                  <span>Current Project</span>
                  <span class="primary-indicator">• Contains Primary</span>
                </div>
              </div>
              <div class="selection-indicator" v-if="selectedProject?.id === currentProject.id">
                <i class="bi bi-check-circle-fill text-primary"></i>
              </div>
            </div>
          </div>

          <!-- Other Projects -->
          <div v-for="project in filteredProjects"
               :key="project.id"
               class="project-card"
               :class="{ 'selected': selectedProject?.id === project.id }"
               @click="selectProject(project)">
            <div class="project-header">
              <i class="bi bi-folder me-2"></i>
              <div class="project-info">
                <div class="project-name">{{ project.name }}</div>
                <div class="project-meta">{{ FormattingService.formatDate(project.createdAt) }}</div>
              </div>
              <div class="selection-indicator" v-if="selectedProject?.id === project.id">
                <i class="bi bi-check-circle-fill text-primary"></i>
              </div>
            </div>
          </div>

          <!-- Projects Loading State -->
          <div v-if="loadingProjects" class="loading-state">
            <div v-for="i in 3" :key="i" class="skeleton-card"></div>
          </div>

          <!-- Empty State -->
          <div v-if="!loadingProjects && availableProjects.length === 0 && effectiveWorkspace" class="empty-state">
            <i class="bi bi-folder text-muted mb-2"></i>
            <p class="text-muted mb-0">No projects in this workspace</p>
          </div>

          <!-- No Workspace Selected -->
          <div v-if="!effectiveWorkspace" class="empty-state">
            <i class="bi bi-arrow-left text-muted mb-2"></i>
            <p class="text-muted mb-0">Select a workspace to view projects</p>
          </div>
        </div>
      </div>

      <!-- Panel 3: Profiles -->
      <div class="selection-panel">
        <div class="panel-header">
          <h6 class="panel-title">
            <i class="bi bi-file-earmark-bar-graph me-2"></i>
            Profiles
          </h6>
          <div v-if="loadingProfiles" class="spinner-border spinner-border-sm text-primary ms-auto" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>

        <div class="panel-content">
          <!-- Search Profiles -->
          <div class="search-box mb-3" v-if="availableProfiles.length > 5">
            <i class="bi bi-search search-icon"></i>
            <input
              type="text"
              class="form-control"
              placeholder="Search profiles..."
              v-model="profileSearchQuery"
            />
          </div>

          <!-- Profile Cards -->
          <div v-for="profile in filteredProfiles"
               :key="profile.id"
               class="profile-card"
               :class="{
                 'selected': selectedProfile?.id === profile.id,
                 'current-primary': profile.id === currentProfileId && selectedProject?.id === currentProjectId,
                 'disabled': profile.id === currentProfileId && selectedProject?.id === currentProjectId
               }"
               @click="selectProfile(profile)">
            <div class="profile-header">
              <i class="bi bi-file-earmark-bar-graph me-2"></i>
              <div class="profile-info">
                <div class="profile-name">{{ profile.name }}</div>
                <div class="profile-meta">
                  <span class="profile-date">{{ FormattingService.formatDate(profile.createdAt) }}</span>
                  <span class="profile-duration">{{ FormattingService.formatDurationInMillis2Units(profile.durationInMillis) }}</span>
                </div>
              </div>
              <div class="profile-indicators">
                <div v-if="profile.id === currentProfileId && selectedProject?.id === currentProjectId" class="indicator primary">
                  <i class="bi bi-star-fill"></i>
                  <span>Primary</span>
                </div>
                <div v-else-if="selectedProfile?.id === profile.id" class="indicator selected">
                  <i class="bi bi-check-circle-fill"></i>
                  <span>Selected</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Profiles Loading State -->
          <div v-if="loadingProfiles" class="loading-state">
            <div v-for="i in 3" :key="i" class="skeleton-card profile-skeleton"></div>
          </div>

          <!-- Empty State -->
          <div v-if="!loadingProfiles && availableProfiles.length === 0 && selectedProject" class="empty-state">
            <i class="bi bi-file-earmark-bar-graph text-muted mb-2"></i>
            <p class="text-muted mb-0">No profiles in this project</p>
          </div>

          <!-- No Project Selected -->
          <div v-if="!selectedProject" class="empty-state">
            <i class="bi bi-arrow-left text-muted mb-2"></i>
            <p class="text-muted mb-0">Select a project to view profiles</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Custom Footer -->
    <template #footer>
      <div class="d-flex justify-content-between align-items-center w-100">
        <div class="selection-summary">
          <span v-if="selectedProfile" class="text-muted">
            Selected: <strong>{{ selectedProfile.name }}</strong>
            <span v-if="selectedProject?.id !== currentProjectId">
              from <strong>{{ selectedProject?.name }}</strong>
            </span>
          </span>
        </div>
        <div class="button-group">
          <button type="button" class="btn btn-outline-danger me-2" @click="clearSelection">
            <i class="bi bi-x-circle me-1"></i>
            Clear Selection
          </button>
          <button type="button" class="btn btn-secondary me-2" @click="closeModal">
            Cancel
          </button>
          <button
            type="button"
            class="btn btn-primary"
            @click="confirmSelection"
            :disabled="!hasValidSelection"
          >
            <i class="bi bi-check2 me-1"></i>
            Select Profile
          </button>
        </div>
      </div>
    </template>
  </GenericModal>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import GenericModal from '@/components/GenericModal.vue';
import ProjectProfileClient from '@/services/ProjectProfileClient';
import ProjectsClient from '@/services/ProjectsClient';
import WorkspaceClient from '@/services/workspace/WorkspaceClient';
import FormattingService from '@/services/FormattingService';
import ToastService from '@/services/ToastService';
import Profile from '@/services/model/Profile';
import Project from '@/services/model/Project';
import Workspace from '@/services/workspace/model/Workspace';
import WorkspaceType from '@/services/workspace/model/WorkspaceType';

interface Props {
  show: boolean;
  currentProjectId: string;
  currentProfileId: string;
  currentSecondaryProfileId?: string;
  currentSecondaryProjectId?: string;
  workspaceId: string;
}

interface Emits {
  'update:show': [value: boolean];
  'profile-selected': [profile: Profile, projectId: string];
  'profile-cleared': [];
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Local state
const allWorkspaces = ref<Workspace[]>([]);
const currentWorkspace = ref<Workspace | null>(null);
const selectedWorkspace = ref<Workspace | null>(null);
const currentProject = ref<Project | null>(null);
const selectedProject = ref<Project | null>(null);
const availableProjects = ref<Project[]>([]);
const availableProfiles = ref<Profile[]>([]);
const selectedProfile = ref<Profile | null>(null);

// Loading states
const loadingWorkspaces = ref(false);
const loadingProjects = ref(false);
const loadingProfiles = ref(false);

// Search queries
const projectSearchQuery = ref('');
const profileSearchQuery = ref('');

// Computed properties
const currentWorkspaceId = computed(() => props.workspaceId);
const currentProjectId = computed(() => props.currentProjectId);

const otherWorkspaces = computed(() =>
  allWorkspaces.value.filter(w => w.id !== currentWorkspaceId.value)
);

const effectiveWorkspace = computed(() =>
  selectedWorkspace.value || currentWorkspace.value
);

const filteredProjects = computed(() => {
  if (!projectSearchQuery.value) {
    return availableProjects.value.filter(p =>
      !(currentProject.value && p.id === currentProject.value.id)
    );
  }
  return availableProjects.value
    .filter(p => !(currentProject.value && p.id === currentProject.value.id))
    .filter(p => p.name.toLowerCase().includes(projectSearchQuery.value.toLowerCase()));
});

const filteredProfiles = computed(() => {
  if (!profileSearchQuery.value) {
    return availableProfiles.value;
  }
  return availableProfiles.value.filter(p =>
    p.name.toLowerCase().includes(profileSearchQuery.value.toLowerCase())
  );
});

const hasValidSelection = computed(() => {
  return selectedProfile.value !== null &&
    !(selectedProfile.value.id === props.currentProfileId &&
      selectedProject.value?.id === props.currentProjectId);
});

// Helper functions
const getWorkspaceIcon = (type?: WorkspaceType) => {
  switch (type) {
    case WorkspaceType.LIVE: return 'bi-house-door-fill text-primary';
    case WorkspaceType.REMOTE: return 'bi-cloud-fill text-info';
    case WorkspaceType.SANDBOX: return 'bi-box-fill text-warning';
    default: return 'bi-building text-secondary';
  }
};

const formatWorkspaceType = (type: WorkspaceType) => {
  switch (type) {
    case WorkspaceType.LIVE: return 'Live Workspace';
    case WorkspaceType.REMOTE: return 'Remote Workspace';
    case WorkspaceType.SANDBOX: return 'Sandbox Environment';
    default: return 'Unknown';
  }
};


// Initialize when modal opens
watch(() => props.show, (newShow) => {
  if (newShow) {
    initializeModal();
  }
}, { immediate: true });

// Initialize current selections
watch([() => props.currentSecondaryProfileId, () => props.currentSecondaryProjectId], () => {
  if (props.currentSecondaryProfileId && props.currentSecondaryProjectId) {
    // Will be handled in initializeModal
  }
}, { immediate: true });

const initializeModal = async () => {
  await loadWorkspaces();
  await loadCurrentProject();

  // Set initial workspace selection (current workspace)
  selectedWorkspace.value = currentWorkspace.value;

  // Load projects for current workspace
  if (effectiveWorkspace.value) {
    await loadProjectsForWorkspace(effectiveWorkspace.value.id);
  }

  // Handle existing secondary profile selection
  if (props.currentSecondaryProjectId && props.currentSecondaryProfileId) {
    // Find and select the secondary project
    const secondaryProject = availableProjects.value.find(p => p.id === props.currentSecondaryProjectId);
    if (secondaryProject) {
      await selectProject(secondaryProject);
      // Find and select the secondary profile
      const secondaryProfile = availableProfiles.value.find(p => p.id === props.currentSecondaryProfileId);
      if (secondaryProfile) {
        selectedProfile.value = secondaryProfile;
      }
    }
  } else {
    // Default to current project
    if (currentProject.value) {
      await selectProject(currentProject.value);
    }
  }
};

const loadWorkspaces = async () => {
  loadingWorkspaces.value = true;
  try {
    allWorkspaces.value = await WorkspaceClient.list();
    currentWorkspace.value = allWorkspaces.value.find(w => w.id === currentWorkspaceId.value) || null;
  } catch (error) {
    console.error('Failed to load workspaces:', error);
    ToastService.error('Failed to load workspaces', 'Error occurred while loading workspaces');
  } finally {
    loadingWorkspaces.value = false;
  }
};

const loadCurrentProject = async () => {
  try {
    const projects = await ProjectsClient.list(currentWorkspaceId.value);
    currentProject.value = projects.find(p => p.id === currentProjectId.value) || null;
  } catch (error) {
    console.error('Failed to load current project:', error);
  }
};

const loadProjectsForWorkspace = async (workspaceId: string) => {
  loadingProjects.value = true;
  try {
    availableProjects.value = await ProjectsClient.list(workspaceId);
  } catch (error) {
    console.error('Failed to load projects:', error);
    ToastService.error('Failed to load projects', 'Error occurred while loading projects');
    availableProjects.value = [];
  } finally {
    loadingProjects.value = false;
  }
};

const loadProfilesForProject = async (workspaceId: string, projectId: string) => {
  loadingProfiles.value = true;
  try {
    const projectProfileClient = new ProjectProfileClient(workspaceId, projectId);
    availableProfiles.value = await projectProfileClient.list();
  } catch (error) {
    console.error('Failed to load profiles:', error);
    ToastService.error('Failed to load profiles', 'Error occurred while loading profiles');
    availableProfiles.value = [];
  } finally {
    loadingProfiles.value = false;
  }
};

// Selection handlers
const selectWorkspace = async (workspace: Workspace | null) => {
  if (!workspace) return;

  selectedWorkspace.value = workspace;
  selectedProject.value = null;
  selectedProfile.value = null;
  availableProfiles.value = [];

  await loadProjectsForWorkspace(workspace.id);
};

const selectProject = async (project: Project) => {
  selectedProject.value = project;
  selectedProfile.value = null;

  const workspaceId = effectiveWorkspace.value?.id || currentWorkspaceId.value;
  await loadProfilesForProject(workspaceId, project.id);
};

const selectProfile = (profile: Profile) => {
  // Don't allow selecting the primary profile as the secondary profile
  if (profile.id === props.currentProfileId && selectedProject.value?.id === props.currentProjectId) {
    ToastService.error("Selection failed", "Cannot select primary profile as secondary profile");
    return;
  }

  selectedProfile.value = profile;
};

const confirmSelection = () => {
  if (selectedProfile.value && selectedProject.value && hasValidSelection.value) {
    emit('profile-selected', selectedProfile.value, selectedProject.value.id);
    closeModal();
  }
};

const clearSelection = () => {
  selectedProfile.value = null;
  emit('profile-cleared');
  closeModal();
};

const closeModal = () => {
  emit('update:show', false);
};
</script>

<style scoped>
/* Breadcrumb Navigation */
.breadcrumb-nav {
  display: flex;
  align-items: center;
  padding: 1rem;
  background: linear-gradient(135deg, #f8f9fa, #e9ecef);
  border-radius: 8px;
  border-left: 4px solid #5e64ff;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  font-weight: 500;
  color: #495057;
}

/* Modal Customization */
:deep(.profile-selection-modal .modal-dialog) {
  max-width: 95vw;
  width: 95vw;
  height: 90vh;
  max-height: 90vh;
  margin: 2.5vh auto;
}

:deep(.profile-selection-modal .modal-content) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.profile-selection-modal .modal-body) {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
}

:deep(.profile-selection-modal .modal-footer) {
  flex-shrink: 0;
  padding: 1rem 1.5rem;
  border-top: 1px solid #dee2e6;
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* Selection Container */
.selection-container {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 1rem;
  flex: 1;
  min-height: 0; /* Important for flexbox children */
}

/* Panel Styles */
.selection-panel {
  display: flex;
  flex-direction: column;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.panel-header {
  display: flex;
  align-items: center;
  padding: 1rem;
  background: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
}

.panel-title {
  margin: 0;
  font-weight: 600;
  color: #495057;
  font-size: 0.95rem;
}

.panel-content {
  flex: 1;
  padding: 1rem;
  overflow-y: auto;
  min-height: 0; /* Important for flex children to allow scrolling */
}

.panel-content::-webkit-scrollbar {
  width: 6px;
}

.panel-content::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.panel-content::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* Card Styles */
.workspace-card,
.project-card,
.profile-card {
  padding: 0.75rem;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  margin-bottom: 0.5rem;
  cursor: pointer;
  transition: all 0.2s ease;
  background: #fff;
}

.workspace-card:hover,
.project-card:hover,
.profile-card:hover {
  border-color: #5e64ff;
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.1);
  transform: translateY(-1px);
}

.workspace-card.current-workspace {
  background: linear-gradient(135deg, #fff9e6, #fef3cd);
  border-color: #ffc107;
  border-left: 3px solid #ffc107;
}

.workspace-card.current-workspace.selected {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
  border-left: 3px solid #5e64ff;
}

.project-card.current-project {
  background: linear-gradient(135deg, #fff9e6, #fef3cd);
  border-color: #ffc107;
  border-left: 3px solid #ffc107;
}

.project-card.current-project.selected {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
  border-left: 3px solid #5e64ff;
}

.workspace-card.selected,
.project-card.selected,
.profile-card.selected {
  border-color: #5e64ff;
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
}

.profile-card.current-primary {
  background: linear-gradient(135deg, #fff3cd, #fef3cd);
  border-color: #ffc107;
  cursor: not-allowed;
}

.profile-card.disabled {
  opacity: 0.6;
}

/* Card Headers */
.workspace-header,
.project-header,
.profile-header {
  display: flex;
  align-items: center;
  margin-bottom: 0.5rem;
}

.workspace-info,
.project-info,
.profile-info {
  flex: 1;
  min-width: 0;
}

.workspace-name,
.project-name,
.profile-name {
  font-weight: 600;
  color: #212529;
  font-size: 0.9rem;
  margin-bottom: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.workspace-meta,
.project-meta,
.profile-meta {
  font-size: 0.75rem;
  color: #6c757d;
  display: flex;
  gap: 0.5rem;
}

/* Selection Indicators */
.selection-indicator {
  margin-left: auto;
  color: #5e64ff;
}

.profile-indicators {
  display: flex;
  align-items: center;
  margin-left: auto;
}

.indicator {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  font-size: 0.7rem;
  font-weight: 600;
}

.indicator.primary {
  background: #fff3cd;
  color: #856404;
}

.indicator.selected {
  background: #d1ecf1;
  color: #0c5460;
}

/* Workspace Stats */
.workspace-stats {
  display: flex;
  gap: 1rem;
  margin-top: 0.5rem;
}

.stat-item {
  font-size: 0.75rem;
  color: #6c757d;
  display: flex;
  align-items: center;
}

/* Search Box */
.search-box {
  position: relative;
}

.search-icon {
  position: absolute;
  left: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: #6c757d;
  z-index: 1;
}

.search-box input {
  padding-left: 2.5rem;
  font-size: 0.85rem;
  border-radius: 6px;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  text-align: center;
}

.empty-state i {
  font-size: 2rem;
  margin-bottom: 0.5rem;
}

/* Loading States */
.loading-state {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.skeleton-card {
  height: 60px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 6px;
}

.profile-skeleton {
  height: 80px;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

/* Selection Summary */
.selection-summary {
  font-size: 0.9rem;
}

.button-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.button-group .btn {
  display: flex;
  align-items: center;
  font-weight: 500;
  transition: all 0.2s ease;
}

.button-group .btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.button-group .btn-primary {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border-color: #5e64ff;
  box-shadow: 0 2px 4px rgba(94, 100, 255, 0.2);
}

.button-group .btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4c52ff, #3d47ff);
  border-color: #4c52ff;
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(94, 100, 255, 0.3);
}

/* Workspace Icons */
.workspace-icon {
  font-size: 1rem;
}

/* Primary Indicator */
.primary-indicator {
  color: #856404;
  font-weight: 600;
  font-size: 0.7rem;
}

/* Responsive Design */
@media (max-width: 1200px) {
  :deep(.profile-selection-modal .modal-dialog) {
    max-width: 98vw;
    width: 98vw;
  }

  .selection-container {
    gap: 0.75rem;
  }
}

@media (max-width: 992px) {
  :deep(.profile-selection-modal .modal-dialog) {
    height: 95vh;
    max-height: 95vh;
    margin: 1vh auto;
  }

  .selection-container {
    grid-template-columns: 1fr 1fr;
    gap: 0.5rem;
  }
}

@media (max-width: 768px) {
  :deep(.profile-selection-modal .modal-dialog) {
    max-width: 100vw;
    width: 100vw;
    height: 100vh;
    max-height: 100vh;
    margin: 0;
    border-radius: 0;
  }

  :deep(.profile-selection-modal .modal-content) {
    border-radius: 0;
    border: none;
  }

  .selection-container {
    grid-template-columns: 1fr;
    gap: 0.5rem;
  }

  .breadcrumb-nav {
    flex-wrap: wrap;
    gap: 0.5rem;
    padding: 0.75rem;
  }

  .breadcrumb-item {
    font-size: 0.8rem;
  }

  .panel-header {
    padding: 0.75rem;
  }

  .panel-content {
    padding: 0.75rem;
  }
}
</style>