<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-bar-chart-steps" title="Instance Timeline" />
      </template>

      <!-- Toolbar -->
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
        <span v-if="!loading" class="timeline-legend">
          <span class="legend-chip">
            <span class="legend-sw finished-strong"></span>
            <span class="legend-sw finished-light"></span>
            adjacent finished
          </span>
          <span class="legend-chip">
            <span class="legend-sw active-strong"></span>
            <span class="legend-sw active-light"></span>
            adjacent active
          </span>
        </span>
        <span v-if="!loading" class="count-chip ms-auto">
          <i class="bi bi-box"></i>
          <strong>{{ instances.length }}</strong>
          {{ instances.length === 1 ? 'instance' : 'instances' }}
          <span class="count-chip-sep">·</span>
          <strong>{{ totalSessions }}</strong>
          {{ totalSessions === 1 ? 'session' : 'sessions' }}
        </span>
      </div>

      <LoadingState v-if="loading" message="Loading timeline..." />

      <EmptyState
        v-else-if="instances.length === 0"
        icon="bi-bar-chart-steps"
        title="No Instances"
        description="No application instances found for this project."
      />

      <div v-else class="swim-card">
        <div
          v-for="instance in instances"
          :key="instance.id"
          class="swim-row-group"
          :class="{ expanded: expandedIds.has(instance.id) }"
        >
          <div class="swim-row" @click="toggleExpand(instance.id)">
            <!-- Left rail (status shown via background) -->
            <div class="rail" :class="statusKey(instance.status)">
              <i
                class="bi rail-chevron"
                :class="expandedIds.has(instance.id) ? 'bi-chevron-down' : 'bi-chevron-right'"
              ></i>
              <router-link
                :to="generateInstanceUrl(instance.id)"
                class="rail-name"
                @click.stop
              >
                <span class="rail-name-text">{{ instance.instanceName }}</span>
                <i class="bi bi-box-arrow-up-right rail-name-icon"></i>
              </router-link>
              <div class="rail-meta">
                <span><i class="bi bi-clock me-1"></i>{{ FormattingService.formatDurationInMillis2Units(instance.duration) }}</span>
                <span><i class="bi bi-layers me-1"></i>{{ instance.sessionCount }} {{ instance.sessionCount === 1 ? 'session' : 'sessions' }}</span>
              </div>
            </div>

            <!-- Swim lanes -->
            <div class="track-wrap" @click.stop="toggleExpand(instance.id)">
              <div class="time-axis">
                <span
                  v-for="(tick, idx) in timelineTicks"
                  :key="tick"
                  class="axis-tick"
                  :style="{ left: axisTickLeft(idx) }"
                >{{ tick }}</span>
              </div>

              <!-- Sessions lane -->
              <div class="lane lane-sessions">
                <div class="lane-bg"></div>
                <template v-if="instanceSessions.has(instance.id)">
                  <div
                    v-for="(session, idx) in getSessionsForInstance(instance.id)"
                    :key="session.id"
                    class="session-bar"
                    :class="sessionBarClass(session, idx)"
                    :style="getSessionBarStyle(session)"
                    @mouseenter.stop="showSessionTooltip($event, session, instance.id)"
                    @mousemove.stop="updateTooltipPosition($event)"
                    @mouseleave.stop="hideTooltip"
                    @click.stop
                  ></div>
                </template>
              </div>

            </div>
          </div>

          <!-- Expand-on-click detail panel -->
          <div v-if="expandedIds.has(instance.id)" class="detail-panel">
            <div class="detail-card">
              <div class="detail-card-head">
                <span class="detail-card-title">Instance</span>
                <span class="detail-card-source">overview</span>
              </div>
              <div class="detail-card-body">
                <div class="kv"><span class="k">started</span><span class="v mono">{{ FormattingService.formatTimestampUTC(instance.createdAt) }}</span></div>
                <div class="kv">
                  <span class="k">finished</span>
                  <span v-if="instanceEnd(instance)" class="v mono">{{ FormattingService.formatTimestampUTC(instanceEnd(instance)) }}</span>
                  <span v-else class="v running">Running...</span>
                </div>
                <div class="kv"><span class="k">duration</span><span class="v mono">{{ FormattingService.formatDurationInMillis2Units(instance.duration) }}</span></div>
                <div class="kv"><span class="k">sessions</span><span class="v mono">{{ instance.sessionCount }}</span></div>
                <div class="kv">
                  <span class="k">files</span>
                  <span v-if="instanceDetails.get(instance.id)" class="v mono">{{ instanceDetails.get(instance.id)!.fileCount }}</span>
                  <span v-else class="v running">loading…</span>
                </div>
                <div class="kv">
                  <span class="k">storage</span>
                  <span v-if="instanceDetails.get(instance.id)" class="v mono">{{ FormattingService.formatBytes(instanceDetails.get(instance.id)!.totalSizeBytes) }}</span>
                  <span v-else class="v running">loading…</span>
                </div>
              </div>
            </div>

            <div class="detail-card">
              <div class="detail-card-head">
                <span class="detail-card-title">JVM · GC Heap</span>
                <span class="detail-card-source">jdk.GCHeapConfiguration</span>
              </div>
              <div class="detail-card-body">
                <div class="kv"><span class="k">min size</span><span class="v mono">{{ mockData(instance.id).jvmGcHeap.minSize }}</span></div>
                <div class="kv"><span class="k">initial size</span><span class="v mono">{{ mockData(instance.id).jvmGcHeap.initialSize }}</span></div>
                <div class="kv"><span class="k">max size</span><span class="v mono">{{ mockData(instance.id).jvmGcHeap.maxSize }}</span></div>
                <div class="kv"><span class="k">uses compressed oops</span><span class="v mono">{{ mockData(instance.id).jvmGcHeap.usesCompressedOops }}</span></div>
                <div class="kv"><span class="k">compressed oops mode</span><span class="v mono">{{ mockData(instance.id).jvmGcHeap.compressedOopsMode }}</span></div>
                <div class="kv"><span class="k">object alignment</span><span class="v mono">{{ mockData(instance.id).jvmGcHeap.objectAlignment }}</span></div>
                <div class="kv"><span class="k">heap address bits</span><span class="v mono">{{ mockData(instance.id).jvmGcHeap.heapAddressBits }}</span></div>
              </div>
            </div>

            <div class="detail-card">
              <div class="detail-card-head">
                <span class="detail-card-title">Container</span>
                <span class="detail-card-source">jdk.ContainerConfiguration</span>
              </div>
              <div class="detail-card-body">
                <div class="kv"><span class="k">container type</span><span class="v mono">{{ mockData(instance.id).container.type }}</span></div>
                <div class="kv"><span class="k">effective cpu count</span><span class="v mono">{{ mockData(instance.id).container.effectiveCpuCount }}</span></div>
                <div class="kv"><span class="k">cpu quota</span><span class="v mono">{{ mockData(instance.id).container.cpuQuota }}</span></div>
                <div class="kv"><span class="k">cpu slice period</span><span class="v mono">{{ mockData(instance.id).container.cpuSlicePeriod }}</span></div>
                <div class="kv"><span class="k">cpu shares</span><span class="v mono">{{ mockData(instance.id).container.cpuShares }}</span></div>
                <div class="kv"><span class="k">memory limit</span><span class="v mono">{{ mockData(instance.id).container.memoryLimit }}</span></div>
                <div class="kv"><span class="k">memory soft limit</span><span class="v mono">{{ mockData(instance.id).container.memorySoftLimit }}</span></div>
                <div class="kv"><span class="k">swap memory limit</span><span class="v mono">{{ mockData(instance.id).container.swapMemoryLimit }}</span></div>
                <div class="kv"><span class="k">host total memory</span><span class="v mono">{{ mockData(instance.id).container.hostTotalMemory }}</span></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Session tooltip (teleported to body) -->
      <Teleport to="body">
        <div
          v-if="hoveredSession"
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
                {{ FormattingService.formatRelativeTime(hoveredSession.session.createdAt) }}
                <span class="timeline-tooltip-utc">{{
                  FormattingService.formatTimestampUTC(hoveredSession.session.createdAt)
                }}</span>
              </span>
            </div>
            <div class="timeline-tooltip-row">
              <span class="timeline-tooltip-label">Finished</span>
              <span class="timeline-tooltip-value">
                <template v-if="hoveredSession.session.finishedAt">
                  {{ FormattingService.formatRelativeTime(hoveredSession.session.finishedAt) }}
                  <span class="timeline-tooltip-utc">{{
                    FormattingService.formatTimestampUTC(hoveredSession.session.finishedAt)
                  }}</span>
                </template>
                <template v-else>Running</template>
              </span>
            </div>
            <div class="timeline-tooltip-row">
              <span class="timeline-tooltip-label">Duration</span>
              <span class="timeline-tooltip-value">
                {{ FormattingService.formatDurationInMillis2Units(hoveredSession.session.duration) }}
              </span>
            </div>
          </div>
        </div>
      </Teleport>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import LoadingState from '@/components/LoadingState.vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectInstance, { type ProjectInstanceStatus } from '@/services/api/model/ProjectInstance';
import ProjectInstanceDetail from '@/services/api/model/ProjectInstanceDetail';
import ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession';
import FormattingService from '@/services/FormattingService';
import { useNavigation } from '@/composables/useNavigation';
import { getInstanceMockData } from './instanceMockData';
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
const selectedRange = ref('24h');
const instances = ref<ProjectInstance[]>([]);
const instanceSessions = ref<Map<string, ProjectInstanceSession[]>>(new Map());
const expandedIds = ref<Set<string>>(new Set());
const instanceDetails = ref<Map<string, ProjectInstanceDetail>>(new Map());
const instanceClient = new ProjectInstanceClient(workspaceId.value!, projectId.value!);

const totalSessions = computed(() =>
  instances.value.reduce((sum, i) => sum + (i.sessionCount ?? 0), 0)
);

const hoveredSession = ref<{ session: ProjectInstanceSession; instanceId: string } | null>(null);
const tooltipPosition = ref<{ x: number; y: number }>({ x: 0, y: 0 });
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

function axisTickLeft(idx: number): string {
  const count = timelineTicks.value.length;
  if (count <= 1) return '0%';
  return ((idx / (count - 1)) * 100).toFixed(2) + '%';
}

function getSessionBarStyle(session: ProjectInstanceSession): Record<string, string> {
  const now = Date.now();
  const rangeMs = getRangeMs();

  const startPercent = Math.max(0, Math.min(((now - session.createdAt) / rangeMs) * 100, 100));

  const endPercent =
    session.isActive || !session.finishedAt
      ? 0
      : Math.max(0, Math.min(((now - session.finishedAt) / rangeMs) * 100, 100));

  const left = endPercent;
  const width = Math.max(startPercent - endPercent, 0.3);

  return {
    left: `${left}%`,
    width: `${Math.min(width, 100 - left)}%`
  };
}

function getRangeMs(): number {
  switch (selectedRange.value) {
    case '1h':
      return 3600000;
    case '6h':
      return 21600000;
    case '24h':
      return 86400000;
    case '7d':
      return 604800000;
    case '30d':
      return 2592000000;
    default:
      return 86400000;
  }
}

function getSessionsForInstance(instanceId: string): ProjectInstanceSession[] {
  return instanceSessions.value.get(instanceId) ?? [];
}

function statusKey(status: ProjectInstanceStatus): string {
  return status.toLowerCase();
}

function sessionBarClass(session: ProjectInstanceSession, idx: number): string[] {
  const kind = session.isActive ? 'active' : 'finished';
  const shade = idx % 2 === 0 ? 'strong' : 'light';
  const classes = [`${kind}-${shade}`];
  if (idx === 0) classes.push('first');
  return classes;
}

function mockData(instanceId: string) {
  return getInstanceMockData(instanceId);
}

function instanceEnd(instance: ProjectInstance): number | undefined {
  return instance.finishedAt ?? instance.expiredAt;
}

function toggleExpand(instanceId: string): void {
  const next = new Set(expandedIds.value);
  if (next.has(instanceId)) {
    next.delete(instanceId);
  } else {
    next.add(instanceId);
    if (!instanceDetails.value.has(instanceId)) {
      instanceClient.getDetail(instanceId).then(detail => {
        const updated = new Map(instanceDetails.value);
        updated.set(instanceId, detail);
        instanceDetails.value = updated;
      });
    }
  }
  expandedIds.value = next;
}

function showSessionTooltip(
  event: MouseEvent,
  session: ProjectInstanceSession,
  instanceId: string
) {
  cancelHideTooltip();
  hoveredSession.value = { session, instanceId };
  updateTooltipPosition(event);
}

function updateTooltipPosition(event: MouseEvent) {
  tooltipPosition.value = { x: event.clientX + 12, y: event.clientY - 10 };
}

function hideTooltip() {
  tooltipHideTimeout = window.setTimeout(() => {
    hoveredSession.value = null;
  }, 150);
}

function cancelHideTooltip() {
  if (tooltipHideTimeout !== null) {
    clearTimeout(tooltipHideTimeout);
    tooltipHideTimeout = null;
  }
}

onMounted(async () => {
  instances.value = await instanceClient.list(true);
  instanceSessions.value = new Map(
    instances.value.map(instance => [instance.id, instance.sessions ?? []])
  );
  loading.value = false;
});
</script>

<style scoped>
/* ======================================================================
   Swim card (outer container holding all instance rows)
   ====================================================================== */
.swim-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-card);
}

.swim-row-group {
  border-bottom: 1px solid var(--color-border);
}
.swim-row-group:last-child {
  border-bottom: none;
}

/* ======================================================================
   Two-column row: left rail + swim lanes
   ====================================================================== */
.swim-row {
  display: grid;
  grid-template-columns: 260px 1fr;
  cursor: pointer;
  transition: background-color var(--transition-fast);
}
.swim-row:hover {
  background-color: var(--color-bg-hover);
}
.swim-row-group.expanded .swim-row {
  background-color: var(--color-bg-hover);
}

/* ======================================================================
   Left rail (instance metadata)
   ====================================================================== */
.rail {
  position: relative;
  padding: 16px 36px 16px 20px;
  border-right: 1px solid var(--color-border);
  border-left: 3px solid transparent;
  min-width: 0;
  transition: background-color var(--transition-fast);
}

.rail.pending {
  background-color: rgba(59, 130, 246, 0.06);
  border-left-color: var(--color-blue-500);
}
.rail.active {
  background-color: rgba(245, 158, 11, 0.06);
  border-left-color: var(--color-amber);
}
.rail.finished {
  background-color: rgba(16, 185, 129, 0.04);
  border-left-color: var(--color-success);
}
.rail.expired {
  background-color: rgba(156, 163, 175, 0.04);
  border-left-color: var(--color-text-light);
}

.swim-row:hover .rail.pending { background-color: rgba(59, 130, 246, 0.12); }
.swim-row:hover .rail.active { background-color: rgba(245, 158, 11, 0.12); }
.swim-row:hover .rail.finished { background-color: rgba(16, 185, 129, 0.1); }
.swim-row:hover .rail.expired { background-color: rgba(156, 163, 175, 0.1); }

.rail-chevron {
  position: absolute;
  top: 14px;
  right: 14px;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  transition: transform var(--transition-fast);
}

.rail-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-dark);
  text-decoration: none;
  line-height: 1.35;
  margin-bottom: 8px;
  transition: color var(--transition-fast);
}
.rail-name-text {
  word-break: break-all;
  min-width: 0;
}
.rail-name-icon {
  font-size: 0.72rem;
  color: var(--color-text-light);
  flex-shrink: 0;
  transition: color var(--transition-fast);
}
.rail-name:hover { color: var(--color-primary); }
.rail-name:hover .rail-name-text { text-decoration: underline; }
.rail-name:hover .rail-name-icon { color: var(--color-primary); }

.rail-meta {
  display: flex;
  gap: 12px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 2px;
}

/* ======================================================================
   Swim lanes track area
   ====================================================================== */
.track-wrap {
  padding: 12px 20px 14px;
  min-width: 0;
}

.time-axis {
  position: relative;
  height: 16px;
  margin-bottom: 6px;
}
.axis-tick {
  position: absolute;
  transform: translateX(-50%);
  top: 0;
  font-size: 0.65rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.lane {
  position: relative;
  margin-top: 6px;
}

.lane-bg {
  position: absolute;
  inset: 0;
  background: var(--color-neutral-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

/* Sessions lane: solid bars with alternating shades per index */
.lane-sessions {
  height: 32px;
}

.session-bar {
  position: absolute;
  top: 4px;
  bottom: 4px;
  border-left: 2px solid var(--color-bg-card);
  box-sizing: border-box;
  cursor: pointer;
  z-index: 1;
  transition: filter var(--transition-fast);
}
.session-bar.first {
  border-left: none;
}
.session-bar:hover {
  filter: brightness(1.08);
  z-index: 2;
}
.session-bar.finished-strong {
  background: var(--color-success-hover);
}
.session-bar.finished-light {
  background: var(--color-success);
}
.session-bar.active-strong {
  background: var(--color-amber-highlight);
  animation: session-pulse-active 2s ease-in-out infinite;
}
.session-bar.active-light {
  background: var(--color-amber);
  animation: session-pulse-active 2s ease-in-out infinite;
}

@keyframes session-pulse-active {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
  50% { box-shadow: 0 0 6px 2px rgba(245, 158, 11, 0.25); }
}

/* Shared timeline legend (inline with toolbar) */
.timeline-legend {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  font-size: 0.7rem;
  color: var(--color-text-muted);
  flex-wrap: wrap;
}
.legend-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.legend-sw {
  display: inline-block;
  width: 14px;
  height: 8px;
  border-radius: 2px;
}
.legend-sw.finished-strong {
  background: var(--color-success-hover);
}
.legend-sw.finished-light {
  background: var(--color-success);
}
.legend-sw.active-strong {
  background: var(--color-amber-highlight);
}
.legend-sw.active-light {
  background: var(--color-amber);
}

/* ======================================================================
   Expanded detail panel (three cards)
   ====================================================================== */
.detail-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 14px 20px 18px;
  background: var(--color-bg-hover);
  border-top: 1px dashed var(--color-border);
}

@media (max-width: 900px) {
  .detail-panel {
    grid-template-columns: 1fr;
  }
}

.detail-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.detail-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-light);
}
.detail-card-title {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-dark);
}
.detail-card-source {
  font-size: 0.65rem;
  color: var(--color-text-light);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.detail-card-body {
  padding: 6px 12px 10px;
}
.kv {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 6px 0;
  border-top: 1px dashed var(--color-border);
  font-size: 0.72rem;
  gap: 8px;
}
.kv:first-child {
  border-top: none;
}
.kv .k {
  color: var(--color-text-muted);
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}
.kv .v {
  color: var(--color-dark);
  font-weight: 600;
  text-align: right;
  word-break: break-word;
}
.v.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
.v.running {
  color: var(--color-text-muted);
  font-style: italic;
  font-weight: 500;
}

/* ======================================================================
   Toolbar buttons & count chip
   ====================================================================== */
.btn-group .btn {
  font-size: 0.75rem;
  padding: 0.25rem 0.75rem;
}

.count-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 0.78rem;
  color: var(--color-text);
  font-weight: 500;
  white-space: nowrap;
}
.count-chip .bi {
  color: var(--color-primary);
  font-size: 0.9rem;
}
.count-chip strong {
  color: var(--color-dark);
  font-weight: 700;
}
.count-chip-sep {
  color: var(--color-text-light);
  margin: 0 2px;
}
</style>

<!-- Non-scoped styles for teleported tooltip -->
<style>
.timeline-tooltip-container {
  position: fixed;
  z-index: 9999;
  background: var(--color-dark);
  color: var(--color-neutral-bg);
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
  color: var(--color-text-light);
  flex-shrink: 0;
}

.timeline-tooltip-value {
  text-align: right;
  font-weight: 500;
}

.timeline-tooltip-utc {
  display: block;
  color: var(--color-text-muted);
  font-size: 0.68rem;
  font-weight: 400;
}
</style>
