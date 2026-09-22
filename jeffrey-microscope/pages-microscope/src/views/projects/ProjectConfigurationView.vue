<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-sliders" title="Configuration" />
      </template>

      <LoadingState v-if="isLoading" message="Loading configuration..." />
      <ErrorState v-else-if="loadError" :message="loadError" @retry="loadConfigs" />

      <div v-else class="project-configuration">
        <p class="scope-hint">
          Set here, this applies to every JVM of this project and overrides the workspace. The
          container's own configuration still wins, and a change reaches a JVM on its next start.
        </p>

        <ScopedConfigEditor
          scope="PROJECT"
          :entries="projectEntries"
          :is-deleting="isDeleting"
          @save="saveValue"
          @delete="deleteValue"
        />

        <section class="inherited">
          <h3 class="inherited-title">Inherited</h3>
          <p v-if="inheritedScopes.length === 0" class="scope-hint">
            Nothing is set above this project, so a JVM falls back to the Provisioner's built-in
            command.
          </p>
          <ScopedConfigEditor
            v-for="scope in inheritedScopes"
            :key="scope"
            :scope="scope"
            :entries="workspaceEntries"
            readonly
          />
        </section>
      </div>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useNavigation } from '@/composables/useNavigation';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import ToastService from '@shared/services/ToastService';
import ScopedConfigEditor from '@/components/config/ScopedConfigEditor.vue';
import ProjectConfigClient from '@/services/api/ProjectConfigClient';
import WorkspaceConfigClient from '@/services/api/WorkspaceConfigClient';
import type ConfigEntry from '@/services/api/model/ConfigEntry';
import type { ConfigScope, ConfigType } from '@/services/api/model/ConfigEntry';

const { hubId, workspaceId, projectId } = useNavigation();
const projectClient = new ProjectConfigClient(hubId.value, workspaceId.value, projectId.value);
const workspaceClient = new WorkspaceConfigClient(hubId.value, workspaceId.value);

const isLoading = ref(true);
const isDeleting = ref(false);
const loadError = ref<string | null>(null);
const projectEntries = ref<ConfigEntry[]>([]);
const workspaceEntries = ref<ConfigEntry[]>([]);

/** What this project sits on: the scopes above it that actually hold something, in merge order. */
const inheritedScopes = computed<ConfigScope[]>(() =>
  (['GLOBAL', 'WORKSPACE'] as ConfigScope[]).filter((scope) =>
    workspaceEntries.value.some((entry) => entry.scope === scope)
  )
);

async function loadConfigs() {
  isLoading.value = true;
  loadError.value = null;
  try {
    const [project, workspace] = await Promise.all([
      projectClient.fetch(),
      workspaceClient.list()
    ]);
    projectEntries.value = project;
    workspaceEntries.value = workspace;
  } catch (error) {
    console.error('Failed to load configuration:', error);
    loadError.value = 'Failed to load configuration';
  } finally {
    isLoading.value = false;
  }
}

async function saveValue(type: ConfigType, value: string) {
  try {
    projectEntries.value = await projectClient.upsert(type, value);
    ToastService.success('Configuration saved', 'It applies the next time a JVM of this project starts.');
  } catch (error) {
    console.error('Failed to save configuration:', error);
    ToastService.error('Save failed', messageOf(error, 'Could not save the configuration.'));
  }
}

async function deleteValue(type: ConfigType) {
  isDeleting.value = true;
  try {
    await projectClient.delete(type);
    projectEntries.value = await projectClient.fetch();
    ToastService.success('Configuration removed', 'This project now inherits from its workspace.');
  } catch (error) {
    console.error('Failed to remove configuration:', error);
    ToastService.error('Removal failed', messageOf(error, 'Could not remove the configuration.'));
  } finally {
    isDeleting.value = false;
  }
}

/** The Hub validates the value, so its message is what tells the operator what to change. */
function messageOf(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

onMounted(loadConfigs);
</script>

<style scoped>
.project-configuration {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.scope-hint {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.inherited {
  border-top: 1px solid var(--border-color);
  padding-top: var(--spacing-md);
}

.inherited-title {
  margin: 0 0 var(--spacing-sm);
  font-size: var(--font-size-base);
  color: var(--text-secondary);
}
</style>
