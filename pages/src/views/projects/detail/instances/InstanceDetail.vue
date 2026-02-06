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
      <!-- Instance Metadata Bar -->
      <div class="instance-metadata-bar mb-3">
        <!-- Top row: status + key info -->
        <div class="meta-row-top">
          <div class="d-flex align-items-center">
            <span class="meta-status-dot" :class="instance.status === 'ACTIVE' ? 'active' : 'finished'"></span>
            <Badge
              :value="instance.status"
              :variant="instance.status === 'ACTIVE' ? 'warning' : 'green'"
              size="xs"
            />
          </div>
          <div class="meta-pill">
            <i class="bi bi-layers me-1"></i>{{ instance.sessionCount }} sessions
          </div>
          <div class="meta-pill meta-pill-id">
            <i class="bi bi-hash me-1"></i>{{ instance.id }}
          </div>
        </div>

        <!-- Bottom row: timeline -->
        <div class="meta-row-timeline">
          <div class="timeline-item">
            <div class="timeline-label"><i class="bi bi-play-circle me-1"></i>Started</div>
            <div class="timeline-relative">{{ FormattingService.formatRelativeTime(instance.startedAt) }}</div>
            <div class="timeline-timestamp">{{ FormattingService.formatTimestampUTC(instance.startedAt) }}</div>
          </div>
          <div class="timeline-item" v-if="instance.finishedAt">
            <div class="timeline-label"><i class="bi bi-stop-circle me-1"></i>Finished</div>
            <div class="timeline-relative">{{ FormattingService.formatRelativeTime(instance.finishedAt) }}</div>
            <div class="timeline-timestamp">{{ FormattingService.formatTimestampUTC(instance.finishedAt) }}</div>
          </div>
          <div class="timeline-item" v-if="instance.finishedAt">
            <div class="timeline-label"><i class="bi bi-hourglass-split me-1"></i>Duration</div>
            <div class="timeline-relative">{{ FormattingService.formatDurationFromMillis(instance.startedAt, instance.finishedAt) }}</div>
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
.instance-metadata-bar {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 10px;
  padding: 0;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

/* Top row */
.meta-row-top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.meta-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-right: 6px;
}

.meta-status-dot.active {
  background-color: #f59e0b;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.5);
}

.meta-status-dot.finished {
  background-color: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.4);
}

.meta-pill {
  font-size: 0.75rem;
  font-weight: 500;
  color: #64748b;
  background: #f1f5f9;
  padding: 2px 10px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
}

.meta-pill i {
  color: #94a3b8;
  font-size: 0.7rem;
}

.meta-pill-id {
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  font-size: 0.7rem;
  letter-spacing: 0.02em;
  color: #94a3b8;
}

/* Timeline row */
.meta-row-timeline {
  display: flex;
  gap: 0;
  padding: 0;
}

.timeline-item {
  flex: 1;
  padding: 10px 16px;
  position: relative;
}

.timeline-item + .timeline-item {
  border-left: 1px solid rgba(0, 0, 0, 0.05);
}

.timeline-label {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #94a3b8;
  margin-bottom: 2px;
}

.timeline-label i {
  font-size: 0.6rem;
}

.timeline-relative {
  font-size: 0.85rem;
  font-weight: 600;
  color: #374151;
  line-height: 1.3;
}

.timeline-timestamp {
  font-size: 0.7rem;
  color: #94a3b8;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  margin-top: 1px;
}
</style>
