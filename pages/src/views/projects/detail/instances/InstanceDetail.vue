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
      <!-- Instance Info Card -->
      <div class="instance-info-card mb-4">
        <div class="d-flex align-items-center mb-3">
          <span class="status-dot me-3" :class="instance.status === 'ONLINE' ? 'online' : 'offline'"></span>
          <div>
            <h5 class="mb-0">
              {{ instance.hostname }}
              <Badge
                class="ms-2"
                :value="instance.status"
                :variant="instance.status === 'ONLINE' ? 'green' : 'gray'"
                size="sm"
              />
            </h5>
          </div>
        </div>

        <div class="row g-3">
          <div class="col-md-3">
            <div class="info-item">
              <i class="bi bi-layers me-2"></i>
              <span class="label">Sessions:</span>
              <span class="value">{{ instance.sessionCount }}</span>
            </div>
          </div>
          <div class="col-md-3">
            <div class="info-item">
              <i class="bi bi-clock me-2"></i>
              <span class="label">Last Heartbeat:</span>
              <span class="value">{{ formatTimeAgo(instance.lastHeartbeat) }}</span>
            </div>
          </div>
          <div class="col-md-3">
            <div class="info-item">
              <i class="bi bi-play-circle me-2"></i>
              <span class="label">Started:</span>
              <span class="value">{{ formatTimeAgo(instance.startedAt) }}</span>
            </div>
          </div>
          <div class="col-md-3">
            <div class="info-item">
              <i class="bi bi-hash me-2"></i>
              <span class="label">Instance ID:</span>
              <span class="value text-muted">{{ instance.id }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Active Session Panel (if any) -->
      <div v-if="activeSession" class="active-session-panel mb-4">
        <div class="panel-header">
          <i class="bi bi-record-circle text-danger me-2"></i>
          <span>Active Session</span>
        </div>
        <div class="panel-body">
          <div class="d-flex justify-content-between align-items-center">
            <div>
              <div class="fw-bold">{{ activeSession.id }}</div>
              <div class="text-muted small">
                Started {{ formatTimeAgo(activeSession.startedAt) }}
              </div>
            </div>
            <Badge value="RECORDING" variant="red" size="sm" />
          </div>
        </div>
      </div>

      <!-- Sessions History -->
      <div class="sessions-section">
        <div class="section-header mb-3">
          <i class="bi bi-clock-history me-2"></i>
          <span>Session History ({{ sessions.length }})</span>
        </div>

        <EmptyState
          v-if="sessions.length === 0"
          icon="bi-inbox"
          title="No Sessions"
          description="No recording sessions found for this instance."
        />

        <div v-else>
          <div v-for="session in sessions" :key="session.id"
               class="session-row mb-2">
            <div class="d-flex justify-content-between align-items-center">
              <div>
                <div class="fw-bold">
                  <i class="bi bi-file-earmark-play me-2"></i>
                  {{ session.id }}
                  <Badge
                    v-if="session.isActive"
                    class="ms-2"
                    value="Active"
                    variant="green"
                    size="xs"
                  />
                </div>
                <div class="text-muted small mt-1">
                  <span class="me-3">
                    <i class="bi bi-calendar me-1"></i>
                    Started: {{ formatDateTime(session.startedAt) }}
                  </span>
                  <span v-if="session.finishedAt">
                    <i class="bi bi-calendar-check me-1"></i>
                    Finished: {{ formatDateTime(session.finishedAt) }}
                  </span>
                  <span v-else class="text-success">
                    <i class="bi bi-record-circle me-1"></i>
                    In progress
                  </span>
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
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId, instanceId } = useNavigation();

const loading = ref(true);
const instance = ref<ProjectInstance | null>(null);
const sessions = ref<ProjectInstanceSession[]>([]);

const activeSession = computed(() => {
  return sessions.value.find(s => s.isActive);
});

function formatTimeAgo(timestamp: number): string {
  const seconds = Math.floor((Date.now() - timestamp) / 1000);

  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

function formatDateTime(timestamp: number): string {
  return new Date(timestamp).toLocaleString();
}

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
.instance-info-card {
  background-color: #f8fafc;
  border-radius: 8px;
  padding: 1.25rem;
  border: 1px solid #e2e8f0;
}

.info-item {
  display: flex;
  align-items: center;
  font-size: 0.875rem;
}

.info-item .label {
  color: #64748b;
  margin-right: 0.5rem;
}

.info-item .value {
  font-weight: 500;
  color: #334155;
}

.active-session-panel {
  background: linear-gradient(135deg, #fef2f2 0%, #fff5f5 100%);
  border: 1px solid #fecaca;
  border-radius: 8px;
  overflow: hidden;
}

.active-session-panel .panel-header {
  background-color: rgba(220, 38, 38, 0.1);
  padding: 0.75rem 1rem;
  font-weight: 600;
  color: #dc2626;
  font-size: 0.875rem;
}

.active-session-panel .panel-body {
  padding: 1rem;
}

.sessions-section .section-header {
  font-weight: 600;
  color: #475569;
  font-size: 0.95rem;
}

.session-row {
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 0.875rem 1rem;
  transition: all 0.2s ease;
}

.session-row:hover {
  border-color: #cbd5e1;
  background-color: #f8fafc;
}

.status-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.online {
  background-color: #22c55e;
  box-shadow: 0 0 10px rgba(34, 197, 94, 0.5);
}

.status-dot.offline {
  background-color: #9ca3af;
}
</style>
