<template>
  <PageHeader
    :title="instance?.hostname || 'Instance'"
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
import {computed, onMounted, ref} from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import RecordingSessionList from '@/components/RecordingSessionList.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@/services/api/ProjectRepositoryClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import RecordingSession from '@/services/api/model/RecordingSession';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import Workspace from '@/services/api/model/Workspace';
import WorkspaceType from '@/services/api/model/WorkspaceType';
import {useNavigation} from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const {workspaceId, projectId, instanceId} = useNavigation();

const loading = ref(true);
const sessionsLoading = ref(true);
const instance = ref<ProjectInstance | null>(null);
const sessions = ref<RecordingSession[]>([]);
const workspaceInfo = ref<Workspace | null>(null);

const isRemoteWorkspace = computed(() => {
  return workspaceInfo.value?.type === WorkspaceType.REMOTE;
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

const fetchWorkspaceInfo = async () => {
  try {
    const workspaces = await WorkspaceClient.list();
    workspaceInfo.value = workspaces.find(w => w.id === workspaceId.value) || null;
  } catch (error: any) {
    console.error('Failed to load workspace info:', error);
  }
};

onMounted(async () => {
  const client = new ProjectInstanceClient(workspaceId.value!, projectId.value!);

  const [inst] = await Promise.all([
    client.get(instanceId.value!),
    fetchSessions(),
    fetchWorkspaceInfo(),
  ]);

  instance.value = inst || null;
  loading.value = false;
});
</script>

