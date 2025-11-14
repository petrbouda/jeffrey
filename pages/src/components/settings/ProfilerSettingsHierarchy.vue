<template>
  <div class="profiler-hierarchy">
    <!-- Loading State -->
    <div v-if="isLoading" class="loading-state">
      <div class="spinner-border spinner-border-sm text-primary me-2" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <span>Loading settings hierarchy...</span>
    </div>

    <div v-else>
      <!-- Active Command Display -->
      <div v-if="activeCommand" class="final-command-display">
        <label class="command-label">{{ activeCommandLabel }}</label>
        <div class="command-preview">
          <code>{{ activeCommand }}</code>
        </div>
      </div>

      <!-- Global Settings -->
      <div class="scope-options">
        <h5 class="scope-section-title">Global Settings</h5>
        <div class="scope-option-cards">
          <div
            class="scope-option-card"
            :class="{
              'selected': activeLevel === 'global'
            }"
            @click="setActiveCommand('global', globalSettings?.agentSettings || null)"
          >
            <div class="scope-option-header">
              <input
                type="radio"
                :checked="activeLevel === 'global'"
                @click.stop
              />
              <div class="scope-option-info">
                <i class="bi bi-globe2"></i>
                <div>
                  <h6 class="scope-option-title">Global Configuration</h6>
                  <p class="scope-option-description">
                    {{ globalSettings?.agentSettings ? 'Custom global settings configured' : 'No global settings configured' }}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Workspaces -->
      <div class="workspace-selection-section">
        <h5 class="workspace-section-title">Workspaces</h5>
        <div v-if="workspaces.length === 0" class="no-workspaces-message">
          <i class="bi bi-info-circle"></i>
          <span>No workspaces available</span>
        </div>
        <div v-else class="workspace-selection-grid">
          <div
            v-for="workspace in workspaces"
            :key="workspace.id"
            class="workspace-selection-card"
            :class="{
              'selected': selectedWorkspaceId === workspace.id
            }"
            @click="handleWorkspaceClick(workspace)"
          >
            <div class="workspace-selection-header">
              <input
                type="radio"
                :checked="selectedWorkspaceId === workspace.id"
                @click.stop
              />
              <div class="workspace-selection-info">
                <i class="bi bi-folder-fill"></i>
                <h6 class="workspace-selection-name">{{ workspace.name }}</h6>
              </div>
            </div>
            <div class="workspace-selection-description">
              <span v-if="workspace.hasCustomSettings" class="badge-custom">Custom Settings</span>
              <span v-else class="badge-uses-global">Uses Global</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Projects with Custom Settings -->
      <div v-if="selectedWorkspaceId" class="project-selection-section">
        <h5 class="workspace-section-title">Projects with Custom Settings in {{ selectedWorkspaceName }}</h5>

        <div v-if="isLoadingProjects" class="loading-projects-message">
          <div class="spinner-border spinner-border-sm text-primary me-2" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <span>Loading projects...</span>
        </div>

        <div v-else-if="projectsWithOverrides.length === 0" class="no-projects-message">
          <i class="bi bi-info-circle"></i>
          <span>No projects with custom settings in this workspace</span>
        </div>

        <div v-else class="project-selection-grid">
          <div
            v-for="project in projectsWithOverrides"
            :key="project.id"
            class="project-selection-card"
            :class="{
              'selected': activeLevel === 'project' && activeProjectId === project.id
            }"
            @click="setActiveCommand('project', project.agentSettings || null, selectedWorkspaceId, project.id, project.name)"
          >
            <div class="project-selection-header">
              <input
                type="radio"
                :checked="activeLevel === 'project' && activeProjectId === project.id"
                @click.stop
              />
              <div class="project-selection-info">
                <i class="bi bi-diagram-3-fill"></i>
                <h6 class="project-selection-name">{{ project.name }}</h6>
              </div>
            </div>
            <div class="project-selection-meta">
              <span class="project-meta-item">
                <i class="bi bi-person-vcard"></i>
                {{ project.profileCount }} {{ project.profileCount === 1 ? 'profile' : 'profiles' }}
              </span>
              <span class="project-meta-item">
                <i class="bi bi-record-circle"></i>
                {{ project.recordingCount }} {{ project.recordingCount === 1 ? 'recording' : 'recordings' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import WorkspaceClient from '@/services/workspace/WorkspaceClient';
import WorkspaceType from '@/services/workspace/model/WorkspaceType';
import ProfilerClient from '@/services/ProfilerClient';
import ProjectsClient from '@/services/ProjectsClient';
import ToastService from '@/services/ToastService';
import type Workspace from '@/services/workspace/model/Workspace';
import type Project from '@/services/model/Project';
import type ProfilerSettings from '@/services/model/ProfilerSettings';

interface WorkspaceWithSettings extends Workspace {
  hasCustomSettings: boolean;
  agentSettings?: string;
}

interface ProjectWithSettings extends Project {
  agentSettings?: string;
}

const isLoading = ref(true);
const isLoadingProjects = ref(false);
const globalSettings = ref<ProfilerSettings | null>(null);
const workspaces = ref<WorkspaceWithSettings[]>([]);
const selectedWorkspaceId = ref<string | null>(null);
const projectsWithOverrides = ref<ProjectWithSettings[]>([]);
const allSettings = ref<ProfilerSettings[]>([]);

// Active command display state
const activeCommand = ref<string | null>(null);
const activeCommandLabel = ref<string>('');
const activeLevel = ref<'global' | 'workspace' | 'project' | null>(null);
const activeWorkspaceId = ref<string | null>(null);
const activeProjectId = ref<string | null>(null);

const selectedWorkspaceName = computed(() => {
  const workspace = workspaces.value.find(w => w.id === selectedWorkspaceId.value);
  return workspace?.name || 'Unknown Workspace';
});

const setActiveCommand = (
  level: 'global' | 'workspace' | 'project',
  command: string | null,
  workspaceId?: string,
  projectId?: string,
  projectName?: string
) => {
  activeCommand.value = command;
  activeLevel.value = level;
  activeWorkspaceId.value = workspaceId || null;
  activeProjectId.value = projectId || null;

  // Set label based on level
  if (level === 'global') {
    activeCommandLabel.value = 'Global Configuration';
    // Deselect any workspace when global is selected
    selectedWorkspaceId.value = null;
    projectsWithOverrides.value = [];
  } else if (level === 'workspace') {
    const workspace = workspaces.value.find(w => w.id === workspaceId);
    activeCommandLabel.value = `Workspace Configuration (${workspace?.name || 'Unknown'})`;
  } else if (level === 'project') {
    activeCommandLabel.value = `Project Configuration (${projectName || 'Unknown'})`;
  }
};

const loadAllSettings = async () => {
  try {
    const settings = await ProfilerClient.fetchAll();

    // fetchAll returns an array of all settings
    if (Array.isArray(settings)) {
      allSettings.value = settings;

      // Find global settings (workspaceId: null, projectId: null)
      const global = settings.find(s => s.workspaceId === null && s.projectId === null);
      globalSettings.value = global || null;
    } else {
      allSettings.value = [];
      globalSettings.value = null;
    }
  } catch (error) {
    console.error('Failed to load settings:', error);
    allSettings.value = [];
    globalSettings.value = null;
  }
};

const loadWorkspaces = async () => {
  try {
    const allWorkspaces = await WorkspaceClient.list();
    const localWorkspaces = allWorkspaces.filter(w => w.type === WorkspaceType.LOCAL);

    // Match workspaces with their settings from allSettings
    const workspacesWithSettings: WorkspaceWithSettings[] = localWorkspaces.map(workspace => {
      // Find workspace-level settings (workspaceId matches, projectId is null)
      const workspaceSetting = allSettings.value.find(
        s => s.workspaceId === workspace.id && s.projectId === null
      );

      return {
        ...workspace,
        hasCustomSettings: !!workspaceSetting,
        agentSettings: workspaceSetting?.agentSettings
      };
    });

    workspaces.value = workspacesWithSettings;
  } catch (error) {
    console.error('Failed to load workspaces:', error);
    ToastService.error('Failed to load workspaces', 'Cannot load workspaces from the server.');
  }
};

const handleWorkspaceClick = async (workspace: WorkspaceWithSettings) => {
  // Toggle workspace selection for showing projects
  if (selectedWorkspaceId.value === workspace.id) {
    // Deselect if clicking the same workspace
    selectedWorkspaceId.value = null;
    projectsWithOverrides.value = [];
    // Clear active command
    activeCommand.value = null;
    activeLevel.value = null;
    activeWorkspaceId.value = null;
  } else {
    // Select new workspace (automatically deselects any previous one)
    selectedWorkspaceId.value = workspace.id;
    await loadProjectsWithOverrides(workspace.id);

    // Set active command if workspace has custom settings
    if (workspace.hasCustomSettings && workspace.agentSettings) {
      setActiveCommand('workspace', workspace.agentSettings, workspace.id);
    }
  }
};

const loadProjectsWithOverrides = async (workspaceId: string) => {
  isLoadingProjects.value = true;
  try {
    // Load all projects in the workspace
    const allProjects = await ProjectsClient.list(workspaceId);

    // Match projects with their settings from allSettings
    const projectsWithSettings: ProjectWithSettings[] = [];
    for (const project of allProjects) {
      // Find project-level settings (workspaceId and projectId both match)
      const projectSetting = allSettings.value.find(
        s => s.workspaceId === workspaceId && s.projectId === project.id
      );

      // Only include projects that have custom settings
      if (projectSetting) {
        projectsWithSettings.push({
          ...project,
          agentSettings: projectSetting.agentSettings
        });
      }
    }

    projectsWithOverrides.value = projectsWithSettings;
  } catch (error) {
    console.error('Failed to load projects:', error);
    ToastService.error('Failed to load projects', 'Cannot load projects from the server.');
    projectsWithOverrides.value = [];
  } finally {
    isLoadingProjects.value = false;
  }
};

onMounted(async () => {
  isLoading.value = true;
  await loadAllSettings();
  await loadWorkspaces();
  isLoading.value = false;
});
</script>

<style scoped>
@import '@/styles/form-utilities.css';

.profiler-hierarchy {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px;
  background: rgba(94, 100, 255, 0.05);
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 12px;
  color: #6b7280;
  font-size: 0.9rem;
  justify-content: center;
}

/* Command Display - matching final-command-display from Apply Configuration */
.final-command-display {
  margin-bottom: 24px;
}

.command-label {
  display: block;
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.command-preview {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  padding: 14px 16px;
}

.command-preview code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.85rem;
  color: #374151;
  background: none;
  word-break: break-all;
}

/* Scope Options - matching Apply Configuration */
.scope-options {
  margin-bottom: 24px;
}

.scope-section-title {
  font-size: 1rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 16px;
}

.scope-option-cards {
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 16px;
}

.scope-option-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 2px solid rgba(94, 100, 255, 0.1);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.scope-option-card:hover {
  border-color: rgba(94, 100, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
}

.scope-option-card.selected {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
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
  color: #5e64ff;
  margin-top: 2px;
}

.scope-option-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
  margin: 0 0 4px 0;
}

.scope-option-description {
  font-size: 0.8rem;
  color: #6b7280;
  margin: 0;
  line-height: 1.4;
}

/* Workspace Selection - matching Apply Configuration */
.workspace-selection-section {
  margin-top: 20px;
}

.workspace-section-title {
  font-size: 1rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 16px;
}

.no-workspaces-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(94, 100, 255, 0.05);
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  color: #6b7280;
  font-size: 0.85rem;
}

.workspace-selection-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.workspace-selection-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 2px solid rgba(94, 100, 255, 0.1);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
}

.workspace-selection-card:hover {
  border-color: rgba(94, 100, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
}

.workspace-selection-card.selected {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.2);
}

.workspace-selection-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.workspace-selection-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.workspace-selection-info i {
  font-size: 0.9rem;
  color: #5e64ff;
  flex-shrink: 0;
}

.workspace-selection-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1a237e;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-selection-description {
  font-size: 0.75rem;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.badge-custom {
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  border: 1px solid rgba(94, 100, 255, 0.3);
  font-size: 0.7rem;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.badge-uses-global {
  background: rgba(156, 163, 175, 0.1);
  color: #6b7280;
  border: 1px solid rgba(156, 163, 175, 0.3);
  font-size: 0.7rem;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Project Selection - matching Apply Configuration */
.project-selection-section {
  margin-top: 20px;
}

.loading-projects-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(94, 100, 255, 0.05);
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  color: #6b7280;
  font-size: 0.85rem;
}

.no-projects-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(94, 100, 255, 0.03);
  border: 1px dashed rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  color: #6b7280;
  font-size: 0.85rem;
  font-style: italic;
}

.project-selection-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.project-selection-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 2px solid rgba(94, 100, 255, 0.15);
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.project-selection-card:hover {
  border-color: rgba(94, 100, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
}

.project-selection-card.selected {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.2);
}

.project-selection-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.project-selection-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.project-selection-info i {
  font-size: 0.95rem;
  color: #5e64ff;
}

.project-selection-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
}

.project-selection-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 0.75rem;
  color: #6b7280;
}

.project-meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.project-meta-item i {
  font-size: 0.7rem;
  opacity: 0.8;
}

/* Responsive Design */
@media (max-width: 1400px) {
  .workspace-selection-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1024px) {
  .workspace-selection-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .workspace-selection-grid {
    grid-template-columns: 1fr;
  }

  .project-selection-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .workspace-selection-grid {
    grid-template-columns: 1fr;
  }
}
</style>
