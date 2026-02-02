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
      <!-- Instance Info Row (compact, matching list page style) -->
      <div class="child-row p-3 mb-3 rounded">
        <div class="d-flex align-items-center">
          <span class="status-dot me-3" :class="instance.status === 'ONLINE' ? 'online' : 'offline'"></span>
          <div class="flex-grow-1">
            <div class="fw-bold">
              <i class="bi bi-box me-2 text-secondary"></i>
              {{ instance.hostname }}
              <Badge
                class="ms-2"
                :value="instance.status"
                :variant="instance.status === 'ONLINE' ? 'green' : 'gray'"
                size="xs"
              />
            </div>
            <div class="d-flex text-muted small mt-1 flex-wrap gap-3">
              <div>
                <i class="bi bi-layers me-1"></i>
                {{ instance.sessionCount }} sessions
              </div>
              <div>
                <i class="bi bi-play-circle me-1"></i>
                Started: {{ FormattingService.formatRelativeTime(instance.startedAt) }}
              </div>
              <div>
                <i class="bi bi-hash me-1"></i>
                {{ instance.id }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Sessions Header Bar -->
      <div class="d-flex align-items-center mb-3">
        <div class="sessions-header-bar flex-grow-1 d-flex align-items-center px-3">
          <span class="header-text">Sessions ({{ sessions.length }})</span>
          <div v-if="activeSession" class="ms-auto">
            <Badge value="1 recording" variant="red" size="xs" />
          </div>
        </div>
      </div>

      <!-- Sessions List -->
      <EmptyState
        v-if="sessions.length === 0"
        icon="bi-inbox"
        title="No Sessions"
        description="No recording sessions found for this instance."
      />

      <div v-else>
        <div v-for="session in sessions" :key="session.id"
             class="child-row p-3 mb-2 rounded"
             :class="{ 'active-session-row': session.isActive }">
          <div class="d-flex justify-content-between align-items-center">
            <div>
              <div class="fw-bold">
                <i class="bi bi-file-earmark-play me-2 text-secondary"></i>
                {{ session.id }}
                <Badge
                  v-if="session.isActive"
                  class="ms-2"
                  value="RECORDING"
                  variant="red"
                  size="xs"
                />
              </div>
              <div class="d-flex text-muted small mt-1 flex-wrap gap-3">
                <div>
                  <i class="bi bi-calendar me-1"></i>
                  Started: {{ FormattingService.formatTimestampUTC(session.startedAt) }}
                </div>
                <div v-if="session.finishedAt">
                  <i class="bi bi-calendar-check me-1"></i>
                  Finished: {{ FormattingService.formatTimestampUTC(session.finishedAt) }}
                </div>
                <div v-else-if="session.isActive" class="text-success">
                  <i class="bi bi-record-circle me-1"></i>
                  In progress ({{ FormattingService.formatRelativeTime(session.startedAt) }})
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession';
import FormattingService from '@/services/FormattingService';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId, instanceId } = useNavigation();

const loading = ref(true);
const instance = ref<ProjectInstance | null>(null);
const sessions = ref<ProjectInstanceSession[]>([]);

const activeSession = computed(() => {
  return sessions.value.find(s => s.isActive);
});

onMounted(async () => {
  const client = new ProjectInstanceClient(workspaceId.value!, projectId.value!);

  const [inst, sess] = await Promise.all([
    client.get(instanceId.value!),
    client.getSessions(instanceId.value!)
  ]);

  instance.value = inst || null;
  sessions.value = sess;
  loading.value = false;
});
</script>

<style scoped>
.sessions-header-bar {
  background-color: #f8fafc;
  border-radius: 6px;
  padding: 0.5rem 1rem;
  border: 1px solid #e2e8f0;
}

.header-text {
  font-weight: 600;
  color: #475569;
  font-size: 0.85rem;
}

.active-session-row {
  border-left: 3px solid #22c55e !important;
}

.status-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.online {
  background-color: #22c55e;
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.4);
}

.status-dot.offline {
  background-color: #9ca3af;
}
</style>
