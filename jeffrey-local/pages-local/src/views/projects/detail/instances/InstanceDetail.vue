<template>
  <PageHeader
    :title="instance?.instanceName || 'Instance'"
    description="View instance details and recording sessions"
    icon="bi-box"
  >
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
  </PageHeader>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import RecordingSessionList from '@/components/RecordingSessionList.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@/services/api/ProjectRepositoryClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import RecordingSession from '@/services/api/model/RecordingSession';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId, instanceId } = useNavigation();

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
    const repositoryService = new ProjectRepositoryClient(workspaceId.value!, projectId.value!);
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
  const client = new ProjectInstanceClient(workspaceId.value!, projectId.value!);

  const [inst] = await Promise.all([client.find(instanceId.value!), fetchSessions()]);

  instance.value = inst || null;
  loading.value = false;
});
</script>
