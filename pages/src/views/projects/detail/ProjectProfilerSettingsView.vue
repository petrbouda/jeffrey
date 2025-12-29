<template>
  <!-- Remote Workspace: Under Development Message -->
  <ProfilerSettingsRemoteAlert v-if="isRemoteWorkspace" />

  <!-- Live Workspace: Full Functionality -->
  <PageHeader
      v-else
      title="Profiler Settings"
      description="Configure profiler agent settings for this project"
      icon="bi-cpu"
  >

    <!-- Loading State -->
    <div v-if="isLoading" class="loading-state">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="mt-3">Loading profiler settings...</p>
    </div>

    <template v-else>
      <!-- Current Settings Section -->
      <div class="settings-section mb-4">
        <div class="section-header">
          <i class="bi bi-sliders section-icon"></i>
          <h6 class="section-title">Current Settings</h6>
          <Badge
              :value="settingsLevelLabel"
              :variant="settingsLevelVariant"
              size="xs"
              class="ms-2"
          />
          <!-- Delete button for Project-level settings -->
          <button
              v-if="settingsLevel === 'PROJECT'"
              type="button"
              class="btn btn-outline-danger btn-sm ms-auto"
              @click="deleteProjectSettings"
              :disabled="isDeleting"
              :title="`Deleting will revert to ${parentSettingsLevel} settings`"
          >
            <span v-if="isDeleting" class="spinner-border spinner-border-sm" role="status"></span>
            <i v-else class="bi bi-trash"></i>
          </button>
        </div>

        <!-- No Settings State -->
        <div v-if="!currentSettings" class="no-settings-state-compact">
          <i class="bi bi-gear text-muted"></i>
          <span>No settings configured. Use the builder below to create settings.</span>
        </div>

        <!-- Settings Display -->
        <div v-else class="current-settings-compact" @click="copyToClipboard" title="Click to copy">
          <code>{{ currentSettings }}</code>
          <i class="bi bi-clipboard copy-icon"></i>
        </div>
      </div>

      <!-- Configure New Settings Section -->
      <div class="settings-section">
        <div class="section-header">
          <i class="bi bi-plus-circle section-icon"></i>
          <h6 class="section-title">Configure New Settings</h6>
        </div>

        <!-- Step 1: Command Configuration -->
        <div v-if="currentStep === 1">
          <ConfigureCommand
              v-model="newCommand"
              @open-builder="openBuilder"
              @accept-command="applySettings"
              @clear="newCommand = ''"
          />
        </div>

        <!-- Step 2: Builder Mode -->
        <div v-if="currentStep === 2">
          <CommandBuilder
              @cancel="cancelBuilder"
              @accept-command="acceptBuilderCommand"
          />
        </div>
      </div>
    </template>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useNavigation } from '@/composables/useNavigation';
import ProfilerClient from '@/services/api/ProfilerClient';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import ToastService from '@/services/ToastService';
import Badge from '@/components/Badge.vue';
import ConfigureCommand from '@/components/settings/ConfigureCommand.vue';
import CommandBuilder from '@/components/settings/CommandBuilder.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import ProfilerSettingsRemoteAlert from '@/components/alerts/ProfilerSettingsRemoteAlert.vue';
import WorkspaceType from '@/services/api/model/WorkspaceType';
import type Workspace from '@/services/api/model/Workspace';
import type ProfilerSettings from '@/services/api/model/ProfilerSettings';

const { workspaceId, projectId } = useNavigation();

// State
const isLoading = ref(true);
const isDeleting = ref(false);
const currentStep = ref(1);
const newCommand = ref('');
const workspaceInfo = ref<Workspace | null>(null);

// Settings at different levels
const globalSettings = ref<ProfilerSettings | null>(null);
const workspaceSettings = ref<ProfilerSettings | null>(null);
const projectSettings = ref<ProfilerSettings | null>(null);

// Computed: Check if Remote workspace
const isRemoteWorkspace = computed(() => {
  return workspaceInfo.value?.type === WorkspaceType.REMOTE;
});

// Computed: Current effective settings
const currentSettings = computed(() => {
  if (projectSettings.value?.agentSettings) {
    return projectSettings.value.agentSettings;
  }
  if (workspaceSettings.value?.agentSettings) {
    return workspaceSettings.value.agentSettings;
  }
  if (globalSettings.value?.agentSettings) {
    return globalSettings.value.agentSettings;
  }
  return null;
});

// Computed: Settings level (where the current settings come from)
const settingsLevel = computed<'PROJECT' | 'WORKSPACE' | 'GLOBAL' | 'NONE'>(() => {
  if (projectSettings.value?.agentSettings) {
    return 'PROJECT';
  }
  if (workspaceSettings.value?.agentSettings) {
    return 'WORKSPACE';
  }
  if (globalSettings.value?.agentSettings) {
    return 'GLOBAL';
  }
  return 'NONE';
});

// Computed: Badge label
const settingsLevelLabel = computed(() => {
  switch (settingsLevel.value) {
    case 'PROJECT': return 'PROJECT';
    case 'WORKSPACE': return 'WORKSPACE';
    case 'GLOBAL': return 'GLOBAL';
    default: return 'NONE';
  }
});

// Computed: Badge variant
const settingsLevelVariant = computed(() => {
  switch (settingsLevel.value) {
    case 'PROJECT': return 'green';
    case 'WORKSPACE': return 'primary';
    case 'GLOBAL': return 'gray';
    default: return 'yellow';
  }
});

// Computed: Parent settings level (for revert hint)
const parentSettingsLevel = computed(() => {
  if (workspaceSettings.value?.agentSettings) {
    return 'workspace';
  }
  if (globalSettings.value?.agentSettings) {
    return 'global';
  }
  return 'none (no parent settings)';
});

// Load all settings
async function loadSettings() {
  isLoading.value = true;
  try {
    // Fetch workspace info first to check type
    const workspaces = await WorkspaceClient.list();
    workspaceInfo.value = workspaces.find(w => w.id === workspaceId.value) || null;

    // Skip loading settings for Remote workspaces
    if (isRemoteWorkspace.value) {
      isLoading.value = false;
      return;
    }

    // Fetch all settings levels
    const allSettings = await ProfilerClient.fetchAll();

    // Find settings at each level
    globalSettings.value = allSettings.find(
        s => s.workspaceId === null && s.projectId === null
    ) || null;

    workspaceSettings.value = allSettings.find(
        s => s.workspaceId === workspaceId.value && s.projectId === null
    ) || null;

    projectSettings.value = allSettings.find(
        s => s.workspaceId === workspaceId.value && s.projectId === projectId.value
    ) || null;
  } catch (error) {
    console.error('Failed to load profiler settings:', error);
    ToastService.error('Error', 'Failed to load profiler settings');
  } finally {
    isLoading.value = false;
  }
}

// Copy settings to clipboard
async function copyToClipboard() {
  if (currentSettings.value) {
    try {
      await navigator.clipboard.writeText(currentSettings.value);
      ToastService.success('Copied!', 'Settings copied to clipboard');
    } catch (error) {
      console.error('Failed to copy:', error);
      ToastService.error('Copy Failed', 'Could not copy to clipboard');
    }
  }
}

// Delete project-level settings
async function deleteProjectSettings() {
  isDeleting.value = true;
  try {
    await ProfilerClient.delete(workspaceId.value, projectId.value);
    projectSettings.value = null;
    ToastService.success('Settings Deleted', 'Project-level profiler settings have been removed.');
  } catch (error) {
    console.error('Failed to delete settings:', error);
    ToastService.error('Error', 'Failed to delete profiler settings');
  } finally {
    isDeleting.value = false;
  }
}

// Apply new settings
async function applySettings(command: string) {
  if (!command?.trim()) return;

  try {
    await ProfilerClient.upsert(workspaceId.value, projectId.value, command.trim());
    ToastService.success('Settings Applied', 'Profiler settings have been applied to this project.');
    await loadSettings();
    newCommand.value = '';
    currentStep.value = 1;
  } catch (error) {
    console.error('Failed to apply settings:', error);
    ToastService.error('Error', 'Failed to apply profiler settings');
  }
}

// Builder navigation
function openBuilder() {
  currentStep.value = 2;
}

function cancelBuilder() {
  currentStep.value = 1;
}

function acceptBuilderCommand(command: string) {
  newCommand.value = command;
  currentStep.value = 1;
  ToastService.success('Command Accepted', 'Builder configuration has been converted to command.');
}

onMounted(() => {
  loadSettings();
});
</script>

<style scoped>
/* Loading State */
.loading-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 3rem;
  color: #6b7280;
}

/* Section Styling */
.settings-section {
  background: white;
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(94, 100, 255, 0.08);
}

.section-icon {
  color: #5e64ff;
  font-size: 1rem;
}

.section-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

/* No Settings State - Compact */
.no-settings-state-compact {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: #f9fafb;
  border-radius: 6px;
  font-size: 0.85rem;
  color: #6b7280;
}

/* Current Settings Display - Compact */
.current-settings-compact {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border: 1px solid rgba(94, 100, 255, 0.12);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.current-settings-compact:hover {
  border-color: rgba(94, 100, 255, 0.3);
  background: linear-gradient(135deg, #f1f5f9, #e8eaf6);
}

.current-settings-compact code {
  flex: 1;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.8rem;
  color: #374151;
  line-height: 1.5;
  word-break: break-all;
  white-space: pre-wrap;
  background: none;
  padding: 0;
}

.current-settings-compact .copy-icon {
  color: #9ca3af;
  font-size: 0.85rem;
  flex-shrink: 0;
  transition: color 0.2s ease;
}

.current-settings-compact:hover .copy-icon {
  color: #5e64ff;
}
</style>
