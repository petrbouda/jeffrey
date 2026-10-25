<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-box" :title="instance?.instanceName ?? 'Instance'" />
      </template>

      <!-- Loading Indicator -->
      <LoadingState v-if="loading" message="Loading instance..." />

      <ErrorState v-else-if="failed" message="The instance could not be loaded" @retry="load()" />

      <!-- Instance Not Found -->
      <EmptyState
        v-else-if="!instance"
        icon="bi-exclamation-circle"
        title="Instance Not Found"
        description="The requested instance could not be found."
      />

      <!-- Instance Details -->
      <div v-else class="col-12">
        <RecordingSessionList
          :sessions="sessions"
          :workspaceId="workspaceId!"
          :projectId="projectId!"
          :isCollectorOnly="isCollectorOnly"
          :showInstanceLink="false"
          headerText="Sessions"
          @refresh="() => load(true)"
        />

        <!-- No Sessions Message -->
        <EmptyState
          v-if="sessions.length === 0"
          icon="bi-inbox"
          title="No Sessions"
          description="No recording sessions found for this instance."
        />
      </div>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import RecordingSessionList from '@remote/components/RecordingSessionList.vue';
import ProjectInstanceClient from '@remote/services/api/ProjectInstanceClient';
import RecordingStatus from '@remote/services/api/model/RecordingStatus';
import { usePolling } from '@shared/composables/usePolling';
import ProjectInstance from '@remote/services/api/model/ProjectInstance';
import RecordingSession from '@remote/services/api/model/RecordingSession';
import { useNavigation } from '@/composables/useNavigation';
import '@shared/styles/shared-components.css';

const { workspaceId, projectId, instanceId } = useNavigation();

const instanceClient = new ProjectInstanceClient(workspaceId.value!, projectId.value!);

// Collector-only mode is never active in local mode
const isCollectorOnly = computed(() => {
  return false;
});

const loading = ref(true);
const failed = ref(false);
const instance = ref<ProjectInstance | null>(null);
const sessions = ref<RecordingSession[]>([]);

/**
 * How often an instance with a session still recording is re-read. A session's figures — file
 * sizes above all — come from a listing of the hub's repository directory taken at request time,
 * so without this the page shows whatever the files measured at the moment it was opened, for
 * as long as it stays open.
 */
const ACTIVE_SESSION_REFRESH_MS = 10_000;

const hasActiveSession = (): boolean => {
  return sessions.value.some(session => session.status === RecordingStatus.ACTIVE);
};

// Declared ahead of load because that is what arms and disarms it; the callback reaches the
// function below only when a tick fires, long after setup has run.
const sessionPolling = usePolling(() => load(true), ACTIVE_SESSION_REFRESH_MS);

/**
 * Reads the instance and its sessions (each without its files: the list fetches a session's
 * files when it is expanded) in one call. Background reloads — the poll above and a refresh
 * from the list — leave the loading flag alone so the view on screen never flickers, and keep
 * it when a read fails.
 */
const load = async (background: boolean = false) => {
  if (!background) {
    loading.value = true;
    failed.value = false;
  }
  try {
    const found = await instanceClient.find(instanceId.value!);
    instance.value = found?.instance ?? null;
    sessions.value = found?.sessions ?? [];
  } catch {
    if (!background) {
      failed.value = true;
    }
  } finally {
    loading.value = false;
    // Decided after every load rather than once: a session that finishes while the page is
    // open ends the polling, one that starts begins it, and a removed instance ends it.
    if (hasActiveSession()) {
      sessionPolling.start();
    } else {
      sessionPolling.stop();
    }
  }
};

onMounted(() => load());
</script>
