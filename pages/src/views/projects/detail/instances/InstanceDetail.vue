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
      <!-- Compact Metadata Bar -->
      <div class="instance-metadata-bar mb-3">
        <div class="d-flex align-items-center flex-wrap gap-3">
          <div class="d-flex align-items-center">
            <span class="meta-status-dot" :class="instance.status === 'ACTIVE' ? 'active' : 'finished'"></span>
            <Badge
              :value="instance.status"
              :variant="instance.status === 'ACTIVE' ? 'warning' : 'green'"
              size="xs"
            />
          </div>
          <div class="meta-item">
            <i class="bi bi-layers me-1"></i>
            {{ instance.sessionCount }} sessions
          </div>
          <div class="meta-item">
            <i class="bi bi-clock me-1"></i>
            Started {{ FormattingService.formatRelativeTime(instance.startedAt) }}
          </div>
          <div class="meta-item">
            <i class="bi bi-hash me-1"></i>
            {{ instance.id }}
          </div>
        </div>
      </div>

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
import Badge from '@/components/Badge.vue';
import RecordingSessionList from '@/components/RecordingSessionList.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@/services/api/ProjectRepositoryClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import RecordingSession from '@/services/api/model/RecordingSession';
import FormattingService from '@/services/FormattingService';
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

<style scoped>
/* Compact metadata bar */
.instance-metadata-bar {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 16px;
}

.meta-status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-right: 8px;
}

.meta-status-dot.active {
  background-color: #f59e0b;
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.5);
}

.meta-status-dot.finished {
  background-color: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.4);
}

.meta-item {
  font-size: 0.8rem;
  color: #64748b;
}

.meta-item i {
  color: #94a3b8;
}
</style>
