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
        <MainCardHeader icon="bi bi-box" :title="instance?.instanceName ?? 'Instance'">
          <template #actions>
            <button
              class="btn btn-sm btn-outline-secondary"
              title="Read the instance and its sessions again"
              :disabled="loading || refreshing"
              @click="load(true)"
            >
              <i class="bi bi-arrow-clockwise"></i> Refresh
            </button>
          </template>
        </MainCardHeader>
      </template>

      <!-- Figures live in the project tab row; they leave with this page -->
      <Teleport defer :to="PROJECT_NAV_ACTIONS_SELECTOR">
        <span v-if="!loading && instance" class="instance-figures">
          <strong>{{ instance.sessionCount }}</strong>
          {{ instance.sessionCount === 1 ? 'session' : 'sessions' }} ·
          <template v-if="instance.lastDataAt !== null">
            Last data
            <strong :title="FormattingService.formatTimestampUTC(instance.lastDataAt)">{{
              FormattingService.formatTimestampUTCShort(instance.lastDataAt)
            }}</strong>
          </template>
          <template v-else>No data yet</template>
        </span>
      </Teleport>

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
          :sessionsCarryFiles="true"
          headerText="Sessions"
          @refresh="() => load(true)"
        />

        <LoadMoreFooter
          v-if="sessions.length > 0 && hasOlder"
          :shown="sessions.length"
          :total="Math.max(instance.sessionCount, sessions.length + 1)"
          noun="sessions"
          :loading="loadingOlder"
          @loadMore="loadOlder"
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
import LoadMoreFooter from '@shared/components/LoadMoreFooter.vue';
import { ToastService } from '@shared/services/ToastService';
import FormattingService from '@shared/services/FormattingService';
import EmptyState from '@shared/components/EmptyState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import RecordingSessionList from '@remote/components/RecordingSessionList.vue';
import ProjectInstanceClient from '@remote/services/api/ProjectInstanceClient';
import ProjectInstance from '@remote/services/api/model/ProjectInstance';
import RecordingSession from '@remote/services/api/model/RecordingSession';
import { useNavigation } from '@/composables/useNavigation';
import { PROJECT_NAV_ACTIONS_SELECTOR } from '../projectNav';
import '@shared/styles/shared-components.css';

const { workspaceId, projectId, instanceId } = useNavigation();
const toast = ToastService;

const instanceClient = new ProjectInstanceClient(workspaceId.value!, projectId.value!);

// Collector-only mode is never active in local mode
const isCollectorOnly = computed(() => {
  return false;
});

const loading = ref(true);
const refreshing = ref(false);
const loadingOlder = ref(false);
const hasOlder = ref(false);
const failed = ref(false);
const instance = ref<ProjectInstance | null>(null);
const sessions = ref<RecordingSession[]>([]);

/**
 * Reads the instance and its sessions (each without its files: the list fetches a session's
 * files when it is expanded) in one call. The page reads them when it opens and again only when
 * asked — the Refresh button, or the list after a change — never on a timer: on an Azure file
 * share every read is a run of round-trips, so a figure that changes while the page stays open
 * (a recording session's sizes) waits for a refresh. A refresh leaves the loading flag alone so
 * the view on screen never flickers, and keeps it when a read fails.
 */
const load = async (background: boolean = false) => {
  if (background) {
    refreshing.value = true;
  } else {
    loading.value = true;
    failed.value = false;
  }
  try {
    const found = await instanceClient.find(instanceId.value!);
    instance.value = found?.instance ?? null;
    sessions.value = found?.sessions ?? [];
    hasOlder.value = found?.hasOlder ?? false;
  } catch {
    if (!background) {
      failed.value = true;
    }
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
};

/**
 * Reads the next page of older sessions, after the oldest on screen, and adds it below. A refresh
 * reads the newest page again and starts over from there.
 */
const loadOlder = async () => {
  const oldest = sessions.value[sessions.value.length - 1];
  if (!oldest || loadingOlder.value) {
    return;
  }
  loadingOlder.value = true;
  try {
    const older = await instanceClient.find(instanceId.value!, oldest.id);
    if (older) {
      sessions.value = [...sessions.value, ...older.sessions];
      hasOlder.value = older.hasOlder;
    }
  } catch {
    toast.error('Older sessions', 'They could not be read from the remote.');
  } finally {
    loadingOlder.value = false;
  }
};

onMounted(() => load());
</script>

<style scoped>
.instance-figures {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.instance-figures strong {
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
}
</style>
