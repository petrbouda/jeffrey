<template>
  <PageHeader
    title="Instance Timeline"
    description="Visual timeline showing when instances were active. Track the lifecycle of your application instances over time."
    icon="bi-bar-chart-steps"
  >
    <!-- Time Range Selector -->
    <div class="d-flex gap-3 mb-3 align-items-center">
      <div class="btn-group" role="group">
        <button
          v-for="range in timeRanges"
          :key="range.value"
          type="button"
          class="btn btn-sm"
          :class="selectedRange === range.value ? 'btn-primary' : 'btn-outline-secondary'"
          @click="selectedRange = range.value"
        >
          {{ range.label }}
        </button>
      </div>
    </div>

    <!-- Instances Header Bar -->
    <div class="col-12" v-if="!loading">
      <SectionHeaderBar :text="`Instances (${instances.length})`" />
    </div>

    <!-- Loading Indicator -->
    <LoadingState v-if="loading" message="Loading timeline..." />

    <!-- Empty State -->
    <EmptyState
      v-else-if="instances.length === 0"
      icon="bi-bar-chart-steps"
      title="No Instances"
      description="No application instances found for this project."
    />

    <!-- Timeline Visualization -->
    <div v-else class="col-12">
      <!-- Timeline Header -->
      <div class="timeline-header mb-3">
        <div class="timeline-scale">
          <span v-for="tick in timelineTicks" :key="tick" class="tick">{{ tick }}</span>
        </div>
      </div>

      <!-- Instance Rows -->
      <div class="timeline-container">
        <!-- Grid Lines -->
        <div class="timeline-grid-lines">
          <div
            v-for="(_, index) in timelineTicks"
            :key="'grid-' + index"
            class="grid-line"
            :style="{ left: (index / (timelineTicks.length - 1) * 100) + '%' }"
          ></div>
        </div>

        <div v-for="instance in instances" :key="instance.id" class="timeline-row">
          <div class="instance-label">
            <span class="status-dot" :class="instance.status === 'ACTIVE' ? 'active' : 'finished'"></span>
            <router-link
              :to="generateInstanceUrl(instance.id)"
              class="hostname-link"
            >{{ truncateHostname(instance.hostname) }}</router-link>
            <Badge
              :value="instance.sessionCount"
              size="xxs"
              variant="grey"
              :uppercase="false"
            />
          </div>
          <div
            class="instance-bar-container"
            @mouseenter="showInstanceTooltip($event, instance)"
            @mousemove="updateTooltipPosition($event)"
            @mouseleave="hideTooltip"
          >
            <!-- Instance background bar (faint) -->
            <div
              class="instance-bg-bar"
              :class="instance.status === 'ACTIVE' ? 'active' : 'finished'"
              :style="getBarStyle(instance)"
            ></div>

            <!-- Session bars (solid, overlaid) -->
            <template v-if="!sessionsLoading && instanceSessions.has(instance.id)">
              <div
                v-for="session in getSessionsForInstance(instance.id)"
                :key="session.id"
                class="session-bar"
                :class="session.isActive ? 'active' : 'finished'"
                :style="getSessionBarStyle(session)"
                @mouseenter.stop="showSessionTooltip($event, session, instance.id)"
                @mousemove.stop="updateTooltipPosition($event)"
                @mouseleave.stop="hideTooltip"
              ></div>
            </template>

            <!-- Shimmer while sessions load -->
            <div
              v-if="sessionsLoading"
              class="session-shimmer"
              :style="getBarStyle(instance)"
            ></div>
          </div>
        </div>
      </div>

      <!-- Legend -->
      <div class="timeline-legend mt-4">
        <div class="legend-item">
          <span class="legend-bar instance-bg active"></span>
          <span>Active Instance</span>
        </div>
        <div class="legend-item">
          <span class="legend-bar instance-bg finished"></span>
          <span>Finished Instance</span>
        </div>
        <div class="legend-item">
          <span class="legend-bar session active"></span>
          <span>Active Session</span>
        </div>
        <div class="legend-item">
          <span class="legend-bar session finished"></span>
          <span>Finished Session</span>
        </div>
        <div class="legend-item">
          <span class="legend-bar gap"></span>
          <span>Gap (No Recording)</span>
        </div>
      </div>
    </div>

    <!-- Instance Tooltip (teleported to body) -->
    <Teleport to="body">
      <div
        v-if="tooltipType === 'instance' && hoveredInstance"
        class="timeline-tooltip-container"
        :style="{ left: tooltipPosition.x + 'px', top: tooltipPosition.y + 'px' }"
        @mouseenter="cancelHideTooltip"
        @mouseleave="hideTooltip"
      >
        <div class="timeline-tooltip-header">
          <span class="timeline-tooltip-hostname">{{ hoveredInstance.hostname }}</span>
          <Badge
            :value="hoveredInstance.status === 'ACTIVE' ? 'Active' : 'Finished'"
            size="xxs"
            :variant="hoveredInstance.status === 'ACTIVE' ? 'orange' : 'green'"
          />
        </div>
        <div class="timeline-tooltip-body">
          <div class="timeline-tooltip-row">
            <span class="timeline-tooltip-label">Started</span>
            <span class="timeline-tooltip-value">
              {{ FormattingService.formatRelativeTime(hoveredInstance.startedAt) }}
              <span class="timeline-tooltip-utc">{{ FormattingService.formatTimestampUTC(hoveredInstance.startedAt) }}</span>
            </span>
          </div>
          <div class="timeline-tooltip-row">
            <span class="timeline-tooltip-label">Finished</span>
            <span class="timeline-tooltip-value">
              <template v-if="hoveredInstance.finishedAt">
                {{ FormattingService.formatRelativeTime(hoveredInstance.finishedAt) }}
                <span class="timeline-tooltip-utc">{{ FormattingService.formatTimestampUTC(hoveredInstance.finishedAt) }}</span>
              </template>
              <template v-else>Running</template>
            </span>
          </div>
          <div class="timeline-tooltip-row">
            <span class="timeline-tooltip-label">Duration</span>
            <span class="timeline-tooltip-value">
              {{ hoveredInstance.finishedAt
                ? FormattingService.formatDurationFromMillis(hoveredInstance.startedAt, hoveredInstance.finishedAt)
                : FormattingService.formatDurationFromMillis(hoveredInstance.startedAt, Date.now())
              }}
            </span>
          </div>
          <div class="timeline-tooltip-row">
            <span class="timeline-tooltip-label">Sessions</span>
            <span class="timeline-tooltip-value">
              {{ hoveredInstance.sessionCount }}
              <span v-if="instanceSessions.has(hoveredInstance.id)" class="timeline-tooltip-utc">
                ({{ getSessionBreakdown(hoveredInstance.id) }})
              </span>
            </span>
          </div>
        </div>
        <router-link :to="generateInstanceUrl(hoveredInstance.id)" class="timeline-tooltip-link">
          View Details &rarr;
        </router-link>
      </div>
    </Teleport>

    <!-- Session Tooltip (teleported to body) -->
    <Teleport to="body">
      <div
        v-if="tooltipType === 'session' && hoveredSession"
        class="timeline-tooltip-container"
        :style="{ left: tooltipPosition.x + 'px', top: tooltipPosition.y + 'px' }"
        @mouseenter="cancelHideTooltip"
        @mouseleave="hideTooltip"
      >
        <div class="timeline-tooltip-header">
          <span class="timeline-tooltip-hostname">Session</span>
          <Badge
            :value="hoveredSession.session.isActive ? 'Active' : 'Finished'"
            size="xxs"
            :variant="hoveredSession.session.isActive ? 'orange' : 'green'"
          />
        </div>
        <div class="timeline-tooltip-body">
          <div class="timeline-tooltip-row">
            <span class="timeline-tooltip-label">Started</span>
            <span class="timeline-tooltip-value">
              {{ FormattingService.formatRelativeTime(hoveredSession.session.startedAt) }}
              <span class="timeline-tooltip-utc">{{ FormattingService.formatTimestampUTC(hoveredSession.session.startedAt) }}</span>
            </span>
          </div>
          <div class="timeline-tooltip-row">
            <span class="timeline-tooltip-label">Finished</span>
            <span class="timeline-tooltip-value">
              <template v-if="hoveredSession.session.finishedAt">
                {{ FormattingService.formatRelativeTime(hoveredSession.session.finishedAt) }}
                <span class="timeline-tooltip-utc">{{ FormattingService.formatTimestampUTC(hoveredSession.session.finishedAt) }}</span>
              </template>
              <template v-else>Running</template>
            </span>
          </div>
          <div class="timeline-tooltip-row">
            <span class="timeline-tooltip-label">Duration</span>
            <span class="timeline-tooltip-value">
              {{ hoveredSession.session.finishedAt
                ? FormattingService.formatDurationFromMillis(hoveredSession.session.startedAt, hoveredSession.session.finishedAt)
                : FormattingService.formatDurationFromMillis(hoveredSession.session.startedAt, Date.now())
              }}
            </span>
          </div>
        </div>
      </div>
    </Teleport>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import SectionHeaderBar from '@/components/SectionHeaderBar.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession';
import FormattingService from '@/services/FormattingService';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId, generateInstanceUrl } = useNavigation();

const timeRanges = [
  { label: '1H', value: '1h' },
  { label: '6H', value: '6h' },
  { label: '24H', value: '24h' },
  { label: '7D', value: '7d' },
  { label: '30D', value: '30d' }
];

const loading = ref(true);
const sessionsLoading = ref(false);
const selectedRange = ref('24h');
const instances = ref<ProjectInstance[]>([]);
const instanceSessions = ref<Map<string, ProjectInstanceSession[]>>(new Map());

const hoveredInstance = ref<ProjectInstance | null>(null);
const hoveredSession = ref<{ session: ProjectInstanceSession; instanceId: string } | null>(null);
const tooltipPosition = ref<{ x: number; y: number }>({ x: 0, y: 0 });
const tooltipType = ref<'instance' | 'session' | null>(null);
let tooltipHideTimeout: number | null = null;

const timelineTicks = computed(() => {
  switch (selectedRange.value) {
    case '1h':
      return ['Now', '-15m', '-30m', '-45m', '-1h'];
    case '6h':
      return ['Now', '-1h', '-2h', '-3h', '-4h', '-5h', '-6h'];
    case '24h':
      return ['Now', '-6h', '-12h', '-18h', '-24h'];
    case '7d':
      return ['Today', '-1d', '-2d', '-3d', '-4d', '-5d', '-6d', '-7d'];
    case '30d':
      return ['Today', '-1w', '-2w', '-3w', '-4w'];
    default:
      return ['Now', '-6h', '-12h', '-18h', '-24h'];
  }
});

function truncateHostname(hostname: string): string {
  if (hostname.length > 20) {
    return hostname.substring(0, 17) + '...';
  }
  return hostname;
}

function getBarStyle(instance: ProjectInstance): Record<string, string> {
  const now = Date.now();
  const rangeMs = getRangeMs();

  const startPercent = Math.max(0, Math.min((now - instance.startedAt) / rangeMs * 100, 100));

  const endPercent = (instance.status === 'ACTIVE' || !instance.finishedAt)
    ? 0
    : Math.max(0, Math.min((now - instance.finishedAt) / rangeMs * 100, 100));

  const left = endPercent;
  const width = Math.max(startPercent - endPercent, 0.5);

  return {
    left: `${left}%`,
    width: `${Math.min(width, 100 - left)}%`
  };
}

function getSessionBarStyle(session: ProjectInstanceSession): Record<string, string> {
  const now = Date.now();
  const rangeMs = getRangeMs();

  const startPercent = Math.max(0, Math.min((now - session.startedAt) / rangeMs * 100, 100));

  const endPercent = (session.isActive || !session.finishedAt)
    ? 0
    : Math.max(0, Math.min((now - session.finishedAt) / rangeMs * 100, 100));

  const left = endPercent;
  const width = Math.max(startPercent - endPercent, 0.3);

  return {
    left: `${left}%`,
    width: `${Math.min(width, 100 - left)}%`
  };
}

function getRangeMs(): number {
  switch (selectedRange.value) {
    case '1h': return 3600000;
    case '6h': return 21600000;
    case '24h': return 86400000;
    case '7d': return 604800000;
    case '30d': return 2592000000;
    default: return 86400000;
  }
}

function getSessionsForInstance(instanceId: string): ProjectInstanceSession[] {
  return instanceSessions.value.get(instanceId) ?? [];
}

function getSessionBreakdown(instanceId: string): string {
  const sessions = getSessionsForInstance(instanceId);
  const active = sessions.filter(s => s.isActive).length;
  const finished = sessions.filter(s => !s.isActive).length;
  const parts: string[] = [];
  if (finished > 0) parts.push(`${finished} finished`);
  if (active > 0) parts.push(`${active} active`);
  return parts.join(', ');
}

function showInstanceTooltip(event: MouseEvent, instance: ProjectInstance) {
  cancelHideTooltip();
  hoveredInstance.value = instance;
  hoveredSession.value = null;
  tooltipType.value = 'instance';
  updateTooltipPosition(event);
}

function showSessionTooltip(event: MouseEvent, session: ProjectInstanceSession, instanceId: string) {
  cancelHideTooltip();
  hoveredSession.value = { session, instanceId };
  hoveredInstance.value = null;
  tooltipType.value = 'session';
  updateTooltipPosition(event);
}

function updateTooltipPosition(event: MouseEvent) {
  tooltipPosition.value = { x: event.clientX + 12, y: event.clientY - 10 };
}

function hideTooltip() {
  tooltipHideTimeout = window.setTimeout(() => {
    tooltipType.value = null;
    hoveredInstance.value = null;
    hoveredSession.value = null;
  }, 150);
}

function cancelHideTooltip() {
  if (tooltipHideTimeout !== null) {
    clearTimeout(tooltipHideTimeout);
    tooltipHideTimeout = null;
  }
}

async function loadAllSessions(client: ProjectInstanceClient) {
  sessionsLoading.value = true;
  const entries = await Promise.all(
    instances.value.map(async (instance) => {
      const sessions = await client.getSessions(instance.id);
      return [instance.id, sessions] as [string, ProjectInstanceSession[]];
    })
  );
  instanceSessions.value = new Map(entries);
  sessionsLoading.value = false;
}

onMounted(async () => {
  const client = new ProjectInstanceClient(workspaceId.value!, projectId.value!);
  instances.value = await client.list();
  loading.value = false;
  if (instances.value.length > 0) {
    loadAllSessions(client);
  }
});
</script>

<style scoped>
.timeline-header {
  padding-left: 170px;
}

.timeline-scale {
  display: flex;
  justify-content: space-between;
  color: #64748b;
  font-size: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #e2e8f0;
}

.timeline-container {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  position: relative;
}

.timeline-grid-lines {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 170px;
  right: 0;
  pointer-events: none;
  z-index: 0;
}

.grid-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: rgba(0, 0, 0, 0.06);
}

.timeline-row {
  display: flex;
  align-items: center;
  height: 52px;
  border-radius: 4px;
  transition: background-color 0.15s ease;
  position: relative;
  z-index: 1;
}

.timeline-row:hover {
  background-color: rgba(94, 100, 255, 0.03);
}

.instance-label {
  width: 160px;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding-right: 0.75rem;
  flex-shrink: 0;
}

.hostname-link {
  font-size: 0.8rem;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-decoration: none;
  cursor: pointer;
  transition: color 0.15s ease;
}

.hostname-link:hover {
  color: #5e64ff;
  text-decoration: underline;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.active {
  background-color: #f59e0b;
}

.status-dot.finished {
  background-color: #10b981;
}

.instance-bar-container {
  flex: 1;
  height: 32px;
  background-color: #f1f5f9;
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

/* Instance background bar (faint) */
.instance-bg-bar {
  position: absolute;
  height: 100%;
  border-radius: 4px;
  min-width: 8px;
  z-index: 1;
}

.instance-bg-bar.active {
  background: rgba(245, 158, 11, 0.15);
  border: 1px solid rgba(245, 158, 11, 0.3);
  animation: instance-pulse-active 3s ease-in-out infinite;
}

.instance-bg-bar.finished {
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.25);
}

/* Session bars (solid, overlaid on instance bg) */
.session-bar {
  position: absolute;
  height: 60%;
  top: 20%;
  border-radius: 3px;
  min-width: 4px;
  z-index: 2;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.session-bar:hover {
  opacity: 0.85;
}

.session-bar.active {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  animation: session-pulse-active 2s ease-in-out infinite;
}

.session-bar.finished {
  background: linear-gradient(135deg, #10b981, #059669);
}

/* Shimmer effect while sessions load */
.session-shimmer {
  position: absolute;
  height: 60%;
  top: 20%;
  border-radius: 3px;
  z-index: 2;
  background: linear-gradient(90deg, rgba(200, 200, 200, 0.2) 25%, rgba(200, 200, 200, 0.4) 50%, rgba(200, 200, 200, 0.2) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@keyframes session-pulse-active {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
  50% { box-shadow: 0 0 6px 2px rgba(245, 158, 11, 0.25); }
}

@keyframes instance-pulse-active {
  0%, 100% { border-color: rgba(245, 158, 11, 0.3); }
  50% { border-color: rgba(245, 158, 11, 0.55); }
}

/* Legend */
.timeline-legend {
  display: flex;
  gap: 1.5rem;
  padding-left: 170px;
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  color: #64748b;
}

.legend-bar {
  width: 20px;
  height: 12px;
  border-radius: 3px;
}

.legend-bar.instance-bg.active {
  background: rgba(245, 158, 11, 0.15);
  border: 1px solid rgba(245, 158, 11, 0.4);
}

.legend-bar.instance-bg.finished {
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.35);
}

.legend-bar.session.active {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.legend-bar.session.finished {
  background: linear-gradient(135deg, #10b981, #059669);
}

.legend-bar.gap {
  background: #f1f5f9;
  border: 1px dashed #cbd5e1;
}

.btn-group .btn {
  font-size: 0.75rem;
  padding: 0.25rem 0.75rem;
}
</style>

<!-- Non-scoped styles for teleported tooltip -->
<style>
.timeline-tooltip-container {
  position: fixed;
  z-index: 9999;
  background: #1f2937;
  color: #f9fafb;
  border-radius: 8px;
  padding: 0;
  min-width: 260px;
  max-width: 340px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
  pointer-events: auto;
  overflow: hidden;
  font-size: 0.78rem;
}

.timeline-tooltip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.6rem 0.75rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  gap: 0.5rem;
}

.timeline-tooltip-hostname {
  font-weight: 600;
  font-size: 0.82rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.timeline-tooltip-body {
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.timeline-tooltip-row {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
}

.timeline-tooltip-label {
  color: #9ca3af;
  flex-shrink: 0;
}

.timeline-tooltip-value {
  text-align: right;
  font-weight: 500;
}

.timeline-tooltip-utc {
  display: block;
  color: #6b7280;
  font-size: 0.68rem;
  font-weight: 400;
}

.timeline-tooltip-link {
  display: block;
  padding: 0.5rem 0.75rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  color: #60a5fa;
  text-decoration: none;
  font-size: 0.75rem;
  font-weight: 500;
  transition: background 0.15s ease;
}

.timeline-tooltip-link:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #93bbfd;
}
</style>
