<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-box" :title="instance?.instanceName ?? 'Instance'" />
      </template>

      <!-- Loading Indicator -->
      <LoadingState v-if="loading" message="Loading instance..." />

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
          :hubId="hubId!"
          :workspaceId="workspaceId!"
          :projectId="projectId!"
          :isRemoteWorkspace="isRemoteWorkspace"
          :isCollectorOnly="isCollectorOnly"
          :showInstanceLink="false"
          headerText="Sessions"
          @refresh="() => fetchSessions()"
        />

        <!-- No Sessions Message -->
        <EmptyState
          v-if="!sessionsLoading && sessions.length === 0"
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
import EmptyState from '@shared/components/EmptyState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import RecordingSessionList from '@hubs/components/RecordingSessionList.vue';
import ProjectInstanceClient from '@hubs/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@hubs/services/api/ProjectRepositoryClient';
import RecordingStatus from '@hubs/services/api/model/RecordingStatus';
import { usePolling } from '@shared/composables/usePolling';
import ProjectInstance from '@hubs/services/api/model/ProjectInstance';
import RecordingSession from '@hubs/services/api/model/RecordingSession';
import { useNavigation } from '@/composables/useNavigation';
import '@shared/styles/shared-components.css';

const { hubId, workspaceId, projectId, instanceId } = useNavigation();

// Collector-only mode is never active in local mode
const isCollectorOnly = computed(() => {
  return false;
});

const loading = ref(true);
const sessionsLoading = ref(true);
const instance = ref<ProjectInstance | null>(null);
const sessions = ref<RecordingSession[]>([]);

// Always remote in local mode
const isRemoteWorkspace = computed(() => {
  return true;
});

/**
 * How often a session that is still recording is re-read. A session's figures — file sizes
 * above all — come from a listing of the hub's repository directory taken at request time,
 * so without this the page shows whatever the files measured at the moment it was opened,
 * for as long as it stays open.
 */
const ACTIVE_SESSION_REFRESH_MS = 10_000;

const hasActiveSession = (): boolean => {
  return sessions.value.some(session => session.status === RecordingStatus.ACTIVE);
};

// Declared ahead of fetchSessions because that is what arms and disarms it; the callback
// reaches the function below only when a tick fires, long after setup has run.
const sessionPolling = usePolling(() => fetchSessions(true), ACTIVE_SESSION_REFRESH_MS);

/**
 * Loads this instance's sessions. Background reloads (the poll above) leave the loading
 * flag alone so a refresh never flickers the view that is already on screen.
 */
const fetchSessions = async (background: boolean = false) => {
  if (!background) {
    sessionsLoading.value = true;
  }
  try {
    const repositoryService = new ProjectRepositoryClient(
      hubId.value,
      workspaceId.value!,
      projectId.value!
    );
    const allSessions = await repositoryService.listRecordingSessions();
    sessions.value = allSessions.filter(s => s.instanceId === instanceId.value);
  } catch (error: any) {
    if (error.response?.status === 404) {
      sessions.value = [];
    }
  } finally {
    if (!background) {
      sessionsLoading.value = false;
    }
    // Decided after every load rather than once: a session that finishes while the page is
    // open ends the polling, and one that starts begins it.
    if (hasActiveSession()) {
      sessionPolling.start();
    } else {
      sessionPolling.stop();
    }
  }
};

onMounted(async () => {
  const client = new ProjectInstanceClient(hubId.value, workspaceId.value!, projectId.value!);

  const [inst] = await Promise.all([client.find(instanceId.value!), fetchSessions()]);

  instance.value = inst || null;
  loading.value = false;
});
</script>
