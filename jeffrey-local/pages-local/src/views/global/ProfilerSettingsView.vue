<template>
  <div>
    <MainCard>
      <template #header>
        <!-- Header with tabs -->
        <div class="page-header">
        <div class="page-header-info">
          <i class="bi bi-gear page-header-icon"></i>
          <span class="page-header-title">Profiler Settings</span>
        </div>
        <div class="header-tabs">
          <button
            class="header-tab"
            :class="{ 'active': viewMode === 'configure' }"
            @click="viewMode = 'configure'">
            <i class="bi bi-gear-fill"></i>
            <span>Configure</span>
          </button>
          <button
            class="header-tab"
            :class="{ 'active': viewMode === 'view' }"
            @click="viewMode = 'view'">
            <i class="bi bi-eye-fill"></i>
            <span>View</span>
          </button>
        </div>
        </div>
      </template>

      <!-- VIEW MODE -->
        <div v-if="viewMode === 'view'">
          <ProfilerSettingsHierarchy/>
        </div>

        <!-- CONFIGURE MODE -->
        <div v-if="viewMode === 'configure'">
          <!-- Step 1: Command Configuration -->
          <div v-if="currentStep === 1">
            <!-- Tab Bar -->
            <div class="tab-bar">
              <button
                  :class="['tab-item', { 'tab-item--active': activeTab === 'manual' }]"
                  @click="activeTab = 'manual'"
              >
                Manual
              </button>
              <button
                  :class="['tab-item', { 'tab-item--active': activeTab === 'builder' }]"
                  @click="activeTab = 'builder'"
              >
                Visual Builder
              </button>
            </div>

            <!-- Tab Content -->
            <div class="tab-content">
              <ConfigureCommand
                  v-if="activeTab === 'manual'"
                  v-model="finalCommand"
                  @accept-command="proceedToApply"
              />
              <CommandBuilder
                  v-if="activeTab === 'builder'"
                  @cancel="activeTab = 'manual'"
                  @accept-command="acceptBuilderCommand"
              />
            </div>
          </div>

          <!-- Step 2: Application Scope -->
          <div v-if="currentStep === 2" class="scope-section">
            <!-- Final Command Display -->
            <CommandDisplay
                :command="finalCommand"
                label="Command to Apply"
            />

            <!-- Scope Options -->
            <div class="scope-options">
              <div class="scope-title">Application Scope</div>

              <div class="scope-option-cards">
                <div class="scope-option-card" :class="{ 'selected': applicationScope === 'global' }"
                     @click="applicationScope = 'global'">
                  <div class="scope-option-header">
                    <input type="radio" v-model="applicationScope" value="global"/>
                    <div class="scope-option-info">
                      <i class="bi bi-globe2"></i>
                      <div>
                        <h6 class="scope-option-name">Apply Globally</h6>
                        <p class="scope-option-desc">Apply to all workspaces and future projects</p>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="scope-option-card" :class="{ 'selected': applicationScope === 'workspaces' }"
                     @click="applicationScope = 'workspaces'">
                  <div class="scope-option-header">
                    <input type="radio" v-model="applicationScope" value="workspaces"/>
                    <div class="scope-option-info">
                      <i class="bi bi-folder-fill"></i>
                      <div>
                        <h6 class="scope-option-name">Apply to Selected Workspaces</h6>
                        <p class="scope-option-desc">Choose specific live workspaces to apply configuration</p>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="scope-option-card" :class="{ 'selected': applicationScope === 'projects' }"
                     @click="applicationScope = 'projects'">
                  <div class="scope-option-header">
                    <input type="radio" v-model="applicationScope" value="projects"/>
                    <div class="scope-option-info">
                      <i class="bi bi-diagram-3-fill"></i>
                      <div>
                        <h6 class="scope-option-name">Apply to Selected Projects</h6>
                        <p class="scope-option-desc">Choose specific projects from live workspaces</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Workspace Selection -->
            <div v-if="applicationScope === 'workspaces'" class="selection-section">
              <div class="scope-title">Select Live Workspaces</div>
              <div v-if="liveWorkspaces.length === 0" class="empty-selection">
                <i class="bi bi-info-circle"></i>
                <span>No live workspaces available. Only live workspaces can have profiler settings applied.</span>
              </div>
              <div v-else class="selection-grid">
                <ProfilerSelectionCard
                    v-for="workspace in liveWorkspaces"
                    :key="workspace.id"
                    :name="workspace.name"
                    icon="bi-folder-fill"
                    :selected="selectedWorkspaces.includes(workspace.id)"
                    selection-type="checkbox"
                    @select="toggleWorkspaceSelection(workspace.id)"
                />
              </div>
            </div>

            <!-- Project Selection -->
            <div v-if="applicationScope === 'projects'" class="selection-section">
              <!-- Step 1: Select Workspaces -->
              <div class="scope-title">Step 1: Select Live Workspaces</div>
              <div v-if="liveWorkspaces.length === 0" class="empty-selection">
                <i class="bi bi-info-circle"></i>
                <span>No live workspaces available. Only live workspaces can have profiler settings applied.</span>
              </div>
              <div v-else class="selection-grid">
                <ProfilerSelectionCard
                    v-for="workspace in liveWorkspaces"
                    :key="workspace.id"
                    :name="workspace.name"
                    icon="bi-folder-fill"
                    :selected="selectedWorkspaces.includes(workspace.id)"
                    selection-type="checkbox"
                    @select="toggleWorkspaceSelection(workspace.id)"
                />
              </div>

              <!-- Step 2: Select Projects -->
              <div v-if="selectedWorkspaces.length > 0" class="projects-section">
                <div class="scope-title">Step 2: Select Projects</div>

                <div v-if="isLoadingProjects" class="loading-msg">
                  <div class="spinner-border spinner-border-sm text-primary me-2" role="status">
                    <span class="visually-hidden">Loading...</span>
                  </div>
                  <span>Loading projects...</span>
                </div>

                <div v-else>
                  <div
                      v-for="wsId in selectedWorkspaces"
                      :key="wsId"
                      class="workspace-group"
                  >
                    <div class="workspace-group-header">
                      <div class="workspace-group-title">
                        <i class="bi bi-folder-fill"></i>
                        <span>{{ getWorkspaceName(wsId) }}</span>
                      </div>
                      <button
                          type="button"
                          class="btn-select-all"
                          @click="selectAllProjectsInWorkspace(wsId)"
                      >
                        <i :class="areAllProjectsSelectedInWorkspace(wsId) ? 'bi bi-check-square-fill' : 'bi bi-square'"></i>
                        {{ areAllProjectsSelectedInWorkspace(wsId) ? 'Deselect All' : 'Select All' }}
                      </button>
                    </div>

                    <div
                        v-if="!projectsByWorkspace.get(wsId) || projectsByWorkspace.get(wsId)?.length === 0"
                        class="empty-selection"
                    >
                      <i class="bi bi-info-circle"></i>
                      <span>No projects available in this workspace</span>
                    </div>

                    <div v-else class="selection-grid">
                      <ProfilerSelectionCard
                          v-for="project in projectsByWorkspace.get(wsId)"
                          :key="project.id"
                          :name="project.name"
                          icon="bi-diagram-3-fill"
                          :selected="isProjectSelected(wsId, project.id)"
                          selection-type="checkbox"
                          @select="toggleProjectSelection(wsId, project.id)"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Apply Actions -->
            <div class="apply-actions">
              <button type="button" class="settings-btn settings-btn-outline" @click="backToCommand">
                <i class="bi bi-arrow-left"></i>
                Back to Command
              </button>
              <button
                  type="button"
                  class="settings-btn settings-btn-primary"
                  @click="applyConfiguration"
                  :disabled="!canApplyConfiguration"
              >
                <i class="bi bi-check-circle-fill"></i>
                Apply Configuration
              </button>
            </div>
          </div>
        </div>
        <!-- END CONFIGURE MODE -->
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import ToastService from '@/services/ToastService';
import ConfigureCommand from '@/components/settings/ConfigureCommand.vue';
import CommandBuilder from '@/components/settings/CommandBuilder.vue';
import CommandDisplay from '@/components/settings/CommandDisplay.vue';
import ProfilerSelectionCard from '@/components/settings/ProfilerSelectionCard.vue';
import ProfilerSettingsHierarchy from '@/components/settings/ProfilerSettingsHierarchy.vue';
import MainCard from '@/components/MainCard.vue';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import type Workspace from '@/services/api/model/Workspace';
import GlobalProfilerClient from '@/services/api/GlobalProfilerClient';
import ProjectsClient from '@/services/api/ProjectsClient';

const workspaceClient = new WorkspaceClient();
const globalProfilerClient = new GlobalProfilerClient();
const projectsClient = new ProjectsClient();
import type Project from '@/services/api/model/Project';

// Mode management
const viewMode = ref<'view' | 'configure'>('configure');

// Step management (1: Configure Command, 2: Apply)
const currentStep = ref(1);

// Step 1: Command Configuration
const activeTab = ref<'manual' | 'builder'>('manual');
const finalCommand = ref('');

// Step 2: Application Scope
const applicationScope = ref<'global' | 'workspaces' | 'projects'>('global');
const selectedWorkspaces = ref<string[]>([]);
const workspaces = ref<Workspace[]>([]);

// Project selection
const selectedProjects = ref<Array<{ workspaceId: string, projectId: string }>>([]);
const projectsByWorkspace = ref<Map<string, Project[]>>(new Map());
const isLoadingProjects = ref(false);

// Navigation
const proceedToApply = () => {
  currentStep.value = 2;
};

const acceptBuilderCommand = (command: string) => {
  finalCommand.value = command;
  activeTab.value = 'manual';
  ToastService.success('Command Accepted', 'Builder configuration has been converted to command.');
};

const backToCommand = () => {
  currentStep.value = 1;
};

// Workspace management
const liveWorkspaces = computed(() => workspaces.value);

const toggleWorkspaceSelection = (workspaceId: string) => {
  const index = selectedWorkspaces.value.indexOf(workspaceId);
  if (index === -1) {
    selectedWorkspaces.value.push(workspaceId);
  } else {
    selectedWorkspaces.value.splice(index, 1);
  }
};

// Project management
const loadProjectsForWorkspaces = async () => {
  if (selectedWorkspaces.value.length === 0) {
    projectsByWorkspace.value = new Map();
    return;
  }

  isLoadingProjects.value = true;
  try {
    const projectsMap = new Map<string, Project[]>();
    for (const workspaceId of selectedWorkspaces.value) {
      const projects = await projectsClient.list(workspaceId);
      projectsMap.set(workspaceId, projects);
    }
    projectsByWorkspace.value = projectsMap;
  } catch (error) {
    console.error('Failed to load projects:', error);
    ToastService.error('Failed to load projects', 'Cannot load projects from the server.');
  } finally {
    isLoadingProjects.value = false;
  }
};

const toggleProjectSelection = (workspaceId: string, projectId: string) => {
  const index = selectedProjects.value.findIndex(
      p => p.workspaceId === workspaceId && p.projectId === projectId
  );
  if (index === -1) {
    selectedProjects.value.push({workspaceId, projectId});
  } else {
    selectedProjects.value.splice(index, 1);
  }
};

const isProjectSelected = (workspaceId: string, projectId: string): boolean => {
  return selectedProjects.value.some(
      p => p.workspaceId === workspaceId && p.projectId === projectId
  );
};

const selectAllProjectsInWorkspace = (workspaceId: string) => {
  const projects = projectsByWorkspace.value.get(workspaceId) || [];
  const allSelected = projects.every(project => isProjectSelected(workspaceId, project.id));

  if (allSelected) {
    selectedProjects.value = selectedProjects.value.filter(p => p.workspaceId !== workspaceId);
  } else {
    projects.forEach(project => {
      if (!isProjectSelected(workspaceId, project.id)) {
        selectedProjects.value.push({workspaceId, projectId: project.id});
      }
    });
  }
};

const areAllProjectsSelectedInWorkspace = (workspaceId: string): boolean => {
  const projects = projectsByWorkspace.value.get(workspaceId) || [];
  if (projects.length === 0) return false;
  return projects.every(project => isProjectSelected(workspaceId, project.id));
};

const getWorkspaceName = (workspaceId: string): string => {
  const workspace = workspaces.value.find(w => w.id === workspaceId);
  return workspace?.name || 'Unknown Workspace';
};

// Watch for workspace selection changes in 'projects' mode
watch([selectedWorkspaces, applicationScope], async ([newWorkspaces, newScope], [oldWorkspaces]) => {
  if (newScope === 'projects') {
    if (JSON.stringify(newWorkspaces) !== JSON.stringify(oldWorkspaces)) {
      selectedProjects.value = [];
    }
    await loadProjectsForWorkspaces();
  }
}, {deep: true});

const canApplyConfiguration = computed(() => {
  if (applicationScope.value === 'global') return true;
  if (applicationScope.value === 'workspaces') return selectedWorkspaces.value.length > 0;
  if (applicationScope.value === 'projects') return selectedProjects.value.length > 0;
  return false;
});

// Apply configuration
const applyConfiguration = async () => {
  try {
    if (applicationScope.value === 'global') {
      await globalProfilerClient.upsert(null, null, finalCommand.value);
      ToastService.success('Configuration Applied', 'Profiler configuration has been applied globally.');
    } else if (applicationScope.value === 'workspaces') {
      const promises = selectedWorkspaces.value.map(workspaceId =>
          globalProfilerClient.upsert(workspaceId, null, finalCommand.value)
      );
      await Promise.all(promises);
      ToastService.success('Configuration Applied', `Profiler configuration has been applied to ${selectedWorkspaces.value.length} workspace(s).`);
    } else if (applicationScope.value === 'projects') {
      const promises = selectedProjects.value.map(({workspaceId, projectId}) =>
          globalProfilerClient.upsert(workspaceId, projectId, finalCommand.value)
      );
      await Promise.all(promises);
      ToastService.success('Configuration Applied', `Profiler configuration has been applied to ${selectedProjects.value.length} project(s).`);
    }

    currentStep.value = 1;
    applicationScope.value = 'global';
    selectedWorkspaces.value = [];
    selectedProjects.value = [];
    projectsByWorkspace.value = new Map();
  } catch (error) {
    console.error('Failed to apply configuration:', error);
    ToastService.error('Application Failed', 'Failed to apply profiler configuration. Please try again.');
  }
};

// Load workspaces
const loadWorkspaces = async () => {
  try {
    workspaces.value = await workspaceClient.list();
  } catch (error) {
    console.error('Failed to load workspaces:', error);
    ToastService.error('Failed to load workspaces', 'Cannot load workspaces from the server.');
  }
};

onMounted(() => {
  loadWorkspaces();
});
</script>

<style scoped>
@import '@/styles/shared-components.css';

/* Header Tabs */
.header-tabs {
  display: flex;
  gap: 4px;
  background: rgba(255, 255, 255, 0.6);
  padding: 3px;
  border-radius: var(--radius-base);
  border: 1px solid var(--color-border);
}

.header-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.header-tab i {
  font-size: 0.75rem;
}

.header-tab:hover:not(.active) {
  background: var(--color-bg-hover);
  color: var(--color-text);
}

.header-tab.active {
  background: var(--color-primary);
  color: var(--color-white);
  box-shadow: 0 2px 6px rgba(94, 100, 255, 0.25);
}

.header-tab.active i {
  color: var(--color-white);
}

/* Tab Bar */
.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--color-border);
}

.tab-item {
  padding: 10px 20px;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-muted);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tab-item:hover {
  color: var(--color-text);
}

.tab-item--active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
}

.tab-content {
  padding-top: 0;
  min-width: 0;
  overflow-x: hidden;
}

/* Buttons */
.settings-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  border: none;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.settings-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.settings-btn-primary {
  background: var(--color-primary);
  color: var(--color-white);
}

.settings-btn-primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.settings-btn-outline {
  background: var(--color-white);
  color: var(--color-text);
  border: 1px solid var(--color-border);
}

.settings-btn-outline:hover:not(:disabled) {
  background: var(--color-bg-hover);
  border-color: var(--color-border-input);
}

/* Scope Section */
.scope-section {
  margin-top: 0;
}

.scope-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-muted);
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  display: flex;
  align-items: center;
  gap: 8px;
}

.scope-title::before {
  content: '';
  width: 3px;
  height: 14px;
  background: var(--color-primary);
  border-radius: 2px;
}

.scope-option-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.scope-option-card {
  background: var(--color-light);
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 16px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.scope-option-card:hover {
  border-color: rgba(94, 100, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
}

.scope-option-card.selected {
  background: var(--color-primary-lighter);
  border-color: var(--color-primary);
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.2);
}

.scope-option-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.scope-option-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 1;
}

.scope-option-info i {
  font-size: 1.2rem;
  color: var(--color-primary);
  margin-top: 2px;
}

.scope-option-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin: 0 0 4px 0;
}

.scope-option-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin: 0;
  line-height: 1.4;
}

/* Selection grids */
.selection-section {
  margin-top: 20px;
}

.selection-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.empty-selection {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--color-primary-lighter);
  border: 1px dashed rgba(94, 100, 255, 0.2);
  border-radius: var(--radius-base);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.empty-selection i {
  color: var(--color-primary);
  opacity: 0.5;
}

/* Projects section */
.projects-section {
  margin-top: 24px;
}

.loading-msg {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--color-primary-lighter);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

/* Workspace group */
.workspace-group {
  margin-bottom: 24px;
}

.workspace-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--color-primary-lighter);
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: var(--radius-base);
  margin-bottom: 10px;
}

.workspace-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.workspace-group-title i {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
}

.btn-select-all {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--color-primary-light);
  border: 1px solid rgba(94, 100, 255, 0.3);
  border-radius: var(--radius-base);
  color: var(--color-primary);
  font-size: 0.75rem;
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn-select-all:hover {
  background: rgba(94, 100, 255, 0.2);
  border-color: rgba(94, 100, 255, 0.4);
}

/* Apply Actions */
.apply-actions {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

/* Responsive */
@media (max-width: 1600px) {
  .selection-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1200px) {
  .selection-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-tabs {
    width: 100%;
    justify-content: center;
  }

  .scope-option-cards {
    grid-template-columns: 1fr;
  }

  .selection-grid {
    grid-template-columns: 1fr;
  }

  .apply-actions {
    flex-wrap: wrap;
  }

  .workspace-group-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .btn-select-all {
    width: 100%;
    justify-content: center;
  }
}
</style>
