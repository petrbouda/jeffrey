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
      <div v-if="activeCommand" class="config-output-section">
        <div class="config-output" @click="copyCommand" title="Click to copy command">
          <div class="config-output-header">
            <div class="config-output-label">
              <i :class="activeCommandIcon"></i>
              <span>{{ activeCommandLabel }}</span>
            </div>
            <div class="config-output-copy-hint">
              <i class="bi bi-clipboard"></i>
            </div>
          </div>
          <div class="config-output-content">
            <div class="compact-output">
              <div class="config-output-text">
                {{ activeCommand }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Uses Global Settings Note -->
      <div v-if="showUsesGlobalNote" class="config-output-section">
        <div class="config-output config-output-info">
          <div class="config-output-header">
            <div class="config-output-label">
              <i class="bi bi-folder-fill"></i>
              <span>Workspace Configuration ({{ usesGlobalWorkspaceName }})</span>
            </div>
          </div>
          <div class="config-output-content">
            <div class="uses-global-note">
              <i class="bi bi-arrow-up-circle"></i>
              <div class="uses-global-text">
                <strong>Uses Global Settings</strong>
                <p>No custom configuration set. Inherits from Global level.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- No Global Settings Note -->
      <div v-if="activeLevel === 'global' && !activeCommand" class="config-output-section">
        <div class="config-output config-output-info">
          <div class="config-output-header">
            <div class="config-output-label">
              <i class="bi bi-globe2"></i>
              <span>Global Configuration</span>
            </div>
          </div>
          <div class="config-output-content">
            <div class="uses-global-note">
              <i class="bi bi-info-circle"></i>
              <div class="uses-global-text">
                <strong>No Global Settings Configured</strong>
                <p>No global configuration has been set. Configure settings in the 'Configure' tab to apply them globally.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Global Settings -->
      <div class="scope-options">
        <div class="scope-section-title">Global Settings</div>
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
        <div class="workspace-section-title">Workspaces</div>
        <div v-if="workspaces.length === 0" class="no-workspaces-message">
          <i class="bi bi-info-circle"></i>
          <span>No workspaces available</span>
        </div>
        <div v-else class="workspace-selection-grid">
          <ProfilerSelectionCard
            v-for="workspace in workspaces"
            :key="workspace.id"
            :name="workspace.name"
            icon="bi-folder-fill"
            :selected="selectedWorkspaceId === workspace.id"
            selection-type="radio"
            :badge="workspace.hasCustomSettings ? 'CUSTOM' : 'GLOBAL'"
            @select="handleWorkspaceClick(workspace)"
          />
        </div>
      </div>

      <!-- Projects with Custom Settings -->
      <div v-if="selectedWorkspaceId" class="project-selection-section">
        <div class="workspace-section-title">Projects with Custom Settings in {{ selectedWorkspaceName }}</div>

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
          <ProfilerSelectionCard
            v-for="project in projectsWithOverrides"
            :key="project.id"
            :name="project.name"
            icon="bi-diagram-3-fill"
            :selected="activeLevel === 'project' && activeProjectId === project.id"
            selection-type="radio"
            badge="CUSTOM"
            @select="setActiveCommand('project', project.agentSettings || null, selectedWorkspaceId, project.id, project.name)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import ProfilerSelectionCard from '@/components/settings/ProfilerSelectionCard.vue';
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
const showUsesGlobalNote = ref(false);
const usesGlobalWorkspaceName = ref<string>('');

const selectedWorkspaceName = computed(() => {
  const workspace = workspaces.value.find(w => w.id === selectedWorkspaceId.value);
  return workspace?.name || 'Unknown Workspace';
});

const activeCommandIcon = computed(() => {
  if (activeLevel.value === 'global') {
    return 'bi-globe2';
  } else if (activeLevel.value === 'workspace') {
    return 'bi-folder-fill';
  } else if (activeLevel.value === 'project') {
    return 'bi-diagram-3-fill';
  }
  return 'bi-gear-fill';
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
  showUsesGlobalNote.value = false;

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

const copyCommand = async () => {
  if (activeCommand.value) {
    try {
      await navigator.clipboard.writeText(activeCommand.value);
      ToastService.success('Copied!', 'Command copied to clipboard');
    } catch (error) {
      console.error('Failed to copy:', error);
      ToastService.error('Copy Failed', 'Could not copy to clipboard');
    }
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
    showUsesGlobalNote.value = false;
  } else {
    // Select new workspace (automatically deselects any previous one)
    selectedWorkspaceId.value = workspace.id;
    await loadProjectsWithOverrides(workspace.id);

    // Set active command if workspace has custom settings
    if (workspace.hasCustomSettings && workspace.agentSettings) {
      setActiveCommand('workspace', workspace.agentSettings, workspace.id);
      showUsesGlobalNote.value = false;
    } else {
      // Show note if workspace uses global settings
      activeCommand.value = null;
      activeLevel.value = null;
      activeWorkspaceId.value = null;
      showUsesGlobalNote.value = true;
      usesGlobalWorkspaceName.value = workspace.name;
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

  // Always auto-select Global Configuration
  setActiveCommand('global', globalSettings.value?.agentSettings || null);
});
</script>

<style scoped>
@import '@/styles/form-utilities.css';

.profiler-hierarchy {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.04), rgba(94, 100, 255, 0.02));
  border: 1px solid rgba(94, 100, 255, 0.1);
  border-radius: 8px;
  color: #6b7280;
  font-size: 0.85rem;
  justify-content: center;
}

/* Command Display - Modern & Compact Design */
.config-output-section {
  margin-bottom: 20px;
}

.config-output {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 2px solid rgba(94, 100, 255, 0.12);
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.04);
  cursor: pointer;
}

.config-output:hover {
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.15);
  transform: translateY(-1px);
}

.config-output.config-output-info {
  cursor: default;
}

.config-output.config-output-info:hover {
  transform: none;
}

.config-output-header {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  padding: 8px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(94, 100, 255, 0.15);
  min-height: 36px;
}

.config-output-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1f2937;
  font-size: 0.85rem;
  font-weight: 600;
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.config-output-label i {
  color: #5e64ff;
  font-size: 0.9rem;
}

.config-output-copy-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #6b7280;
  font-size: 0.75rem;
  opacity: 0.6;
  transition: all 0.2s ease;
}

.config-output-copy-hint i {
  font-size: 0.85rem;
}

.config-output:hover .config-output-copy-hint {
  opacity: 1;
  color: #5e64ff;
}

.config-output-content {
  padding: 12px 14px;
  background: rgba(94, 100, 255, 0.02);
}

.compact-output {
  display: flex;
  flex-direction: column;
}

.compact-output .config-output-text {
  margin: 0;
  padding: 10px 12px;
  background: rgba(94, 100, 255, 0.05);
  border: 1px solid rgba(94, 100, 255, 0.1);
  border-radius: 6px;
  font-size: 0.8rem;
  line-height: 1.6;
  color: #374151;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-weight: 500;
  word-break: break-all;
  white-space: pre-wrap;
  transition: all 0.15s ease;
}

.compact-output .config-output-text:hover {
  background: rgba(94, 100, 255, 0.08);
  border-color: rgba(94, 100, 255, 0.2);
}

/* Uses Global Note */
.uses-global-note {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  background: rgba(94, 100, 255, 0.05);
  border: 1px solid rgba(94, 100, 255, 0.1);
  border-radius: 6px;
  transition: all 0.15s ease;
}

.uses-global-note:hover {
  background: rgba(94, 100, 255, 0.08);
  border-color: rgba(94, 100, 255, 0.2);
}

.uses-global-note > i {
  font-size: 1.3rem;
  color: #5e64ff;
  margin-top: 2px;
  flex-shrink: 0;
}

.uses-global-text {
  flex: 1;
}

.uses-global-text strong {
  display: block;
  font-size: 0.85rem;
  color: #1f2937;
  margin-bottom: 4px;
  font-weight: 600;
}

.uses-global-text p {
  font-size: 0.8rem;
  color: #6b7280;
  line-height: 1.5;
  margin: 0;
}

/* Scope Options - matching Apply Configuration */
.scope-options {
  margin-bottom: 20px;
}

.scope-section-title {
  font-size: 0.8rem;
  font-weight: 700;
  color: #6b7280;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  display: flex;
  align-items: center;
  gap: 8px;
}

.scope-section-title::before {
  content: '';
  width: 3px;
  height: 14px;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border-radius: 2px;
}

.scope-option-cards {
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 12px;
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
  font-size: 0.8rem;
  font-weight: 700;
  color: #6b7280;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  display: flex;
  align-items: center;
  gap: 8px;
}

.workspace-section-title::before {
  content: '';
  width: 3px;
  height: 14px;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border-radius: 2px;
}

.no-workspaces-message {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.03), rgba(94, 100, 255, 0.01));
  border: 1px dashed rgba(94, 100, 255, 0.2);
  border-radius: 6px;
  color: #9ca3af;
  font-size: 0.8rem;
}

.no-workspaces-message i {
  color: #5e64ff;
  opacity: 0.5;
  font-size: 0.9rem;
}

.workspace-selection-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}


/* Project Selection - matching Apply Configuration */
.project-selection-section {
  margin-top: 20px;
}

.loading-projects-message {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.04), rgba(94, 100, 255, 0.02));
  border: 1px solid rgba(94, 100, 255, 0.1);
  border-radius: 6px;
  color: #6b7280;
  font-size: 0.8rem;
}

.no-projects-message {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.03), rgba(94, 100, 255, 0.01));
  border: 1px dashed rgba(94, 100, 255, 0.2);
  border-radius: 6px;
  color: #9ca3af;
  font-size: 0.8rem;
}

.no-projects-message i {
  color: #5e64ff;
  opacity: 0.5;
  font-size: 0.9rem;
}

.project-selection-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

/* Responsive Design */

/* Large screens: 3 columns */
@media (max-width: 1600px) {
  .workspace-selection-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .project-selection-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

/* Medium screens: 2 columns */
@media (max-width: 1200px) {
  .workspace-selection-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .project-selection-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* Tablet and below */
@media (max-width: 768px) {
  .workspace-selection-grid {
    grid-template-columns: 1fr;
  }

  .project-selection-grid {
    grid-template-columns: 1fr;
  }
}
</style>
