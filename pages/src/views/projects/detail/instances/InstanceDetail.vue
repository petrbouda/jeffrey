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

      <!-- Sessions Header Bar -->
      <div class="d-flex align-items-center mb-3">
        <div class="sessions-header-bar flex-grow-1 d-flex align-items-center px-3">
          <span class="header-text">Sessions ({{ sessions.length }})</span>
        </div>
      </div>

      <!-- Sessions List -->
      <EmptyState
        v-if="sessions.length === 0"
        icon="bi-inbox"
        title="No Sessions"
        description="No recording sessions found for this instance."
      />

      <div v-else class="session-timeline">
        <div
          v-for="(session, index) in sessions"
          :key="session.id"
          class="session-timeline-item"
          :class="{ 'last-item': index === sessions.length - 1 }"
        >
          <div class="session-dot" :class="session.isActive ? 'active' : 'finished'"></div>
          <div class="session-card" :class="session.isActive ? 'session-card-active' : 'session-card-finished'">
            <div class="d-flex align-items-center">
              <span class="fw-bold session-label">Session #{{ sessions.length - index }}</span>
              <div class="session-times">
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
                <div v-if="session.finishedAt">
                  <i class="bi bi-stopwatch me-1"></i>
                  Duration: {{ formatSessionDuration(session.startedAt, session.finishedAt) }}
                </div>
              </div>
              <div class="session-id-text">{{ session.id }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
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

function formatSessionDuration(startedAt: number, finishedAt: number): string {
  const diffMs = finishedAt - startedAt;
  if (diffMs < 1000) return `${diffMs}ms`;

  const seconds = Math.floor(diffMs / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days > 0) return `${days}d ${hours % 24}h`;
  if (hours > 0) return `${hours}h ${minutes % 60}m`;
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`;
  return `${seconds}s`;
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
.sessions-header-bar {
  background: linear-gradient(135deg, #5e64ff 0%, #4a50e2 100%);
  border: 1px solid #4a50e2;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(94, 100, 255, 0.25);
  height: 31px;
}

.header-text {
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

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

/* Session timeline */
.session-timeline {
  padding-left: 4px;
}

.session-timeline-item {
  position: relative;
  padding-left: 28px;
  padding-bottom: 16px;
}

.session-timeline-item::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #e2e8f0;
}

.session-timeline-item.last-item::before {
  display: none;
}

.session-dot {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid;
}

.session-dot.active {
  border-color: #f59e0b;
  background: #f59e0b;
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.5);
  animation: pulse-dot 2s ease-in-out infinite;
}

.session-dot.finished {
  border-color: #10b981;
  background: #10b981;
}

@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 8px rgba(245, 158, 11, 0.5); }
  50% { box-shadow: 0 0 16px rgba(245, 158, 11, 0.7); }
}

.session-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 14px;
  transition: all 0.2s ease;
}

.session-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.session-card-active {
  border-left: 3px solid #f59e0b;
  background-color: rgba(245, 158, 11, 0.03);
}

.session-card-finished {
  border-left: 3px solid #10b981;
  background-color: rgba(16, 185, 129, 0.03);
}

.session-label {
  font-size: 0.9rem;
  color: #1f2937;
}

.session-times {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
  margin-left: 16px;
  font-size: 0.78rem;
  color: #64748b;
}

.session-times > div {
  display: flex;
  align-items: center;
}

.session-id-text {
  font-size: 0.7rem;
  color: #94a3b8;
  margin-left: auto;
  white-space: nowrap;
}
</style>
