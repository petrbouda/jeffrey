<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-cpu" title="Profiler Settings" />
      </template>

      <!-- Loading State -->
      <div v-if="isLoading" class="loading-state">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="mt-3">Loading profiler settings...</p>
    </div>

    <div v-else class="profiler-settings-content">
      <!-- Current Settings Display -->
      <CommandDisplay
        :command="currentSettings"
        :deletable="settingsLevel === 'PROJECT'"
        :deleting="isDeleting"
        @delete="deleteProjectSettings"
      >
        <template #header-left>
          <SettingsBreadcrumbs :items="breadcrumbItems" />
        </template>
      </CommandDisplay>

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
          v-model="newCommand"
          @accept-command="applySettings"
          @clear="newCommand = ''"
        />
        <CommandBuilder
          v-if="activeTab === 'builder'"
          @cancel="cancelBuilder"
          @accept-command="acceptBuilderCommand"
        />
      </div>
    </div>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useNavigation } from '@/composables/useNavigation';
import ProjectProfilerClient from '@/services/api/ProjectProfilerClient';
import ToastService from '@/services/ToastService';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import ConfigureCommand from '@/components/settings/ConfigureCommand.vue';
import CommandBuilder from '@/components/settings/CommandBuilder.vue';
import CommandDisplay from '@/components/settings/CommandDisplay.vue';
import SettingsBreadcrumbs from '@/components/settings/SettingsBreadcrumbs.vue';
import type { BreadcrumbItem } from '@/components/settings/SettingsBreadcrumbs.vue';
import type ProfilerSettings from '@/services/api/model/ProfilerSettings';

const { workspaceId, projectId } = useNavigation();
const profilerClient = new ProjectProfilerClient(workspaceId.value, projectId.value);

// State
const isLoading = ref(true);
const isDeleting = ref(false);
const activeTab = ref<'manual' | 'builder'>('manual');
const newCommand = ref('');

// Effective settings from backend (already resolved from hierarchy)
const effectiveSettings = ref<ProfilerSettings | null>(null);

// Computed
const currentSettings = computed(() => effectiveSettings.value?.agentSettings ?? null);
const settingsLevel = computed(() => effectiveSettings.value?.level ?? 'NONE');

const breadcrumbItems = computed<BreadcrumbItem[]>(() => {
  const level = settingsLevel.value;
  const items: BreadcrumbItem[] = [
    { icon: 'bi-globe2', label: 'Global', active: level === 'GLOBAL' || level === 'NONE' }
  ];
  if (level === 'WORKSPACE' || level === 'PROJECT') {
    items.push({ icon: 'bi-folder-fill', label: 'Workspace', active: level === 'WORKSPACE' });
  }
  if (level === 'PROJECT') {
    items.push({ icon: 'bi-diagram-3-fill', label: 'Project', active: true });
  }
  return items;
});

// Load effective settings
async function loadSettings() {
  isLoading.value = true;
  try {
    effectiveSettings.value = await profilerClient.fetch();
  } catch (error) {
    console.error('Failed to load profiler settings:', error);
    ToastService.error('Error', 'Failed to load profiler settings');
  } finally {
    isLoading.value = false;
  }
}

// Delete project-level settings
async function deleteProjectSettings() {
  isDeleting.value = true;
  try {
    await profilerClient.delete();
    await loadSettings();
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
    await profilerClient.upsert(command.trim());
    ToastService.success(
      'Settings Applied',
      'Profiler settings have been applied to this project.'
    );
    await loadSettings();
    newCommand.value = '';
    activeTab.value = 'manual';
  } catch (error) {
    console.error('Failed to apply settings:', error);
    ToastService.error('Error', 'Failed to apply profiler settings');
  }
}

// Builder navigation
function cancelBuilder() {
  activeTab.value = 'manual';
}

function acceptBuilderCommand(command: string) {
  newCommand.value = command;
  activeTab.value = 'manual';
  ToastService.success('Command Accepted', 'Builder configuration has been converted to command.');
}

onMounted(() => {
  loadSettings();
});
</script>

<style scoped>
/* Root content wrapper — prevent horizontal overflow from child components */
.profiler-settings-content {
  width: 100%;
  min-width: 0;
  overflow-x: hidden;
  box-sizing: border-box;
}

/* Loading State */
.loading-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 3rem;
  color: var(--color-text-muted);
}

/* Tab Bar */
.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--color-border);
  padding: 0;
  margin-top: 16px;
}

.tab-item {
  padding: 10px 20px;
  font-size: var(--font-size-base);
  font-weight: 600;
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

/* Tab Content */
.tab-content {
  padding-top: 16px;
  min-width: 0;
  overflow-x: hidden;
}

/* Constrain child component flex layouts to available width */
.tab-content :deep(.builder-and-command-layout) {
  min-width: 0;
}

.tab-content :deep(.builder-panel),
.tab-content :deep(.live-command-panel) {
  min-width: 0;
}

.tab-content :deep(.command-actions) {
  flex-wrap: wrap;
}

.tab-content :deep(.command-help) {
  overflow-wrap: break-word;
  word-break: break-all;
}
</style>
