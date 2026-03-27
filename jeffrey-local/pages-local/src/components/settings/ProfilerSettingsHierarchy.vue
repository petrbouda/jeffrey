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
      <CommandDisplay
          v-if="activeCommand"
          :command="activeCommand"
          :deletable="canDeleteCurrentConfig"
          @delete="deleteCurrentConfig"
      >
        <template #header-left>
          <SettingsBreadcrumbs :items="activeCommandBreadcrumbs"/>
        </template>
      </CommandDisplay>

      <!-- Uses Global Settings Note -->
      <CommandDisplay v-if="showUsesGlobalNote" :command="null">
        <template #header-left>
          <SettingsBreadcrumbs :items="[
            {icon: 'bi-globe2', label: 'Global', onClick: () => navigateToBreadcrumb('global')},
            {icon: 'bi-folder-fill', label: usesGlobalWorkspaceName, active: true},
          ]"/>
        </template>
        <template #content>
          <div class="info-message">
            <i class="bi bi-arrow-up-circle"></i>
            <div>
              <strong>Uses Global Settings</strong>
              <p>No custom configuration set. Inherits from Global level.</p>
            </div>
          </div>
        </template>
      </CommandDisplay>

      <!-- No Global Settings Note -->
      <CommandDisplay v-if="activeLevel === 'global' && !activeCommand" :command="null">
        <template #header-left>
          <SettingsBreadcrumbs :items="[{icon: 'bi-globe2', label: 'Global', active: true}]"/>
        </template>
        <template #content>
          <div class="info-message">
            <i class="bi bi-info-circle"></i>
            <div>
              <strong>No Global Settings Configured</strong>
              <p>No global configuration has been set. Configure settings in the 'Configure' tab to apply them globally.</p>
            </div>
          </div>
        </template>
      </CommandDisplay>

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
import CommandDisplay from '@/components/settings/CommandDisplay.vue';
import SettingsBreadcrumbs from '@/components/settings/SettingsBreadcrumbs.vue';
import type {BreadcrumbItem} from '@/components/settings/SettingsBreadcrumbs.vue';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import GlobalProfilerClient from '@/services/api/GlobalProfilerClient';
import ProjectsClient from '@/services/api/ProjectsClient';

const workspaceClient = new WorkspaceClient();
const globalProfilerClient = new GlobalProfilerClient();
const projectsClient = new ProjectsClient();
import ToastService from '@/services/ToastService';
import type Workspace from '@/services/api/model/Workspace';
import type Project from '@/services/api/model/Project';
import type ProfilerSettings from '@/services/api/model/ProfilerSettings';

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
const activeProjectName = ref<string>('');
const showUsesGlobalNote = ref(false);
const usesGlobalWorkspaceName = ref<string>('');

const selectedWorkspaceName = computed(() => {
  const workspace = workspaces.value.find(w => w.id === selectedWorkspaceId.value);
  return workspace?.name || 'Unknown Workspace';
});

const activeCommandBreadcrumbs = computed<BreadcrumbItem[]>(() => {
  const items: BreadcrumbItem[] = [
    {icon: 'bi-globe2', label: 'Global', active: activeLevel.value === 'global', onClick: () => navigateToBreadcrumb('global')},
  ];
  if (activeLevel.value === 'workspace' || activeLevel.value === 'project') {
    items.push({
      icon: 'bi-folder-fill',
      label: getWorkspaceName(activeWorkspaceId.value),
      active: activeLevel.value === 'workspace',
      onClick: () => navigateToBreadcrumb('workspace'),
    });
  }
  if (activeLevel.value === 'project') {
    items.push({icon: 'bi-diagram-3-fill', label: activeProjectName.value, active: true});
  }
  return items;
});

const canDeleteCurrentConfig = computed(() => {
  // Can only delete CUSTOM workspace or project settings, not global
  if (activeLevel.value === 'global') {
    return false;
  }
  if (activeLevel.value === 'workspace') {
    const workspace = workspaces.value.find(w => w.id === activeWorkspaceId.value);
    return workspace?.hasCustomSettings || false;
  }
  if (activeLevel.value === 'project') {
    // Projects in the list always have custom settings
    return true;
  }
  return false;
});

const getWorkspaceName = (workspaceId: string | null): string => {
  if (!workspaceId) return 'Unknown';
  const workspace = workspaces.value.find(w => w.id === workspaceId);
  return workspace?.name || 'Unknown';
};

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
  activeProjectName.value = projectName || '';
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

const navigateToBreadcrumb = (level: 'global' | 'workspace' | 'project') => {
  if (level === 'global') {
    // Navigate to global
    setActiveCommand('global', globalSettings.value?.agentSettings || null);
    selectedWorkspaceId.value = null;
    projectsWithOverrides.value = [];
  } else if (level === 'workspace' && activeWorkspaceId.value) {
    // Navigate to workspace
    const workspace = workspaces.value.find(w => w.id === activeWorkspaceId.value);
    if (workspace) {
      if (workspace.hasCustomSettings && workspace.agentSettings) {
        setActiveCommand('workspace', workspace.agentSettings, workspace.id);
      } else {
        // Show uses global note
        activeCommand.value = null;
        activeLevel.value = null;
        showUsesGlobalNote.value = true;
        usesGlobalWorkspaceName.value = workspace.name;
      }
    }
  }
  // Project level is not clickable (it's the current item)
};

const deleteCurrentConfig = async () => {
  if (activeLevel.value === 'workspace' && activeWorkspaceId.value) {
    const workspace = workspaces.value.find(w => w.id === activeWorkspaceId.value);
    if (workspace) {
      await handleWorkspaceDelete(workspace);
    }
  } else if (activeLevel.value === 'project' && activeProjectId.value) {
    const project = projectsWithOverrides.value.find(p => p.id === activeProjectId.value);
    if (project) {
      await handleProjectDelete(project);
    }
  }
};

const loadAllSettings = async () => {
  try {
    const settings = await globalProfilerClient.fetchAll();

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
    const allWorkspaces = await workspaceClient.list();

    // Match workspaces with their settings from allSettings
    const workspacesWithSettings: WorkspaceWithSettings[] = allWorkspaces.map(workspace => {
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
    const allProjects = await projectsClient.list(workspaceId);

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

const handleWorkspaceDelete = async (workspace: WorkspaceWithSettings) => {
  // Only allow deletion of CUSTOM workspace settings
  if (!workspace.hasCustomSettings) {
    ToastService.warning('Cannot Delete', 'Global settings cannot be deleted. You can only modify them in the Configure tab.');
    return;
  }

  // Confirm deletion
  const confirmed = confirm(
    `Are you sure you want to delete custom settings for workspace "${workspace.name}"?\n\n` +
    'This will revert the workspace to using global settings.'
  );

  if (!confirmed) {
    return;
  }

  try {
    // Delete workspace settings (workspaceId set, projectId is null)
    await globalProfilerClient.delete(workspace.id, null);

    ToastService.success('Deleted', `Custom settings for workspace "${workspace.name}" have been deleted.`);

    // Reload all settings and workspaces
    await loadAllSettings();
    await loadWorkspaces();

    // Clear active command if the deleted workspace was selected
    if (activeLevel.value === 'workspace' && activeWorkspaceId.value === workspace.id) {
      activeCommand.value = null;
      activeLevel.value = null;
      activeWorkspaceId.value = null;
      showUsesGlobalNote.value = false;
    }

    // Clear workspace selection and projects
    if (selectedWorkspaceId.value === workspace.id) {
      selectedWorkspaceId.value = null;
      projectsWithOverrides.value = [];
    }
  } catch (error) {
    console.error('Failed to delete workspace settings:', error);
    ToastService.error('Delete Failed', 'Could not delete workspace settings.');
  }
};

const handleProjectDelete = async (project: ProjectWithSettings) => {
  // Confirm deletion
  const confirmed = confirm(
    `Are you sure you want to delete custom settings for project "${project.name}"?\n\n` +
    'This will revert the project to using workspace or global settings.'
  );

  if (!confirmed) {
    return;
  }

  try {
    // Delete project settings (both workspaceId and projectId are set)
    await globalProfilerClient.delete(selectedWorkspaceId.value, project.id);

    ToastService.success('Deleted', `Custom settings for project "${project.name}" have been deleted.`);

    // Reload all settings
    await loadAllSettings();

    // Reload projects for the current workspace
    if (selectedWorkspaceId.value) {
      await loadProjectsWithOverrides(selectedWorkspaceId.value);
    }

    // Clear active command if the deleted project was selected
    if (activeLevel.value === 'project' && activeProjectId.value === project.id) {
      activeCommand.value = null;
      activeLevel.value = null;
      activeProjectId.value = null;
      showUsesGlobalNote.value = false;
    }
  } catch (error) {
    console.error('Failed to delete project settings:', error);
    ToastService.error('Delete Failed', 'Could not delete project settings.');
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
  min-width: 0;
  overflow: hidden;
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

/* Info Message (inside CommandDisplay content slot) */
.info-message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.info-message > i {
  font-size: 1.2rem;
  color: var(--color-primary, #5e64ff);
  margin-top: 1px;
  flex-shrink: 0;
}

.info-message strong {
  display: block;
  font-size: var(--font-size-base, 0.875rem);
  color: var(--color-dark, #0b1727);
  margin-bottom: 4px;
}

.info-message p {
  font-size: var(--font-size-sm, 0.7rem);
  color: var(--color-text-muted, #748194);
  line-height: 1.5;
  margin: 0;
}

/* Scope Options - matching Apply Configuration */
.scope-options {
  margin-top: 20px;
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
