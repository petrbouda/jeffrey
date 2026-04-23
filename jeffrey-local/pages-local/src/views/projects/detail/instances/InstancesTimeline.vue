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
          :class="{
            expanded: expandedIds.has(instance.id),
            'has-open-session': activeSessionByInstance.has(instance.id)
          }"
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
                    :class="[sessionBarClass(session, idx), { selected: activeSessionByInstance.get(instance.id) === session.id }]"
                    :style="getSessionBarStyle(session)"
                    @mouseenter.stop="showSessionTooltip($event, session, instance.id)"
                    @mousemove.stop="updateTooltipPosition($event)"
                    @mouseleave.stop="hideTooltip"
                    @click.stop="toggleSessionBar(instance.id, session)"
                  ></div>
                </template>
              </div>

            </div>
          </div>

          <!-- Instance overview panel: opens when the row is clicked. Uses the same
               drawer shell as the session drawer below; only one can be open per row. -->
          <div v-if="expandedIds.has(instance.id)" class="inline-drawer">
            <div class="inline-drawer-head">
              <i class="bi bi-box inline-drawer-icon"></i>
              <span class="inline-drawer-label">Instance</span>
              <span class="inline-drawer-id mono">{{ instance.instanceName }}</span>
              <Badge
                :value="statusBadgeLabel(instance.status)"
                :variant="statusBadgeVariant(instance.status)"
                size="xxs"
              />
              <span class="inline-drawer-meta">
                {{ FormattingService.formatTimestampUTC(instance.createdAt) }}
                →
                <template v-if="instanceEnd(instance)">
                  {{ FormattingService.formatTimestampUTC(instanceEnd(instance)) }}
                </template>
                <template v-else>Running</template>
                <span class="inline-drawer-meta-sep">·</span>
                {{ FormattingService.formatDurationInMillis2Units(instance.duration) }}
              </span>
              <button
                type="button"
                class="inline-drawer-close"
                aria-label="Close instance detail"
                @click="closeInstanceDrawer(instance.id)"
              >
                <i class="bi bi-x-lg"></i>
              </button>
            </div>

            <div class="inline-drawer-body">
              <LoadingState
                v-if="!instanceDetails.get(instance.id)"
                message="Loading instance details..."
              />
              <div v-else class="detail-cards">
                <div class="detail-card">
                  <div class="detail-card-head">
                    <span class="detail-card-title">Overview</span>
                  </div>
                  <div class="detail-card-body">
                    <div class="kv"><span class="k">started</span><span class="v mono">{{ FormattingService.formatTimestampUTC(instanceDetails.get(instance.id)!.instance.createdAt) }}</span></div>
                    <div class="kv">
                      <span class="k">finished</span>
                      <span v-if="instanceEnd(instanceDetails.get(instance.id)!.instance)" class="v mono">{{ FormattingService.formatTimestampUTC(instanceEnd(instanceDetails.get(instance.id)!.instance)) }}</span>
                      <span v-else class="v running">Running...</span>
                    </div>
                    <div class="kv"><span class="k">duration</span><span class="v mono">{{ FormattingService.formatDurationInMillis2Units(instanceDetails.get(instance.id)!.instance.duration) }}</span></div>
                    <div class="kv"><span class="k">sessions</span><span class="v mono">{{ instanceDetails.get(instance.id)!.instance.sessionCount }}</span></div>
                    <div class="kv"><span class="k">files</span><span class="v mono">{{ instanceDetails.get(instance.id)!.fileCount }}</span></div>
                    <div class="kv"><span class="k">storage</span><span class="v mono">{{ FormattingService.formatBytes(instanceDetails.get(instance.id)!.totalSizeBytes) }}</span></div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Session drawer: opens when a session bar in the timeline above is clicked. -->
          <template v-if="activeSessionByInstance.has(instance.id)">
            <template v-for="session in getSessionsForInstance(instance.id)" :key="session.id + ':drawer'">
              <div
                v-if="activeSessionByInstance.get(instance.id) === session.id"
                class="inline-drawer"
              >
                <div class="inline-drawer-head">
                  <i class="bi bi-layers inline-drawer-icon"></i>
                  <span class="inline-drawer-label">Session</span>
                  <span class="inline-drawer-id mono">{{ session.id }}</span>
                  <Badge
                    :value="session.isActive ? 'Active' : 'Finished'"
                    :variant="session.isActive ? 'orange' : 'green'"
                    size="xxs"
                  />
                  <span class="inline-drawer-meta">
                    {{ FormattingService.formatTimestampUTC(session.createdAt) }}
                    →
                    <template v-if="session.finishedAt">
                      {{ FormattingService.formatTimestampUTC(session.finishedAt) }}
                    </template>
                    <template v-else>Running</template>
                    <span class="inline-drawer-meta-sep">·</span>
                    {{ FormattingService.formatDurationInMillis2Units(session.duration) }}
                  </span>
                  <button
                    type="button"
                    class="inline-drawer-close"
                    aria-label="Close session detail"
                    @click="closeSessionDrawer(instance.id)"
                  >
                    <i class="bi bi-x-lg"></i>
                  </button>
                </div>

                <div class="inline-drawer-body">
                  <LoadingState
                    v-if="!sessionDetails.get(session.id)"
                    message="Loading session..."
                  />
                  <EmptyState
                    v-else-if="!sessionDetails.get(session.id)!.environment"
                    icon="bi-file-earmark-x"
                    title="No environment data"
                    description="This session has no finished recording chunk yet, so no JVM environment events could be read."
                  />
                  <div v-else class="detail-cards">
                    <div
                      v-for="[typeName, fields] in envEntries(sessionDetails.get(session.id)!.environment)"
                      :key="typeName"
                      class="detail-card"
                    >
                      <div class="detail-card-head">
                        <span class="detail-card-title">{{ cardTitle(typeName) }}</span>
                        <span class="detail-card-subtitle mono">{{ typeName }}</span>
                      </div>
                      <div class="detail-card-body">
                        <!-- Shutdown cards get a derived kind badge at the top before the generic rows. -->
                        <template v-if="typeName === 'jdk.Shutdown'">
                          <div class="kv">
                            <span class="k">kind</span>
                            <span class="v">
                              <Badge
                                :value="shutdownKindLabel(classifyShutdownKind((fields as any).reason))"
                                :variant="shutdownKindVariant(classifyShutdownKind((fields as any).reason))"
                                size="xxs"
                              />
                            </span>
                          </div>
                          <div class="kv kv-desc">
                            <span class="v reason-desc">{{ shutdownKindDescription(classifyShutdownKind((fields as any).reason)) }}</span>
                          </div>
                        </template>
                        <template
                          v-for="row in fieldRows(fields)"
                          :key="row.key"
                        >
                          <div class="kv" :class="{ long: row.multi }">
                            <span class="k">{{ row.label }}</span>
                            <span class="v" :class="{ mono: row.mono, multi: row.multi, 'bool-true': row.boolTrue, 'bool-false': row.boolFalse }">{{ row.display }}</span>
                          </div>
                        </template>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </template>
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
import ProjectInstanceSessionDetail from '@/services/api/model/ProjectInstanceSessionDetail';
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
const selectedRange = ref('24h');
const instances = ref<ProjectInstance[]>([]);
const instanceSessions = ref<Map<string, ProjectInstanceSession[]>>(new Map());
const expandedIds = ref<Set<string>>(new Set());
const instanceDetails = ref<Map<string, ProjectInstanceDetail>>(new Map());

// Maps an instance id → the id of the session whose drawer is currently open beneath it.
// Only one drawer per instance is open at a time; re-clicking the same bar closes it, and
// clicking another bar in the same row swaps the content.
const activeSessionByInstance = ref<Map<string, string>>(new Map());
const sessionDetails = ref<Map<string, ProjectInstanceSessionDetail>>(new Map());
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

// ====================================================================
// Dynamic environment-card rendering
// --------------------------------------------------------------------
// The server passes the raw JFR JSON through as
//   { "jdk.JVMInformation": { jvmName, ..., pid }, "jdk.Shutdown": { ... }, ... }
// We iterate that map directly so new JFR fields appear automatically.
//
// Label resolution: override map first, then an acronym-aware camelCase
// splitter as fallback.
// Value rendering: inferred from the field name (size/memory → bytes,
// *Time → UTC timestamp, pauseTarget → nanos→ms, booleans → ✓/✗, long
// strings → monospace wrap, null/empty → row is hidden).
// ====================================================================

const CARD_TITLE_OVERRIDES: Record<string, string> = {
  'jdk.JVMInformation': 'JVM',
  'jdk.OSInformation': 'OS',
  'jdk.CPUInformation': 'CPU',
  'jdk.GCConfiguration': 'GC',
  'jdk.GCHeapConfiguration': 'GC Heap',
  'jdk.CompilerConfiguration': 'Compiler',
  'jdk.ContainerConfiguration': 'Container',
  'jdk.VirtualizationInformation': 'Virtualization',
  'jdk.Shutdown': 'Shutdown',
};

const FIELD_LABEL_OVERRIDES: Record<string, string> = {
  pid: 'PID',
  hwThreads: 'HW Threads',
  cpu: 'CPU Model',
  jvmName: 'Name',
  jvmVersion: 'Version',
  jvmArguments: 'JVM Args',
  javaArguments: 'Java Args',
  jvmFlags: 'Flags',
  jvmStartTime: 'Start Time',
  osVersion: 'Version',
  compressedOopsMode: 'OOPs Mode',
  usesCompressedOops: 'Compressed OOPs',
  usesDynamicGCThreads: 'Dynamic GC Threads',
  isExplicitGCConcurrent: 'Explicit GC Concurrent',
  isExplicitGCDisabled: 'Explicit GC Disabled',
  parallelGCThreads: 'Parallel GC Threads',
  concurrentGCThreads: 'Concurrent GC Threads',
  gcTimeRatio: 'GC Time Ratio',
  pauseTarget: 'Pause Target',
  dynamicCompilerThreadCount: 'Dynamic Threads',
  tieredCompilation: 'Tiered',
  threadCount: 'Threads',
  containerType: 'Type',
  cpuSlicePeriod: 'CPU Slice Period',
  cpuQuota: 'CPU Quota',
  cpuShares: 'CPU Shares',
  effectiveCpuCount: 'Effective CPUs',
  memorySoftLimit: 'Memory (Soft)',
  memoryLimit: 'Memory Limit',
  swapMemoryLimit: 'Swap Limit',
  hostTotalMemory: 'Host Total',
  hostTotalSwapMemory: 'Host Swap Total',
  reason: 'Reason',
  eventTime: 'Time',
};

// Rendering-order priority when listing event-type cards (smaller first).
// Unknown event types fall through to a large sentinel so they land at the end.
const CARD_ORDER: Record<string, number> = {
  'jdk.Shutdown': 0,
  'jdk.JVMInformation': 1,
  'jdk.OSInformation': 2,
  'jdk.CPUInformation': 3,
  'jdk.GCConfiguration': 4,
  'jdk.GCHeapConfiguration': 5,
  'jdk.CompilerConfiguration': 6,
  'jdk.ContainerConfiguration': 7,
  'jdk.VirtualizationInformation': 8,
};

type FieldRow = {
  key: string;
  label: string;
  display: string;
  mono?: boolean;
  multi?: boolean;
  boolTrue?: boolean;
  boolFalse?: boolean;
};

function envEntries(env: Record<string, Record<string, unknown>>): [string, Record<string, unknown>][] {
  return Object.entries(env).sort(([a], [b]) => {
    const ao = CARD_ORDER[a] ?? 1000;
    const bo = CARD_ORDER[b] ?? 1000;
    return ao - bo || a.localeCompare(b);
  });
}

function cardTitle(typeName: string): string {
  return CARD_TITLE_OVERRIDES[typeName] ?? typeName.replace(/^jdk\./, '');
}

function splitLabel(key: string): string {
  const spaced = key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2');
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function fieldLabel(key: string): string {
  return FIELD_LABEL_OVERRIDES[key] ?? splitLabel(key);
}

function inferValue(key: string, value: unknown): FieldRow | null {
  if (value == null) return null;
  if (typeof value === 'boolean') {
    return { key, label: fieldLabel(key), display: value ? '✓' : '✗', boolTrue: value, boolFalse: !value };
  }
  if (typeof value === 'number') {
    if (key === 'jvmStartTime' || key === 'eventTime' || /Time$/.test(key)) {
      return { key, label: fieldLabel(key), display: FormattingService.formatTimestampUTC(value), mono: true };
    }
    if (key === 'objectAlignment') {
      return { key, label: fieldLabel(key), display: `${value} B`, mono: true };
    }
    if (/(Size|Memory|Limit)$/.test(key) && value > 0) {
      return { key, label: fieldLabel(key), display: FormattingService.formatBytes(value), mono: true };
    }
    if (key === 'pauseTarget') {
      return { key, label: fieldLabel(key), display: `${value / 1_000_000} ms`, mono: true };
    }
    if (key === 'cpuQuota' || key === 'cpuSlicePeriod') {
      return { key, label: fieldLabel(key), display: `${value.toLocaleString()} µs`, mono: true };
    }
    return { key, label: fieldLabel(key), display: value.toLocaleString(), mono: true };
  }
  if (typeof value === 'string') {
    if (value.length === 0) return null;
    return { key, label: fieldLabel(key), display: value, mono: true, multi: value.length > 60 };
  }
  return { key, label: fieldLabel(key), display: String(value), mono: true };
}

function fieldRows(fields: Record<string, unknown>): FieldRow[] {
  // Shutdown's reason/eventTime are still rendered generically; the derived
  // `kind` badge is placed outside the loop at the top of that card.
  return Object.entries(fields)
    .map(([k, v]) => inferValue(k, v))
    .filter((row): row is FieldRow => row !== null);
}

function classifyShutdownKind(reason: unknown): string {
  switch (reason) {
    case 'Shutdown requested from Java': return 'GRACEFUL';
    case 'VM Error': return 'VM_ERROR';
    case 'CrashOnOutOfMemoryError': return 'CRASH_OOM';
    default: return 'UNKNOWN';
  }
}

function shutdownKindLabel(kind: string | undefined): string {
  switch (kind) {
    case 'GRACEFUL': return 'Graceful';
    case 'VM_ERROR': return 'Crash';
    case 'CRASH_OOM': return 'OOM Crash';
    default: return 'Unknown';
  }
}

function shutdownKindVariant(kind: string | undefined): 'green' | 'red' | 'grey' {
  switch (kind) {
    case 'GRACEFUL': return 'green';
    case 'VM_ERROR':
    case 'CRASH_OOM': return 'red';
    default: return 'grey';
  }
}

function shutdownKindDescription(kind: string | undefined): string {
  switch (kind) {
    case 'GRACEFUL':
      return 'Clean shutdown via System.exit, SIGTERM/SIGINT/SIGHUP, or the main thread finishing.';
    case 'VM_ERROR':
      return 'Fatal JVM error (SIGSEGV, assertion failure). Look for an hs_err_pid*.log alongside the recording.';
    case 'CRASH_OOM':
      return 'Crashed due to OutOfMemoryError with -XX:+CrashOnOutOfMemoryError enabled (JDK 24+).';
    default:
      return 'Unrecognised reason — usually from a third-party JVM (SapMachine, GraalVM, …). See the raw reason above.';
  }
}

function instanceEnd(instance: ProjectInstance): number | undefined {
  return instance.finishedAt ?? instance.expiredAt;
}

function statusBadgeLabel(status: ProjectInstanceStatus): string {
  switch (status) {
    case 'PENDING': return 'Pending';
    case 'ACTIVE': return 'Active';
    case 'FINISHED': return 'Finished';
    case 'EXPIRED': return 'Expired';
    default: return 'Unknown';
  }
}

function statusBadgeVariant(status: ProjectInstanceStatus): 'blue' | 'orange' | 'green' | 'grey' {
  switch (status) {
    case 'PENDING': return 'blue';
    case 'ACTIVE': return 'orange';
    case 'FINISHED': return 'green';
    case 'EXPIRED':
    default: return 'grey';
  }
}

function toggleExpand(instanceId: string): void {
  const next = new Set(expandedIds.value);
  if (next.has(instanceId)) {
    next.delete(instanceId);
  } else {
    next.add(instanceId);
    // Mutual exclusion: only one drawer (Instance or Session) open per row.
    const activeNext = new Map(activeSessionByInstance.value);
    activeNext.delete(instanceId);
    activeSessionByInstance.value = activeNext;

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

function closeInstanceDrawer(instanceId: string): void {
  const next = new Set(expandedIds.value);
  next.delete(instanceId);
  expandedIds.value = next;
}

function toggleSessionBar(instanceId: string, session: ProjectInstanceSession): void {
  const next = new Map(activeSessionByInstance.value);
  if (next.get(instanceId) === session.id) {
    next.delete(instanceId);
  } else {
    next.set(instanceId, session.id);
    // Mutual exclusion: close the Instance panel for this row if it was open.
    const expandedNext = new Set(expandedIds.value);
    expandedNext.delete(instanceId);
    expandedIds.value = expandedNext;

    if (!sessionDetails.value.has(session.id)) {
      instanceClient.getSessionDetail(instanceId, session.id).then(detail => {
        const updated = new Map(sessionDetails.value);
        updated.set(session.id, detail);
        sessionDetails.value = updated;
      });
    }
  }
  activeSessionByInstance.value = next;
}

function closeSessionDrawer(instanceId: string): void {
  const next = new Map(activeSessionByInstance.value);
  next.delete(instanceId);
  activeSessionByInstance.value = next;
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

/* Detail cards grid — reused by both the instance and session drawers. */
.detail-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
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
.v.multi {
  text-align: left;
  background: var(--color-bg);
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  font-size: 0.66rem;
  color: var(--color-text-muted);
  font-weight: 500;
  max-width: 100%;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
}
.v.bool-true { color: var(--color-success); }
.v.bool-false { color: var(--color-text-light); }

.kv.long {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
.kv.long .k { min-width: 0; }
.kv.long .v { text-align: left; width: 100%; }

.detail-card-subtitle {
  font-size: 0.62rem;
  font-weight: 500;
  color: var(--color-text-light);
}
.detail-card-subtitle.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
.reason-desc {
  font-size: 0.65rem;
  color: var(--color-text-light);
  font-style: italic;
  text-align: right;
  line-height: 1.4;
  font-weight: 400;
  max-width: 100%;
}

/* ======================================================================
   Session drawer — opens inline beneath the row when a session bar is
   clicked in the timeline lane above. One drawer per instance at a time.
   ====================================================================== */
.swim-row-group.has-open-session {
  background-color: var(--color-bg-hover);
}
.swim-row-group.has-open-session .swim-row {
  background-color: var(--color-bg-hover);
}

.session-bar.selected {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
  z-index: 2;
}

.inline-drawer {
  border-top: 1px dashed var(--color-border);
  background: var(--color-bg-hover);
  padding: 14px 20px 18px;
  animation: inline-drawer-in 0.18s ease-out;
}
@keyframes inline-drawer-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.inline-drawer-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 10px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.76rem;
  flex-wrap: wrap;
}
.inline-drawer-icon {
  font-size: 0.95rem;
  color: var(--color-primary);
  flex-shrink: 0;
}
.inline-drawer-label {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
  flex-shrink: 0;
}
.inline-drawer-id {
  color: var(--color-dark);
  font-weight: 600;
  word-break: break-all;
  min-width: 0;
}
.inline-drawer-meta {
  color: var(--color-text-muted);
  font-size: 0.7rem;
  margin-left: auto;
}
.inline-drawer-meta-sep {
  margin: 0 6px;
  color: var(--color-text-light);
}
.inline-drawer-close {
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  padding: 2px 8px;
  font-size: 0.75rem;
  cursor: pointer;
  line-height: 1;
  transition: all var(--transition-fast);
}
.inline-drawer-close:hover {
  background: var(--color-light);
  color: var(--color-dark);
}

.inline-drawer-body {
  min-height: 40px;
}

.kv.kv-desc {
  border-top: none;
}
.kv.kv-desc .v {
  text-align: left;
  font-weight: 400;
  color: var(--color-text-light);
  font-style: italic;
  font-size: 0.68rem;
  line-height: 1.4;
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
