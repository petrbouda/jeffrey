<template>
  <div class="workspace-configuration">
    <LoadingState v-if="isLoading" message="Loading configuration..." />
    <ErrorState v-else-if="loadError" :message="loadError" @retry="loadConfigs" />

    <div v-else>
      <p class="scope-hint">
        The Hub publishes these onto the shared volume, where the Provisioner merges them as it
        starts a JVM: a project's overrides its workspace, which overrides the global scope, and the
        container's own configuration overrides all three. Changes reach a JVM on its next start.
      </p>

      <TabBar v-model="activeScope" :tabs="SCOPE_TABS" />

      <ScopedConfigEditor
        :scope="activeScope"
        :entries="entries"
        :is-deleting="isDeleting"
        @save="saveValue"
        @delete="deleteValue"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import TabBar, { type TabBarItem } from '@shared/components/TabBar.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import ToastService from '@shared/services/ToastService';
import ScopedConfigEditor from '@/components/config/ScopedConfigEditor.vue';
import WorkspaceConfigClient from '@/services/api/WorkspaceConfigClient';
import type ConfigEntry from '@/services/api/model/ConfigEntry';
import type { ConfigScope, ConfigType } from '@/services/api/model/ConfigEntry';

interface Props {
  hubId: string;
  workspaceId: string;
  workspaceName?: string;
}

const props = defineProps<Props>();

/** Only the scopes a workspace owns; a project's is edited from the project itself. */
const SCOPE_TABS: TabBarItem[] = [
  { id: 'WORKSPACE', label: 'Workspace' },
  { id: 'GLOBAL', label: 'Global' }
];

const client = new WorkspaceConfigClient(props.hubId, props.workspaceId);

const isLoading = ref(true);
const isDeleting = ref(false);
const loadError = ref<string | null>(null);
const activeScope = ref<ConfigScope>('WORKSPACE');
const entries = ref<ConfigEntry[]>([]);

async function loadConfigs() {
  isLoading.value = true;
  loadError.value = null;
  try {
    entries.value = await client.list();
  } catch (error) {
    console.error('Failed to load configuration:', error);
    loadError.value = 'Failed to load configuration';
  } finally {
    isLoading.value = false;
  }
}

async function saveValue(type: ConfigType, value: string) {
  try {
    await client.upsert(activeScope.value, type, value);
    await loadConfigs();
    ToastService.success(
      'Configuration saved',
      `It applies the next time a JVM in ${scopeDescription()} starts.`
    );
  } catch (error) {
    console.error('Failed to save configuration:', error);
    ToastService.error('Save failed', messageOf(error, 'Could not save the configuration.'));
  }
}

async function deleteValue(type: ConfigType) {
  isDeleting.value = true;
  try {
    await client.delete(activeScope.value, type);
    await loadConfigs();
    ToastService.success('Configuration removed', 'Nothing is set at this scope any more.');
  } catch (error) {
    console.error('Failed to remove configuration:', error);
    ToastService.error('Removal failed', messageOf(error, 'Could not remove the configuration.'));
  } finally {
    isDeleting.value = false;
  }
}

function scopeDescription(): string {
  if (activeScope.value === 'GLOBAL') {
    return 'any workspace';
  }
  return props.workspaceName || 'this workspace';
}

/** The Hub validates the value, so its message is what tells the operator what to change. */
function messageOf(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

onMounted(loadConfigs);
</script>

<style scoped>
.workspace-configuration {
  min-width: 0;
}

.scope-hint {
  margin: 0 0 var(--spacing-md);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}
</style>
