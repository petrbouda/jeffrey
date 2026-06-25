<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';

import InformationClient from '@/services/api/InformationClient';
import EventSummariesClient from '@/services/api/EventSummariesClient';
import ProfileSystemClient from '@/services/api/ProfileSystemClient';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import EventTimeseriesClient from '@/services/api/EventTimeseriesClient';
import DirectProfileClient from '@/services/api/DirectProfileClient';
import { profileStore } from '@/stores/profileStore';

// ------------------------------------------------------------------
// Section / field labels produced by the /information config endpoint
// (event labels from the JFR event metadata; see event-type-fields.json).
// ------------------------------------------------------------------
const SECTION_APP_INFORMATION = 'Application Information';
const SECTION_JVM = 'JVM Information';
const SECTION_GC = 'GC Configuration';
const SECTION_HEAP = 'GC Heap Configuration';
const SECTION_OS = 'OS Information';
const SECTION_CPU = 'CPU Information';
const SECTION_CONTAINER = 'Container Configuration';

const APP_PROJECT_LABEL = 'Project Label';
const APP_PROJECT_NAME = 'Project Name';
const APP_WORKSPACE_ID = 'Workspace ID';
const APP_INSTANCE_ID = 'Instance ID';
const APP_SESSION_ID = 'Session ID';
const APP_SESSION_ORDER = 'Session Order';
const APP_ATTRIBUTES = 'Attributes';
const APP_PROVISIONED_AT = 'Provisioned At';
const APP_JVM_STARTED_AT = 'JVM Started At';

const TOP_EVENT_LIMIT = 5;
const SPARK_BUCKETS = 32;
const BASIS_POINTS_PER_PERCENT = 100;

type InfoMap = Record<string, Record<string, unknown>>;

interface Attribute {
  key: string;
  value: string;
}

interface AppIdentity {
  projectLabel?: string;
  projectName?: string;
  workspaceId?: string;
  instanceId?: string;
  sessionId?: string;
  sessionOrder?: string;
  attributes: Attribute[];
  provisionedAt?: string;
  jvmStartedAt?: string;
}

interface TopEvent {
  code: string;
  label: string;
  samples: number;
  share: number;
}

interface InfoRow {
  label: string;
  value: string;
}

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);

const recordingName = ref<string>('');
const eventSource = ref<string>('');
const durationMillis = ref<number | null>(null);
const sizeInBytes = ref<number | null>(null);

const identity = ref<AppIdentity | null>(null);
const topEvents = ref<TopEvent[]>([]);
const totalEvents = ref(0);
const eventTypeCount = ref(0);
const sparkline = ref<number[]>([]);
const sparklineLabel = ref<string>('');

const avgCpuPercent = ref<number | null>(null);
const maxCpuPercent = ref<number | null>(null);

const runtimeRows = ref<InfoRow[]>([]);
const gcRows = ref<InfoRow[]>([]);
const hasGc = ref(false);

const handledByJeffrey = computed(() => identity.value !== null);

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------
function section(info: InfoMap | null, name: string): Record<string, unknown> | null {
  return info?.[name] ?? null;
}

function field(sec: Record<string, unknown> | null, label: string): string | undefined {
  const value = sec?.[label];
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return String(value);
}

function asNumber(value: string | undefined): number | undefined {
  if (value === undefined) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseAttributes(raw: string | undefined): Attribute[] {
  if (!raw) {
    return [];
  }
  const result: Attribute[] = [];
  for (const pair of raw.split(';')) {
    const trimmed = pair.trim();
    if (trimmed === '') {
      continue;
    }
    const eq = trimmed.indexOf('=');
    if (eq < 0) {
      result.push({ key: trimmed, value: '' });
    } else {
      result.push({ key: trimmed.slice(0, eq), value: trimmed.slice(eq + 1) });
    }
  }
  return result;
}

function parseIdentity(info: InfoMap | null): AppIdentity | null {
  const sec = section(info, SECTION_APP_INFORMATION);
  if (sec === null) {
    return null;
  }
  const provisionedAt = asNumber(field(sec, APP_PROVISIONED_AT));
  const jvmStartedAt = asNumber(field(sec, APP_JVM_STARTED_AT));
  return {
    projectLabel: field(sec, APP_PROJECT_LABEL),
    projectName: field(sec, APP_PROJECT_NAME),
    workspaceId: field(sec, APP_WORKSPACE_ID),
    instanceId: field(sec, APP_INSTANCE_ID),
    sessionId: field(sec, APP_SESSION_ID),
    sessionOrder: field(sec, APP_SESSION_ORDER),
    attributes: parseAttributes(field(sec, APP_ATTRIBUTES)),
    provisionedAt:
      provisionedAt !== undefined ? FormattingService.formatTimestamp(provisionedAt) : undefined,
    jvmStartedAt:
      jvmStartedAt !== undefined ? FormattingService.formatTimestamp(jvmStartedAt) : undefined
  };
}

function buildRuntimeRows(info: InfoMap | null): InfoRow[] {
  const rows: InfoRow[] = [];
  const push = (label: string, value: string | undefined) => {
    rows.push({ label, value: value ?? '—' });
  };

  const jvm = section(info, SECTION_JVM);
  push(
    'JVM',
    [field(jvm, 'JVM Name'), field(jvm, 'JVM Version')].filter(Boolean).join(' · ') || undefined
  );

  const gc = section(info, SECTION_GC);
  const young = field(gc, 'Young Garbage Collector');
  const old = field(gc, 'Old Garbage Collector');
  push('Garbage collector', [young, old].filter(Boolean).join(' + ') || undefined);

  const heap = section(info, SECTION_HEAP);
  const maxHeap = asNumber(field(heap, 'Maximum Heap Size'));
  const initialHeap = asNumber(field(heap, 'Initial Heap Size'));
  if (maxHeap !== undefined || initialHeap !== undefined) {
    const max = maxHeap !== undefined ? FormattingService.formatBytes(maxHeap) : '—';
    const initial = initialHeap !== undefined ? FormattingService.formatBytes(initialHeap) : '—';
    push('Heap (max / initial)', `${max} / ${initial}`);
  } else {
    push('Heap (max / initial)', undefined);
  }

  const os = section(info, SECTION_OS);
  const cpu = section(info, SECTION_CPU);
  const cores = field(cpu, 'Cores');
  push(
    'OS',
    [field(os, 'OS Version'), cores ? `${cores} cores` : undefined].filter(Boolean).join(' · ') ||
      undefined
  );

  const container = section(info, SECTION_CONTAINER);
  const memLimit = asNumber(field(container, 'Memory Limit'));
  const effectiveCpu = field(container, 'Effective CPU Count');
  if (memLimit !== undefined || effectiveCpu !== undefined) {
    const parts: string[] = [];
    if (effectiveCpu !== undefined) {
      parts.push(`${effectiveCpu} vCPU`);
    }
    if (memLimit !== undefined) {
      parts.push(FormattingService.formatBytes(memLimit));
    }
    push('Container limit', parts.join(' · ') || undefined);
  }

  return rows;
}

function downsample(values: number[], buckets: number): number[] {
  if (values.length <= buckets) {
    return values;
  }
  const groupSize = Math.ceil(values.length / buckets);
  const result: number[] = [];
  for (let i = 0; i < values.length; i += groupSize) {
    let sum = 0;
    const slice = values.slice(i, i + groupSize);
    for (const v of slice) {
      sum += v;
    }
    result.push(sum / slice.length);
  }
  return result;
}

const sparkMax = computed(() => Math.max(1, ...sparkline.value));

function barHeight(value: number): string {
  const pct = Math.max(4, Math.round((value / sparkMax.value) * 100));
  return `${pct}%`;
}

// ------------------------------------------------------------------
// Data loading — each section degrades independently.
// ------------------------------------------------------------------
async function loadRecordingMetadata(): Promise<void> {
  const current = profileStore.currentProfile.value;
  if (current !== null && current.id === profileId) {
    recordingName.value = current.name;
    eventSource.value = String(current.eventSource ?? '');
    durationMillis.value = current.durationInMillis ?? null;
    sizeInBytes.value = current.sizeInBytes ?? null;
    return;
  }
  try {
    const profile = await new DirectProfileClient().getById(profileId);
    recordingName.value = profile.name;
    eventSource.value = String(profile.eventSource ?? '');
    durationMillis.value = profile.durationInMillis ?? null;
    sizeInBytes.value = profile.sizeInBytes ?? null;
  } catch (err) {
    console.error('Failed to load recording metadata', err);
  }
}

async function loadDashboard(): Promise<void> {
  loading.value = true;
  error.value = null;

  const [infoResult, eventsResult, systemResult, gcResult] = await Promise.allSettled([
    new InformationClient(profileId).info() as Promise<InfoMap>,
    EventSummariesClient.primary(profileId).events(),
    new ProfileSystemClient(profileId).getOverview(),
    new ProfileGCClient(profileId).getOverview()
  ]);

  await loadRecordingMetadata();

  // Identity + runtime (from /information)
  if (infoResult.status === 'fulfilled') {
    const info = infoResult.value;
    identity.value = parseIdentity(info);
    runtimeRows.value = buildRuntimeRows(info);
  }

  // Top event types + total (from /flamegraph/events)
  if (eventsResult.status === 'fulfilled') {
    const summaries = eventsResult.value ?? [];
    const sorted = [...summaries]
      .filter(s => s.primary && s.primary.samples > 0)
      .sort((a, b) => b.primary.samples - a.primary.samples);
    const total = sorted.reduce((acc, s) => acc + s.primary.samples, 0);
    totalEvents.value = total;
    eventTypeCount.value = sorted.length;
    topEvents.value = sorted.slice(0, TOP_EVENT_LIMIT).map(s => ({
      code: s.code,
      label: s.label,
      samples: s.primary.samples,
      share: total > 0 ? s.primary.samples / total : 0
    }));
  }

  // System CPU (basis points -> percent)
  if (systemResult.status === 'fulfilled') {
    const overview = systemResult.value;
    avgCpuPercent.value = Math.round(overview.avgMachineCpuBp / BASIS_POINTS_PER_PERCENT);
    maxCpuPercent.value = Math.round(overview.maxMachineCpuBp / BASIS_POINTS_PER_PERCENT);
  }

  // Garbage collection summary
  if (gcResult.status === 'fulfilled' && gcResult.value?.header) {
    const header = gcResult.value.header;
    hasGc.value = header.totalCollections > 0;
    if (hasGc.value) {
      gcRows.value = [
        { label: 'Collections', value: FormattingService.formatNumber(header.totalCollections) },
        { label: 'p99 pause', value: FormattingService.formatDuration(header.p99PauseTime) },
        {
          label: 'GC overhead',
          value: FormattingService.formatPercentage(Number(header.gcOverhead) / 100)
        },
        { label: 'Memory freed', value: FormattingService.formatBytes(header.totalMemoryFreed) }
      ];
    }
  }

  // Events-over-time sparkline for the dominant event type (best-effort)
  if (topEvents.value.length > 0) {
    await loadSparkline(topEvents.value[0]);
  }

  if (
    infoResult.status === 'rejected' &&
    eventsResult.status === 'rejected' &&
    systemResult.status === 'rejected' &&
    gcResult.status === 'rejected'
  ) {
    error.value = 'Failed to load the recording dashboard.';
  }

  loading.value = false;
}

async function loadSparkline(top: TopEvent): Promise<void> {
  try {
    const data = await new EventTimeseriesClient(profileId).forEventType(top.code);
    const serie = data.series?.[0];
    if (serie && serie.data.length > 0) {
      const values = serie.data.map(point => point[1] ?? 0);
      sparkline.value = downsample(values, SPARK_BUCKETS);
      sparklineLabel.value = top.label;
    }
  } catch (err) {
    console.error('Failed to load events-over-time sparkline', err);
  }
}

onMounted(loadDashboard);
</script>

<template>
  <LoadingState v-if="loading" message="Loading recording dashboard..." />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else class="dashboard">
    <!-- ===================== Identity hero ===================== -->
    <section v-if="handledByJeffrey && identity" class="hero">
      <div class="hero-top">
        <div class="hero-logo"><i class="bi bi-box-seam"></i></div>
        <div class="hero-id">
          <h1 class="hero-title">
            {{ identity.projectLabel || identity.projectName || recordingName || 'Recording' }}
          </h1>
          <p class="hero-sub">
            <i class="bi bi-patch-check-fill"></i>
            Identified via <code>jeffrey.AppInformation</code> — self-describing recording
          </p>
        </div>
        <div class="hero-pills">
          <Badge
            value="Handled by Jeffrey"
            icon="bi-check-circle-fill"
            variant="success"
            size="s"
            :uppercase="false"
          />
          <Badge
            v-if="identity.sessionOrder"
            :value="`Session #${identity.sessionOrder}`"
            icon="bi-diagram-3"
            variant="secondary"
            size="s"
            :uppercase="false"
          />
          <Badge
            v-if="eventSource"
            :value="eventSource"
            icon="bi-cpu"
            variant="info"
            size="s"
            :uppercase="false"
          />
        </div>
      </div>
      <div class="hero-meta">
        <div class="hero-cell">
          <span class="hero-k">Workspace</span
          ><span class="hero-v">{{ identity.workspaceId || '—' }}</span>
        </div>
        <div class="hero-cell">
          <span class="hero-k">Project</span
          ><span class="hero-v">{{ identity.projectName || '—' }}</span>
        </div>
        <div class="hero-cell">
          <span class="hero-k">Instance</span
          ><span class="hero-v">{{ identity.instanceId || '—' }}</span>
        </div>
        <div class="hero-cell">
          <span class="hero-k">Session</span
          ><span class="hero-v">{{ identity.sessionId || '—' }}</span>
        </div>
        <div class="hero-cell">
          <span class="hero-k">JVM started</span
          ><span class="hero-v">{{ identity.jvmStartedAt || '—' }}</span>
        </div>
      </div>
    </section>

    <!-- ===================== Fallback hero ===================== -->
    <section v-else class="hero hero-fallback">
      <div class="hero-top">
        <div class="hero-logo hero-logo-muted"><i class="bi bi-question-circle"></i></div>
        <div class="hero-id">
          <h1 class="hero-title hero-title-muted">{{ recordingName || 'Recording' }}</h1>
          <p class="hero-sub hero-sub-muted">
            <i class="bi bi-exclamation-triangle-fill"></i>
            Recording not handled by Jeffrey — no <code>jeffrey.AppInformation</code> event found.
            Generic recording metrics are shown below.
          </p>
        </div>
        <div class="hero-pills">
          <Badge
            v-if="eventSource"
            :value="eventSource"
            icon="bi-cpu"
            variant="secondary"
            size="s"
            :uppercase="false"
          />
        </div>
      </div>
    </section>

    <!-- ===================== KPI tiles ===================== -->
    <section class="kpis">
      <div class="kpi">
        <div class="kpi-icon kpi-primary"><i class="bi bi-stack"></i></div>
        <div class="kpi-body">
          <span class="kpi-num">{{ FormattingService.formatNumber(totalEvents) }}</span>
          <span class="kpi-label">Total events</span>
        </div>
        <span class="kpi-tag">{{ eventTypeCount }} types</span>
      </div>
      <div class="kpi">
        <div class="kpi-icon kpi-info"><i class="bi bi-stopwatch"></i></div>
        <div class="kpi-body">
          <span class="kpi-num">{{
            durationMillis !== null
              ? FormattingService.formatDurationInMillis2Units(durationMillis)
              : '—'
          }}</span>
          <span class="kpi-label">Recording duration</span>
        </div>
      </div>
      <div class="kpi">
        <div class="kpi-icon kpi-success"><i class="bi bi-hdd"></i></div>
        <div class="kpi-body">
          <span class="kpi-num">{{
            sizeInBytes !== null ? FormattingService.formatBytes(sizeInBytes) : '—'
          }}</span>
          <span class="kpi-label">Recording size</span>
        </div>
      </div>
      <div class="kpi">
        <div class="kpi-icon kpi-warning"><i class="bi bi-cpu-fill"></i></div>
        <div class="kpi-body">
          <span class="kpi-num">{{ avgCpuPercent !== null ? `${avgCpuPercent}%` : '—' }}</span>
          <span class="kpi-label">Avg machine CPU</span>
        </div>
        <span v-if="maxCpuPercent !== null" class="kpi-tag">peak {{ maxCpuPercent }}%</span>
      </div>
    </section>

    <!-- ===================== Top events + sparkline ===================== -->
    <section class="grid-2">
      <DataTable v-if="topEvents.length > 0" table-class="top-events-table">
        <template #toolbar>
          <div class="table-head">
            <span class="card-title"
              ><i class="bi bi-bar-chart-line-fill"></i> Top {{ topEvents.length }} Event
              Types</span
            >
            <Badge
              :value="FormattingService.formatNumber(totalEvents)"
              key-label="total"
              variant="primary"
              size="s"
              borderless
            />
          </div>
        </template>
        <thead>
          <tr>
            <th>Event type</th>
            <th class="share-col">Share</th>
            <th class="text-end">Count</th>
            <th class="text-end pct-col">%</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="event in topEvents" :key="event.code">
            <td>
              <span class="event-code">{{ event.label }}</span>
            </td>
            <td class="share-col">
              <div class="bar-track">
                <span
                  class="bar-fill"
                  :style="{ width: `${Math.max(3, Math.round(event.share * 100))}%` }"
                ></span>
              </div>
            </td>
            <td class="text-end count-cell">{{ FormattingService.formatNumber(event.samples) }}</td>
            <td class="text-end pct-cell">{{ FormattingService.formatPercentage(event.share) }}</td>
          </tr>
        </tbody>
      </DataTable>

      <MainCard v-if="sparkline.length > 0" :bottom-margin="false">
        <template #header>
          <MainCardHeader icon="bi-activity" title="Events Over Time" />
        </template>
        <div class="spark-wrap">
          <p class="spark-caption">
            Activity for <code>{{ sparklineLabel }}</code> across the recording
          </p>
          <div class="spark">
            <span
              v-for="(value, index) in sparkline"
              :key="index"
              class="spark-bar"
              :style="{ height: barHeight(value) }"
            ></span>
          </div>
          <div class="spark-axis"><span>start</span><span>end</span></div>
        </div>
      </MainCard>
    </section>

    <!-- ===================== Runtime / GC / Attributes ===================== -->
    <section class="grid-3">
      <MainCard :bottom-margin="false">
        <template #header>
          <MainCardHeader icon="bi-gear-wide-connected" title="Runtime &amp; Environment" />
        </template>
        <div class="info-list">
          <div v-for="row in runtimeRows" :key="row.label" class="info-row">
            <span class="info-k">{{ row.label }}</span
            ><span class="info-v">{{ row.value }}</span>
          </div>
          <EmptyState
            v-if="runtimeRows.length === 0"
            icon="bi-gear"
            title="No configuration events"
            description="This recording has no JVM/OS/GC configuration events."
          />
        </div>
      </MainCard>

      <MainCard :bottom-margin="false">
        <template #header>
          <MainCardHeader icon="bi-recycle" title="Garbage Collection" />
        </template>
        <div class="info-list">
          <div v-for="row in gcRows" :key="row.label" class="info-row">
            <span class="info-k">{{ row.label }}</span
            ><span class="info-v">{{ row.value }}</span>
          </div>
          <EmptyState
            v-if="!hasGc"
            icon="bi-recycle"
            title="No GC activity"
            description="No garbage collection events were recorded."
          />
        </div>
      </MainCard>

      <MainCard :bottom-margin="false">
        <template #header>
          <MainCardHeader icon="bi-tags" title="Attributes" />
        </template>
        <div class="info-list">
          <div v-if="identity && identity.attributes.length > 0" class="chips">
            <Badge
              v-for="attr in identity.attributes"
              :key="attr.key"
              :value="attr.value"
              :key-label="attr.key"
              variant="indigo"
              size="s"
              :uppercase="false"
              borderless
            />
          </div>
          <div v-if="identity && identity.provisionedAt" class="info-row">
            <span class="info-k">Provisioned</span
            ><span class="info-v">{{ identity.provisionedAt }}</span>
          </div>
          <EmptyState
            v-if="!identity || (identity.attributes.length === 0 && !identity.provisionedAt)"
            icon="bi-tags"
            title="No attributes"
            description="No custom attributes were attached to this recording."
          />
        </div>
      </MainCard>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
}

/* ===================== Hero ===================== */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-lg);
  padding: var(--spacing-6) var(--spacing-6);
  background: linear-gradient(
    135deg,
    var(--color-indigo-dark) 0%,
    var(--color-indigo) 45%,
    var(--color-primary) 100%
  );
  box-shadow: var(--shadow-lg);
}

.hero-fallback {
  background: linear-gradient(135deg, var(--color-light), var(--color-white));
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-base);
}

.hero-top {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
}

.hero-logo {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  display: grid;
  place-items: center;
  font-size: 1.6rem;
  color: var(--color-white);
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
}

.hero-logo-muted {
  color: var(--color-warning);
  background: var(--color-light);
  border-color: var(--color-border);
}

.hero-id {
  min-width: 0;
}

.hero-title {
  color: var(--color-white);
  font-size: 1.4rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0;
}

.hero-title-muted {
  color: var(--color-dark);
}

.hero-sub {
  color: rgba(255, 255, 255, 0.65);
  font-size: var(--font-size-sm);
  margin: 4px 0 0;
}

.hero-sub-muted {
  color: var(--color-text-muted);
}

.hero-sub code {
  color: var(--color-white);
  background: rgba(255, 255, 255, 0.12);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
}

.hero-sub-muted code {
  color: var(--color-dark);
  background: var(--color-light);
}

.hero-pills {
  margin-left: auto;
  display: flex;
  gap: var(--spacing-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.hero-meta {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 1px;
  margin-top: var(--spacing-4);
  background: rgba(255, 255, 255, 0.12);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.hero-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: var(--spacing-3) var(--spacing-3);
  background: rgba(17, 15, 48, 0.28);
}

.hero-k {
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.55);
  font-weight: var(--font-weight-semibold);
}

.hero-v {
  font-size: 0.82rem;
  color: var(--color-white);
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ===================== KPI tiles ===================== */
.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-3);
}

.kpi {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
  padding: var(--spacing-4);
}

.kpi-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
  font-size: 1.2rem;
  color: var(--color-white);
  flex-shrink: 0;
}

.kpi-primary {
  background: var(--color-primary);
}

.kpi-info {
  background: var(--color-info);
}

.kpi-success {
  background: var(--color-success);
}

.kpi-warning {
  background: var(--color-warning);
}

.kpi-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.kpi-num {
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--color-dark);
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.kpi-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.kpi-tag {
  position: absolute;
  top: var(--spacing-3);
  right: var(--spacing-3);
  font-size: 0.62rem;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-muted);
  background: var(--color-light);
  padding: 2px 7px;
  border-radius: var(--radius-sm);
}

/* ===================== Grids ===================== */
.grid-2 {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: var(--spacing-3);
  align-items: start;
}

.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-3);
  align-items: start;
}

.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  font-size: 0.84rem;
}

.card-title i {
  color: var(--color-primary);
}

/* ===================== Top events table ===================== */
.event-code {
  font-family: var(--font-family-mono, monospace);
  font-size: 0.76rem;
  font-weight: 500;
  color: var(--color-dark);
}

.share-col {
  width: 34%;
}

.pct-col {
  width: 64px;
}

.bar-track {
  height: 6px;
  border-radius: var(--radius-sm);
  background: var(--color-light);
  overflow: hidden;
}

.bar-fill {
  display: block;
  height: 100%;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
}

.count-cell {
  font-weight: var(--font-weight-semibold);
  font-variant-numeric: tabular-nums;
  color: var(--color-dark);
}

.pct-cell {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ===================== Sparkline ===================== */
.spark-wrap {
  padding: var(--spacing-2) var(--spacing-2) 0;
}

.spark-caption {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin: 0 0 var(--spacing-3);
}

.spark-caption code {
  color: var(--color-dark);
  background: var(--color-light);
  padding: 1px 5px;
  border-radius: var(--radius-sm);
}

.spark {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 96px;
}

.spark-bar {
  flex: 1;
  min-height: 4px;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  background: linear-gradient(180deg, var(--color-primary), var(--color-info));
}

.spark-axis {
  display: flex;
  justify-content: space-between;
  font-size: 0.62rem;
  color: var(--color-text-muted);
  margin-top: 7px;
}

/* ===================== Info lists ===================== */
.info-list {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-3);
  padding: var(--spacing-2) 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.8rem;
}

.info-row:last-child {
  border-bottom: none;
}

.info-k {
  color: var(--color-text-muted);
}

.info-v {
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
  text-align: right;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  padding-bottom: var(--spacing-3);
}

@media (max-width: 992px) {
  .kpis,
  .grid-2,
  .grid-3,
  .hero-meta {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 576px) {
  .kpis,
  .grid-2,
  .grid-3,
  .hero-meta {
    grid-template-columns: 1fr;
  }
}
</style>
