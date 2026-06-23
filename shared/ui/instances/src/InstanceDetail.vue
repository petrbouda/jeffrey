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
          @refresh="fetchSessions"
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
import RecordingSessionList from '@workspaces/components/RecordingSessionList.vue';
import ProjectInstanceClient from '@workspaces/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@workspaces/services/api/ProjectRepositoryClient';
import ProjectInstance from '@workspaces/services/api/model/ProjectInstance';
import RecordingSession from '@workspaces/services/api/model/RecordingSession';
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

const fetchSessions = async () => {
  sessionsLoading.value = true;
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
    sessionsLoading.value = false;
  }
};

onMounted(async () => {
  const client = new ProjectInstanceClient(hubId.value, workspaceId.value!, projectId.value!);

  const [inst] = await Promise.all([client.find(instanceId.value!), fetchSessions()]);

  instance.value = inst || null;
  loading.value = false;
});
</script>
