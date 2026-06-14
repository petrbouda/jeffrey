<template>
  <div class="stw-container">
    <LoadingState v-if="loading" message="Loading stop-the-world data..." />
    <ErrorState v-else-if="error" message="Failed to load stop-the-world data" />

    <div v-else>
      <PageHeader
        title="Stop-The-World"
        description="Every JVM pause on one timeline — GC pauses, safepoint operations, time-to-safepoint, and per-thread stalls — with the app-stop budget and click-to-explain drill-down"
        icon="bi-pause-circle"
      />

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Timeline -->
      <div v-show="activeTab === 'timeline'">
        <EmptyState
          v-if="events.length === 0"
          icon="bi-pause-circle"
          title="No stop-the-world pauses recorded"
          description="This recording has no GC pauses, safepoint operations, or thread stalls above the display threshold."
        />
        <template v-else>
          <ChartDescription
            shows="The app-stop budget (frozen nanoseconds per second: whole-JVM 'Global STW' vs per-thread 'Local Stalls') above a swimlane of every individual pause."
            use-case="Find when and why the app was frozen: brush the budget to zoom, then click any pause to see what else happened in that window."
          />

          <ChartSection title="App-Stop Budget" icon="bi-hourglass-split" :full-width="true">
            <TimeSeriesChart
              :primaryData="globalBudget"
              primaryTitle="Global STW"
              :secondaryData="localBudget"
              secondaryTitle="Local Stalls"
              :primaryAxisType="AxisFormatType.DURATION_IN_NANOS"
              :secondaryAxisType="AxisFormatType.DURATION_IN_NANOS"
              :visibleMinutes="60"
              zoomEnabled
              @update:timeRange="onTimeRange"
            />
          </ChartSection>

          <StwLaneLegend :events="events" />
          <StwSwimlane :events="visibleEvents" @select="openDrawer" />

          <div class="rails mt-4">
            <ChartSection title="Machine CPU" icon="bi-cpu">
              <TimeSeriesChart
                :primaryData="cpuSeries"
                primaryTitle="Machine CPU"
                :primaryAxisType="AxisFormatType.PERCENT_IN_HUNDREDTHS"
                :visibleMinutes="60"
              />
            </ChartSection>
            <ChartSection title="Context Switches" icon="bi-arrow-left-right">
              <TimeSeriesChart
                :primaryData="ctxSeries"
                primaryTitle="Context Switches / sec"
                :primaryAxisType="AxisFormatType.NUMBER"
                :visibleMinutes="60"
              />
            </ChartSection>
          </div>
        </template>
      </div>

      <!-- Inventory -->
      <div v-show="activeTab === 'inventory'">
        <EmptyState
          v-if="events.length === 0"
          icon="bi-table"
          title="No pauses to list"
          description="This recording has no stop-the-world pauses above the display threshold."
        />
        <template v-else>
          <div class="inventory-cards">
            <ChartSection title="Longest Pauses" icon="bi-trophy">
              <div class="table-responsive">
                <table class="table table-sm table-hover mb-0">
                  <thead>
                    <tr>
                      <th>Category</th>
                      <th>Cause</th>
                      <th class="text-end">Duration</th>
                      <th class="text-end">At</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(event, index) in leaderboard" :key="index" @click="openDrawer(event)" role="button">
                      <td><Badge :value="laneFor(event.category).label" :variant="badgeVariant(event.scope)" size="xs" borderless /></td>
                      <td class="cause-cell" :title="event.label">{{ event.label }}</td>
                      <td class="text-end">{{ FormattingService.formatDuration2Units(event.durationNanos) }}</td>
                      <td class="text-end">+{{ FormattingService.formatDurationMillisCoarse(event.timeOffsetMillis) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </ChartSection>

            <ChartSection title="Cause Attribution" icon="bi-pie-chart">
              <div class="table-responsive">
                <table class="table table-sm table-hover mb-0">
                  <thead>
                    <tr>
                      <th>Category</th>
                      <th>Cause</th>
                      <th class="text-end">Count</th>
                      <th class="text-end">Total Time</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, index) in causeAttribution" :key="index">
                      <td><Badge :value="laneFor(row.category).label" :variant="badgeVariant(laneFor(row.category).scope)" size="xs" borderless /></td>
                      <td class="cause-cell" :title="row.label">{{ row.label }}</td>
                      <td class="text-end">{{ FormattingService.formatNumber(row.count) }}</td>
                      <td class="text-end">{{ FormattingService.formatDuration2Units(row.totalNanos) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </ChartSection>
          </div>

          <DataTable class="mt-4">
            <template #toolbar>
              <TableToolbar v-model="inventoryView.query" search-placeholder="Filter pauses...">
                <span class="toolbar-info">All pauses</span>
                <template #filters>
                  <Badge key-label="Total" :value="inventoryView.matchCount" variant="secondary" size="s" borderless />
                </template>
              </TableToolbar>
            </template>
            <thead>
              <tr>
                <th>At</th>
                <th>Category</th>
                <th>Cause</th>
                <th class="text-end">Duration</th>
                <th>Thread</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(event, index) in inventoryView.visible" :key="index" @click="openDrawer(event)" role="button">
                <td>+{{ FormattingService.formatDurationMillisCoarse(event.timeOffsetMillis) }}</td>
                <td><Badge :value="laneFor(event.category).label" :variant="badgeVariant(event.scope)" size="xs" borderless /></td>
                <td class="cause-cell" :title="event.label">{{ event.label }}</td>
                <td class="text-end">{{ FormattingService.formatDuration2Units(event.durationNanos) }}</td>
                <td class="text-muted">{{ event.thread ?? '—' }}</td>
              </tr>
            </tbody>
            <template #footer>
              <TableShowMore
                :shown="inventoryView.visible.length"
                :match-count="inventoryView.matchCount"
                :total="inventoryView.total"
                :expanded="inventoryView.expanded"
                :page-size="inventoryView.pageSize"
                @toggle="inventoryView.toggle"
              />
            </template>
          </DataTable>
        </template>
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Stop-The-World"
          subtitle="Why your application threads stop, and how to read this page"
        >
          <AboutCallout variant="intro">
            <p>
              A "stop-the-world" (STW) pause freezes <em>every</em> application thread while the JVM does
              something it can't do concurrently — a GC pause, or a safepoint operation. This page merges
              all of those onto one axis, plus per-thread stalls, so you can see what froze the app and when.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-layers" title="The Lanes">
            <FeatureGrid>
              <FeatureCard icon="bi-recycle" variant="warning" title="GC Pause">
                Stop-the-world collection time (uses sum-of-pauses, so concurrent GC time is excluded).
              </FeatureCard>
              <FeatureCard icon="bi-stopwatch" variant="danger" title="VM Operation">
                Safepoint operations (the JVM stopped to run an internal operation).
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-split" variant="primary" title="Time to Safepoint">
                How long the JVM waited for all threads to reach the safepoint — latency to <em>stop</em>,
                shown separately and excluded from the budget to avoid double-counting.
              </FeatureCard>
              <FeatureCard icon="bi-lock" variant="info" title="Local Stalls">
                Per-thread (not whole-JVM): monitor contention, thread parking, virtual-thread pinning.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li><code>jdk.GarbageCollection</code> — GC pause time per collection.</li>
              <li><code>jdk.ExecuteVMOperation</code> — safepoint VM operations.</li>
              <li><code>jdk.SafepointStateSynchronization</code> — time-to-safepoint.</li>
              <li><code>jdk.JavaMonitorEnter</code>, <code>jdk.ThreadPark</code>, <code>jdk.VirtualThreadPinned</code> — per-thread stalls.</li>
            </ul>
            <p>ZGC allocation stalls will join as a lane once that event support lands.</p>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>

    <!-- Click-a-pause drawer -->
    <GenericModal modal-id="stwDetailModal" :show="showDrawer" size="lg" :show-footer="false" @update:show="showDrawer = $event">
      <template #title>Pause detail</template>
      <div v-if="selectedEvent" class="drawer-body">
        <div class="drawer-grid">
          <div><span class="drawer-key">Category</span><Badge :value="laneFor(selectedEvent.category).label" :variant="badgeVariant(selectedEvent.scope)" size="s" borderless /></div>
          <div><span class="drawer-key">Scope</span>{{ selectedEvent.scope }}</div>
          <div><span class="drawer-key">Cause</span>{{ selectedEvent.label }}</div>
          <div><span class="drawer-key">Duration</span>{{ FormattingService.formatDuration2Units(selectedEvent.durationNanos) }}</div>
          <div><span class="drawer-key">At</span>+{{ FormattingService.formatDurationMillisCoarse(selectedEvent.timeOffsetMillis) }}</div>
          <div v-if="selectedEvent.thread"><span class="drawer-key">Thread</span>{{ selectedEvent.thread }}</div>
          <div v-if="selectedEvent.gcId !== null"><span class="drawer-key">GC ID</span>{{ selectedEvent.gcId }}</div>
        </div>

        <h6 class="mt-3 mb-2">Concurrent in this window</h6>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr><th>Category</th><th>Cause</th><th class="text-end">Duration</th><th class="text-end">At</th></tr>
            </thead>
            <tbody>
              <tr v-for="(event, index) in concurrentEvents" :key="index">
                <td><Badge :value="laneFor(event.category).label" :variant="badgeVariant(event.scope)" size="xs" borderless /></td>
                <td class="cause-cell" :title="event.label">{{ event.label }}</td>
                <td class="text-end">{{ FormattingService.formatDuration2Units(event.durationNanos) }}</td>
                <td class="text-end">+{{ FormattingService.formatDurationMillisCoarse(event.timeOffsetMillis) }}</td>
              </tr>
              <tr v-if="concurrentEvents.length === 0">
                <td colspan="4" class="text-muted text-center">No other pauses overlap this window.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </GenericModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import TabBar from '@/components/TabBar.vue';
import type { TabBarItem } from '@/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import GenericModal from '@/components/GenericModal.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import StwSwimlane from '@/components/stw/StwSwimlane.vue';
import StwLaneLegend from '@/components/stw/StwLaneLegend.vue';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import { useTableView } from '@/composables/useTableView';
import { laneFor } from '@/services/stw/stwLanes';
import ProfileStwClient from '@/services/api/ProfileStwClient';
import ProfileSystemClient from '@/services/api/ProfileSystemClient';
import type { StwEvent, StwScope } from '@/services/api/model/stw/StwModels';
import type { Variant } from '@/types/ui';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

const route = useRoute();

const loading = ref(true);
const error = ref(false);
const activeTab = ref('timeline');

const events = ref<StwEvent[]>([]);
const budget = ref<TimeseriesData>();
const cpu = ref<TimeseriesData>();
const ctx = ref<TimeseriesData>();

const selectedEvent = ref<StwEvent | null>(null);
const showDrawer = ref(false);
const visibleRange = ref<{ start: number; end: number } | null>(null);

const MILLIS_PER_SECOND = 1000;
const NANOS_PER_MILLI = 1_000_000;

const tabs: TabBarItem[] = [
  { id: 'timeline', label: 'Timeline', icon: 'bar-chart-steps' },
  { id: 'inventory', label: 'Inventory', icon: 'table' },
  { id: 'about', label: 'How It Works', icon: 'book' }
];

const globalBudget = computed<number[][]>(() => budget.value?.series?.[0]?.data ?? []);
const localBudget = computed<number[][]>(() => budget.value?.series?.[1]?.data ?? []);
const cpuSeries = computed<number[][]>(() => cpu.value?.series?.[0]?.data ?? []);
const ctxSeries = computed<number[][]>(() => ctx.value?.series?.[0]?.data ?? []);

// Brush selection (budget x-axis is per-second) filters the swimlane; falls back to all on any mismatch.
const visibleEvents = computed<StwEvent[]>(() => {
  const range = visibleRange.value;
  if (!range) {
    return events.value;
  }
  const startMs = range.start * MILLIS_PER_SECOND;
  const endMs = range.end * MILLIS_PER_SECOND;
  const filtered = events.value.filter(
    (event) => event.timeOffsetMillis >= startMs && event.timeOffsetMillis <= endMs
  );
  return filtered.length > 0 ? filtered : events.value;
});

const leaderboard = computed<StwEvent[]>(() =>
  [...events.value].sort((a, b) => b.durationNanos - a.durationNanos).slice(0, 15)
);

interface CauseRow {
  category: StwEvent['category'];
  label: string;
  count: number;
  totalNanos: number;
}

const causeAttribution = computed<CauseRow[]>(() => {
  const byCause = new Map<string, CauseRow>();
  for (const event of events.value) {
    const key = `${event.category}::${event.label}`;
    const existing = byCause.get(key);
    if (existing) {
      existing.count += 1;
      existing.totalNanos += event.durationNanos;
    } else {
      byCause.set(key, { category: event.category, label: event.label, count: 1, totalNanos: event.durationNanos });
    }
  }
  return [...byCause.values()].sort((a, b) => b.totalNanos - a.totalNanos).slice(0, 25);
});

const concurrentEvents = computed<StwEvent[]>(() => {
  const selected = selectedEvent.value;
  if (!selected) {
    return [];
  }
  const start = selected.timeOffsetMillis;
  const end = start + selected.durationNanos / NANOS_PER_MILLI;
  return events.value.filter((event) => {
    if (event === selected) {
      return false;
    }
    const eventStart = event.timeOffsetMillis;
    const eventEnd = eventStart + event.durationNanos / NANOS_PER_MILLI;
    return eventStart <= end && eventEnd >= start;
  });
});

const inventoryView = useTableView<StwEvent>(() => events.value, {
  searchableText: (row) => `${laneFor(row.category).label} ${row.label} ${row.thread ?? ''}`
});

function badgeVariant(scope: StwScope): Variant {
  return scope === 'GLOBAL' ? 'danger' : 'warning';
}

function onTimeRange(payload: { start: number; end: number; isZoomed: boolean }) {
  visibleRange.value = payload.isZoomed ? { start: payload.start, end: payload.end } : null;
}

function openDrawer(event: StwEvent) {
  selectedEvent.value = event;
  showDrawer.value = true;
}

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const stwClient = new ProfileStwClient(profileId);
    const systemClient = new ProfileSystemClient(profileId);

    const [timelineResult, budgetResult, cpuResult, ctxResult] = await Promise.all([
      stwClient.getTimeline(),
      stwClient.getBudget(),
      systemClient.getCpuTimeline(),
      systemClient.getContextSwitchTimeline()
    ]);

    events.value = timelineResult;
    budget.value = budgetResult;
    cpu.value = cpuResult;
    ctx.value = ctxResult;
    loading.value = false;
  } catch (e) {
    console.error('Failed to load stop-the-world data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.stw-container {
  width: 100%;
  color: var(--color-text);
}

.rails {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.inventory-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.cause-cell {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem 1.5rem;
}

.drawer-key {
  display: block;
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
}

@media (max-width: 992px) {
  .rails,
  .inventory-cards {
    grid-template-columns: 1fr;
  }
}
</style>
