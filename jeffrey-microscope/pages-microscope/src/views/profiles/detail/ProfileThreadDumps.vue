<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<template>
  <LoadingState v-if="loading" message="Loading thread dumps..." />

  <ErrorState v-else-if="error" message="Failed to load thread dumps" />

  <div v-else>
    <PageHeader
      title="Thread Dumps"
      description="Periodic jstack-style snapshots from jdk.ThreadDump: states, hot frames, locks, deadlocks and stuck threads"
      icon="bi-file-earmark-text"
    />

    <EmptyState
      v-if="!hasData"
      icon="bi-file-earmark-text"
      title="No thread dumps in this recording"
      description="This profile contains no jdk.ThreadDump events (periodic dumps are emitted ~every 60s by the default profiling config)."
    />

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Timeline -->
      <div v-show="activeTab === 'timeline'">
        <ChartDescription
          shows="Thread counts by state across the periodic dumps (carried forward between samples)"
          use-case="A BLOCKED spike is a lock storm; a steadily climbing total is a thread leak; a pool full of WAITING is an idle pool"
        />
        <div class="chart-container">
          <div id="thread-dump-state-chart"></div>
        </div>
      </div>

      <!-- Activity -->
      <div v-show="activeTab === 'activity'">
        <ChartDescription
          shows="The stack frames threads sit at most often, across all dumps"
          use-case="Many threads at the same frame reveal idle pools (parked), I/O waits, or a genuine hotspot"
        />
        <DataTable>
          <template #toolbar>
            <TableToolbar v-model="framesView.query" search-placeholder="Filter frames...">
              <span class="toolbar-info">Top frames</span>
              <template #filters>
                <Badge key-label="Frames" :value="framesView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Frame</th>
              <th class="text-end">Occurrences</th>
              <th class="text-end">Distinct Threads</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(f, i) in framesView.visible" :key="i">
              <td><code>{{ f.frame }}</code></td>
              <td class="text-end">{{ FormattingService.formatNumber(f.occurrences) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(f.distinctThreads) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="framesView.visible.length"
              :match-count="framesView.matchCount"
              :total="framesView.total"
              :expanded="framesView.expanded"
              :page-size="framesView.pageSize"
              @toggle="framesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Locks & Deadlocks -->
      <div v-show="activeTab === 'locks'">
        <h6 class="section-title">Deadlocks</h6>
        <EmptyState
          v-if="data!.deadlocks.length === 0"
          icon="bi-check-circle"
          title="No deadlocks detected"
          description="No 'Found one Java-level deadlock' section appeared in any dump."
        />
        <div v-for="(d, i) in data!.deadlocks" :key="i" class="deadlock-card mb-3">
          <div class="deadlock-head">
            <i class="bi bi-exclamation-octagon-fill"></i>
            <span>Deadlock at {{ formatOffset(d.timeOffsetMillis) }}</span>
            <span class="deadlock-threads">{{ d.involvedThreads.join(' ↔ ') }}</span>
          </div>
          <pre class="deadlock-body">{{ d.description }}</pre>
        </div>

        <h6 class="section-title mt-4">Lock Contention <span class="muted">(worst dump)</span></h6>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Monitor</th>
                <th>Class</th>
                <th class="text-end">Waiters</th>
                <th>Owner</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(c, i) in data!.lockContention" :key="i">
                <td><code>{{ c.monitorId }}</code></td>
                <td>{{ c.monitorClass ?? '—' }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(c.waiterCount) }}</td>
                <td>{{ c.owner ?? '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.lockContention.length === 0"
          icon="bi-unlock"
          title="No monitor contention recorded"
        />
      </div>

      <!-- Stuck -->
      <div v-show="activeTab === 'stuck'">
        <ChartDescription
          shows="Threads whose stack stayed identical across consecutive dumps"
          use-case="A stack that does not move across dumps is a hung, slow, or deadlocked thread — the longer the run, the more suspicious"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Thread</th>
                <th>State</th>
                <th>Top Frame</th>
                <th class="text-end">Dumps</th>
                <th class="text-end">Stuck For</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in data!.stuckThreads" :key="i">
                <td>{{ s.name }}</td>
                <td><Badge :value="s.state" :variant="stateVariant(s.state)" size="s" /></td>
                <td><code>{{ s.topFrame }}</code></td>
                <td class="text-end">{{ s.consecutiveDumps }}</td>
                <td class="text-end">{{ formatOffset(s.stuckForMillis) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.stuckThreads.length === 0"
          icon="bi-check-circle"
          title="No stuck threads"
          description="No thread kept the same stack across 3+ consecutive dumps."
        />
      </div>

      <!-- Heatmap -->
      <div v-show="activeTab === 'heatmap'">
        <ChartDescription
          shows="Each tracked thread's state across the dump sequence (BLOCKED and stuck threads first)"
          use-case="Spot a thread that turns and stays BLOCKED, or a pool that fills with BLOCKED over time"
        />
        <div class="heatmap-legend">
          <span v-for="state in legendStates" :key="state" class="legend-item">
            <span class="legend-swatch" :style="{ background: stateColor(state) }"></span>{{ state }}
          </span>
        </div>
        <div class="heatmap-scroll">
          <div
            class="heatmap-grid"
            :style="{ gridTemplateColumns: `var(--heatmap-label-w) repeat(${data!.heatmap.dumpOffsets.length}, 16px)` }"
          >
            <template v-for="row in data!.heatmap.rows" :key="row.threadName">
              <div class="heatmap-label" :title="row.threadName">{{ row.threadName }}</div>
              <div
                v-for="(state, i) in row.states"
                :key="i"
                class="heatmap-cell"
                :style="{ background: state ? stateColor(state) : 'transparent' }"
                :title="`${row.threadName} · ${state ?? 'absent'} @ ${formatOffset(data!.heatmap.dumpOffsets[i])}`"
              ></div>
            </template>
          </div>
        </div>
      </div>

      <!-- Browse -->
      <div v-show="activeTab === 'browse'">
        <div class="browse-controls">
          <label>
            Dump
            <select v-model.number="selectedIndex" class="form-select form-select-sm" @change="loadDump">
              <option v-for="d in data!.dumps" :key="d.index" :value="d.index">
                {{ formatOffset(d.timeOffsetMillis) }} · {{ d.threadCount }} threads
                <template v-if="d.deadlockCount > 0"> · ⚠ deadlock</template>
              </option>
            </select>
          </label>
          <label>
            State
            <select v-model="browseStateFilter" class="form-select form-select-sm">
              <option value="">All</option>
              <option v-for="state in legendStates" :key="state" :value="state">{{ state }}</option>
            </select>
          </label>
          <input v-model="browseQuery" class="form-control form-control-sm browse-search" placeholder="Filter by thread name..." />
          <label class="raw-toggle"><input v-model="showRaw" type="checkbox" /> Raw text</label>
        </div>

        <LoadingState v-if="dumpLoading" message="Loading dump..." />
        <pre v-else-if="showRaw" class="raw-dump">{{ selectedDump?.rawText }}</pre>
        <div v-else>
          <details v-for="(t, i) in browseThreads" :key="i" class="thread-block">
            <summary>
              <Badge :value="t.state" :variant="stateVariant(t.state)" size="s" />
              <span class="thread-name">{{ t.name }}</span>
              <code v-if="t.frames.length" class="thread-top">{{ t.frames[0] }}</code>
            </summary>
            <pre class="thread-stack">{{ t.frames.map((f) => '  at ' + f).join('\n') || '(no Java frames)' }}</pre>
            <ul v-if="t.locks.length" class="thread-locks">
              <li v-for="(l, li) in t.locks" :key="li">
                {{ lockLabel(l.kind) }} <code>{{ l.monitorId }}</code>
                <span v-if="l.monitorClass">(a {{ l.monitorClass }})</span>
              </li>
            </ul>
          </details>
          <EmptyState
            v-if="browseThreads.length === 0"
            icon="bi-search"
            title="No threads match"
          />
        </div>
      </div>

      <!-- About -->
      <div v-show="activeTab === 'about'">
        <AboutPanel>
          <AboutSection icon="bi-file-earmark-text" title="What Thread Dumps Tell You">
            <p>
              The JVM periodically emits a full textual thread dump (<code>jdk.ThreadDump</code>, like
              <code>jstack</code>): every thread's state, stack and held/awaited locks. Jeffrey parses
              each dump and correlates them across the recording, so you get trends instead of a single
              snapshot.
            </p>
            <AboutCallout variant="tip" title="Sampled, not continuous" icon="bi-lightbulb-fill">
              Dumps are periodic (~every 60s by default), so the timeline is a coarse sample — great for
              hangs and saturation, not for sub-second spikes.
            </AboutCallout>
          </AboutSection>
          <AboutSection icon="bi-graph-up" title="Reading the Views">
            <FeatureGrid>
              <FeatureCard icon="bi-activity" variant="primary" title="Timeline & Activity">
                State counts over time and the frames threads sit at most — saturation and idle vs busy.
              </FeatureCard>
              <FeatureCard icon="bi-lock" variant="danger" title="Locks & Deadlocks">
                JVM-reported deadlocks and the most-contended monitors with their waiters and owner.
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-split" variant="warning" title="Stuck & Heatmap">
                Threads whose stack never moves across dumps, and a per-thread state heatmap.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import Badge from '@/components/Badge.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import ProfileThreadClient from '@/services/api/ProfileThreadClient';
import FormattingService from '@/services/FormattingService';
import { useTableView } from '@/composables/useTableView';
import type {
  FrameStat,
  ParsedDump,
  ThreadDumpAnalysis,
  ThreadLockKind,
  ThreadState
} from '@/services/api/model/ThreadDumpModels';
import type { Variant } from '@/types/ui';

const route = useRoute();

const loading = ref(true);
const error = ref(false);
const data = ref<ThreadDumpAnalysis>();
const activeTab = ref('timeline');

let client: ProfileThreadClient | null = null;
let chart: ApexCharts | null = null;

const legendStates: ThreadState[] = ['RUNNABLE', 'BLOCKED', 'WAITING', 'TIMED_WAITING', 'NEW', 'TERMINATED', 'UNKNOWN'];

const STATE_COLORS: Record<ThreadState, string> = {
  RUNNABLE: '#34A853',
  BLOCKED: '#EA4335',
  WAITING: '#4285F4',
  TIMED_WAITING: '#FBBC04',
  NEW: '#9AA0A6',
  TERMINATED: '#5F6368',
  UNKNOWN: '#DADCE0'
};
const stateColor = (state: ThreadState): string => STATE_COLORS[state] ?? STATE_COLORS.UNKNOWN;

const stateVariant = (state: ThreadState): Variant => {
  switch (state) {
    case 'RUNNABLE':
      return 'success';
    case 'BLOCKED':
      return 'danger';
    case 'WAITING':
      return 'info';
    case 'TIMED_WAITING':
      return 'warning';
    default:
      return 'secondary';
  }
};

const lockLabel = (kind: ThreadLockKind): string => {
  switch (kind) {
    case 'LOCKED':
      return 'locked';
    case 'WAITING_TO_LOCK':
      return 'waiting to lock';
    case 'PARKING_TO_WAIT':
      return 'parking to wait for';
    default:
      return 'waiting on';
  }
};

const formatOffset = (millis: number): string => FormattingService.formatDuration2Units(millis * 1_000_000);

const tabs = [
  { id: 'timeline', label: 'Timeline', icon: 'activity' },
  { id: 'activity', label: 'Activity', icon: 'list-ol' },
  { id: 'locks', label: 'Locks & Deadlocks', icon: 'lock' },
  { id: 'stuck', label: 'Stuck Threads', icon: 'hourglass-split' },
  { id: 'heatmap', label: 'Heatmap', icon: 'grid-3x3' },
  { id: 'browse', label: 'Browse', icon: 'search' },
  { id: 'about', label: 'About', icon: 'info-circle' }
];

const hasData = computed(() => (data.value?.header.dumpCount ?? 0) > 0);

const framesSource = computed<FrameStat[]>(() => data.value?.topFrames ?? []);
const framesView = useTableView<FrameStat>(framesSource, { searchableText: (f) => f.frame });

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'file-earmark-text',
      title: 'Thread Dumps',
      value: FormattingService.formatNumber(h.dumpCount),
      variant: 'highlight' as const,
      breakdown: [{ label: 'Peak Threads', value: FormattingService.formatNumber(h.peakThreadCount) }]
    },
    {
      icon: 'exclamation-octagon',
      title: 'Deadlocks',
      value: FormattingService.formatNumber(h.deadlockCount),
      variant: h.deadlockCount > 0 ? ('danger' as const) : ('success' as const)
    },
    {
      icon: 'hourglass-split',
      title: 'Stuck Threads',
      value: FormattingService.formatNumber(h.stuckThreadCount),
      variant: h.stuckThreadCount > 0 ? ('warning' as const) : ('success' as const)
    }
  ];
});

// ----- State timeline chart (custom stacked area: one series per state) -----
const renderChart = async () => {
  if (!hasData.value) {
    return;
  }
  await nextTick();
  const element = document.getElementById('thread-dump-state-chart');
  if (!element) {
    return;
  }
  const series = (data.value?.stateTimeline.series ?? []).map((s) => ({ name: s.name, data: s.data }));
  const colors = (data.value?.stateTimeline.series ?? []).map((s) => stateColor(s.name as ThreadState));

  const options = {
    chart: { type: 'area' as const, height: 380, stacked: true, fontFamily: 'inherit', toolbar: { show: false } },
    series,
    colors,
    dataLabels: { enabled: false },
    stroke: { curve: 'stepline' as const, width: 1 },
    fill: { type: 'solid', opacity: 0.55 },
    xaxis: {
      type: 'numeric' as const,
      title: { text: 'Time', style: { fontSize: '12px' } },
      labels: {
        style: { fontSize: '10px' },
        formatter: (value: string | number) => FormattingService.formatDuration2Units(Number(value) * 1e9)
      }
    },
    yaxis: { title: { text: 'Threads', style: { fontSize: '12px' } }, labels: { style: { fontSize: '10px' } } },
    legend: { position: 'bottom' as const },
    grid: { borderColor: '#e7e7e7', strokeDashArray: 3 }
  } as ApexCharts.ApexOptions;

  if (chart) {
    chart.destroy();
  }
  chart = new ApexCharts(element, options);
  chart.render();
};

// ----- Browse: lazily load a single parsed dump -----
const selectedIndex = ref(0);
const selectedDump = ref<ParsedDump>();
const dumpLoading = ref(false);
const showRaw = ref(false);
const browseQuery = ref('');
const browseStateFilter = ref<'' | ThreadState>('');

const browseThreads = computed(() => {
  const threads = selectedDump.value?.threads ?? [];
  const query = browseQuery.value.trim().toLowerCase();
  return threads.filter((t) => {
    if (browseStateFilter.value && t.state !== browseStateFilter.value) {
      return false;
    }
    return query === '' || t.name.toLowerCase().includes(query);
  });
});

const loadDump = async () => {
  if (!client) {
    return;
  }
  try {
    dumpLoading.value = true;
    selectedDump.value = await client.dump(selectedIndex.value);
  } catch (err) {
    console.error('Error loading thread dump:', err);
  } finally {
    dumpLoading.value = false;
  }
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = false;
    client = new ProfileThreadClient(route.params.profileId as string);
    data.value = await client.dumps();
    if (activeTab.value === 'timeline') {
      renderChart();
    }
  } catch (err) {
    error.value = true;
    console.error('Error loading thread dumps:', err);
  } finally {
    loading.value = false;
  }
};

watch(activeTab, (tab) => {
  if (tab === 'timeline') {
    renderChart();
  } else if (tab === 'browse' && !selectedDump.value && (data.value?.dumps.length ?? 0) > 0) {
    loadDump();
  }
});

onMounted(loadData);
onUnmounted(() => {
  if (chart) {
    chart.destroy();
    chart = null;
  }
});
</script>

<style scoped>
.chart-container {
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 1rem;
}

.section-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
  margin-bottom: 0.5rem;
}

.muted {
  color: var(--color-text-muted);
  font-weight: 400;
  text-transform: none;
}

.deadlock-card {
  border: 1px solid var(--color-danger-border-light);
  border-radius: var(--radius-md);
  background: var(--color-danger-bg-light);
  overflow: hidden;
}

.deadlock-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  font-weight: 600;
  color: var(--color-danger);
}

.deadlock-threads {
  margin-left: auto;
  font-weight: 500;
  font-size: 0.85rem;
}

.deadlock-body {
  margin: 0;
  padding: 0.75rem;
  font-size: 0.8rem;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Heatmap */
.heatmap-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin: 0.5rem 0 0.75rem;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.legend-swatch {
  width: 12px;
  height: 12px;
  border-radius: var(--radius-sm);
}

.heatmap-scroll {
  overflow-x: auto;
}

.heatmap-grid {
  --heatmap-label-w: 220px;
  display: grid;
  gap: 2px;
  align-items: center;
}

.heatmap-label {
  font-size: 0.75rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 0.5rem;
}

.heatmap-cell {
  width: 16px;
  height: 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-light);
}

/* Browse */
.browse-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
  font-size: 0.85rem;
}

.browse-search {
  max-width: 18rem;
}

.raw-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--color-text-muted);
  cursor: pointer;
}

.raw-dump {
  font-size: 0.78rem;
  max-height: 70vh;
  overflow: auto;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.75rem;
}

.thread-block {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.4rem 0.6rem;
  margin-bottom: 0.4rem;
}

.thread-block summary {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}

.thread-name {
  font-weight: 600;
}

.thread-top {
  color: var(--color-text-muted);
  font-size: 0.78rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thread-stack {
  margin: 0.5rem 0 0;
  font-size: 0.78rem;
  white-space: pre-wrap;
}

.thread-locks {
  margin: 0.4rem 0 0;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}
</style>
