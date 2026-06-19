<template>
  <div class="latency-container">
    <LoadingState v-if="loading" message="Loading blocking operations data..." />
    <ErrorState v-else-if="error" message="Failed to load blocking operations data" />

    <div v-else>
      <PageHeader
        title="Blocking Operations"
        description="Where application threads block — lock contention, Object.wait(), parks, sleeps and virtual-thread pinning"
        icon="bi-lock"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Occurrences -->
      <div v-show="activeTab === 'occurrences'">
        <ChartDescription
          shows="Blocking-event occurrences per second, one line per type."
          use-case="Spot when — and which kind of — blocking spikes, and whether different blocking types correlate."
        />
        <div class="chart-container">
          <div class="chart-toolbar">
            <button
              type="button"
              class="btn btn-sm btn-outline-secondary"
              title="Reset zoom to the entire range"
              @click="resetOccurrencesZoom"
            >
              <i class="bi bi-arrows-angle-expand me-1"></i>Reset zoom
            </button>
          </div>
          <div id="blocking-occurrences-chart"></div>
        </div>
      </div>

      <!-- Lock Contention -->
      <div v-show="activeTab === 'monitors'">
        <EmptyState
          v-if="monitors.length === 0"
          icon="bi-lock"
          title="No contended monitors recorded"
          description="No jdk.JavaMonitorEnter events — either no contention happened above the recording threshold, or the event is disabled in the JFR configuration."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="monitorsView.query" search-placeholder="Filter monitors...">
              <span class="toolbar-info">Contended monitors</span>
              <template #filters>
                <Badge key-label="Total" :value="monitorsView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Monitor Class</th>
              <th class="text-end">Contended Enters</th>
              <th class="text-end">Total Blocked</th>
              <th class="text-end">Max Blocked</th>
              <th class="text-end">Threads</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="monitor in monitorsView.visible" :key="monitor.className">
              <td class="class-cell" :title="monitor.className">
                <ClassNameDisplay :class-name="monitor.className" />
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(monitor.count) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(monitor.totalNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(monitor.maxNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(monitor.threadCount) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="monitorsView.visible.length"
              :match-count="monitorsView.matchCount"
              :total="monitorsView.total"
              :expanded="monitorsView.expanded"
              :page-size="monitorsView.pageSize"
              @toggle="monitorsView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Monitor Waits -->
      <div v-show="activeTab === 'waits'">
        <EmptyState
          v-if="monitorWaits.length === 0"
          icon="bi-hourglass-split"
          title="No monitor waits recorded"
          description="No jdk.JavaMonitorWait events — no Object.wait() calls blocked above the recording threshold."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="monitorWaitsView.query" search-placeholder="Filter monitors...">
              <span class="toolbar-info">Object.wait() by monitor</span>
              <template #filters>
                <Badge key-label="Total" :value="monitorWaitsView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Monitor Class</th>
              <th class="text-end">Waits</th>
              <th class="text-end">Total Wait</th>
              <th class="text-end">Max Wait</th>
              <th class="text-end">Timed Out</th>
              <th class="text-end">Threads</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="wait in monitorWaitsView.visible" :key="wait.className">
              <td class="class-cell" :title="wait.className">
                <ClassNameDisplay :class-name="wait.className" />
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(wait.count) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(wait.totalNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(wait.maxNanos) }}</td>
              <td class="text-end">
                <Badge
                  v-if="wait.timedOutCount > 0"
                  :value="FormattingService.formatNumber(wait.timedOutCount)"
                  variant="warning"
                  size="xs"
                  borderless
                />
                <span v-else class="text-muted">—</span>
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(wait.threadCount) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="monitorWaitsView.visible.length"
              :match-count="monitorWaitsView.matchCount"
              :total="monitorWaitsView.total"
              :expanded="monitorWaitsView.expanded"
              :page-size="monitorWaitsView.pageSize"
              @toggle="monitorWaitsView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Thread Parks -->
      <div v-show="activeTab === 'parks'">
        <EmptyState
          v-if="parks.length === 0"
          icon="bi-pause-circle"
          title="No thread parks recorded"
          description="No jdk.ThreadPark events above the recording threshold."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="parksView.query" search-placeholder="Filter blockers...">
              <span class="toolbar-info">Park blockers</span>
              <template #filters>
                <Badge key-label="Total" :value="parksView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Blocker Class</th>
              <th class="text-end">Parks</th>
              <th class="text-end">Total Parked</th>
              <th class="text-end">Max Parked</th>
              <th class="text-end">Threads</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="park in parksView.visible" :key="park.className">
              <td class="class-cell" :title="park.className">
                <ClassNameDisplay :class-name="park.className" />
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(park.count) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(park.totalNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(park.maxNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(park.threadCount) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="parksView.visible.length"
              :match-count="parksView.matchCount"
              :total="parksView.total"
              :expanded="parksView.expanded"
              :page-size="parksView.pageSize"
              @toggle="parksView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Thread Sleeps -->
      <div v-show="activeTab === 'sleeps'">
        <EmptyState
          v-if="sleeps.length === 0"
          icon="bi-moon"
          title="No thread sleeps recorded"
          description="No jdk.ThreadSleep events above the recording threshold — no Thread.sleep() calls were sampled."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="sleepsView.query" search-placeholder="Filter threads...">
              <span class="toolbar-info">Thread.sleep() by thread</span>
              <template #filters>
                <Badge key-label="Total" :value="sleepsView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Thread</th>
              <th class="text-end">Sleeps</th>
              <th class="text-end">Total Slept</th>
              <th class="text-end">Max Slept</th>
              <th class="text-end">Requested</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="sleep in sleepsView.visible" :key="sleep.thread">
              <td class="operation-name">{{ sleep.thread }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(sleep.count) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(sleep.totalSleptNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(sleep.maxSleptNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(sleep.requestedNanos) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="sleepsView.visible.length"
              :match-count="sleepsView.matchCount"
              :total="sleepsView.total"
              :expanded="sleepsView.expanded"
              :page-size="sleepsView.pageSize"
              @toggle="sleepsView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Virtual Threads -->
      <div v-show="activeTab === 'virtual-threads'">
        <DisabledEventsNotice
          v-if="pinned.length === 0"
          title="No virtual-thread pinning recorded"
          action-label="Capture shorter pins"
          command="settings=profile,jdk.VirtualThreadPinned#threshold=0ms"
        >
          <code>jdk.VirtualThreadPinned</code> is emitted only when a virtual thread stays pinned to
          its carrier longer than the threshold (20ms by default). This recording has none — the
          application may not use virtual threads, or no pin exceeded the threshold.
        </DisabledEventsNotice>
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="pinnedView.query" search-placeholder="Filter threads...">
              <span class="toolbar-info">Pinning incidents</span>
              <template #filters>
                <Badge key-label="Total" :value="pinnedView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Virtual Thread</th>
              <th class="text-end">Pinned Duration</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(entry, index) in pinnedView.visible" :key="index">
              <td class="operation-name">{{ entry.thread ?? '—' }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(entry.durationNanos) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="pinnedView.visible.length"
              :match-count="pinnedView.matchCount"
              :total="pinnedView.total"
              :expanded="pinnedView.expanded"
              :page-size="pinnedView.pageSize"
              @toggle="pinnedView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Blocking Operations"
          subtitle="Where application threads wait — locks, waits, parks, sleeps and pinning"
        >
          <AboutCallout variant="intro">
            <p>
              A blocked thread isn't using CPU, so it's invisible to a flame graph — yet it's often
              exactly where latency hides. This page breaks down <em>why</em> threads block: contending
              for a lock, waiting to be notified, parking on a <code>j.u.c</code> primitive, sleeping,
              or (for virtual threads) pinning their carrier.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-lock" title="Monitor Locks (synchronized)">
            <FeatureGrid>
              <FeatureCard icon="bi-unlock" variant="success" title="Uncontended → thin lock">
                With no contention, a <code>synchronized</code> lock is a cheap CAS on the object header
                (a "thin"/stack lock). No event is recorded — this is the fast path.
              </FeatureCard>
              <FeatureCard icon="bi-lock-fill" variant="danger" title="Contended → inflation">
                When a second thread contends, the lock <strong>inflates</strong> to a heavyweight
                <code>ObjectMonitor</code> with an OS-backed wait queue. Threads waiting to acquire it
                are <code>jdk.JavaMonitorEnter</code> events — the Lock Contention tab.
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-split" variant="warning" title="wait() / notify()">
                <code>Object.wait()</code> releases the monitor and parks the thread on the monitor's
                wait set until <code>notify()</code> (or a timeout). The Monitor Waits tab's timed-out
                count flags waits that hit their timeout — often a missed signal.
              </FeatureCard>
              <FeatureCard icon="bi-diagram-3" variant="info" title="Usual suspects">
                Connection pools, logging frameworks, and synchronized singletons/caches. A single
                monitor class dominating total blocked time is the thing to make lock-free or shard.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-pause-circle" title="Parks, Sleeps &amp; Pinning">
            <FeatureGrid>
              <FeatureCard icon="bi-pause-circle" variant="primary" title="LockSupport.park">
                The primitive behind <code>java.util.concurrent</code>. <code>ReentrantLock</code>,
                <code>Semaphore</code>, <code>CountDownLatch</code> and blocking queues all park threads
                via <code>AbstractQueuedSynchronizer</code>. The blocker class names the lock.
              </FeatureCard>
              <FeatureCard icon="bi-moon" variant="neutral" title="Thread.sleep">
                Explicit timed blocking, grouped by thread. Large totals usually mean a polling loop
                that should be event-driven (a queue, a condition, a callback).
              </FeatureCard>
              <FeatureCard icon="bi-pin-angle" variant="purple" title="Virtual-thread pinning">
                A virtual thread normally unmounts from its carrier platform thread while blocked. If
                it's inside a <code>synchronized</code> block or a native frame it can't unmount — it
                <strong>pins</strong> the carrier, defeating the scalability of virtual threads.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-repeat" variant="success" title="Fixing pinning">
                Replace <code>synchronized</code> around blocking calls with <code>ReentrantLock</code>
                (which parks cleanly). JDK 24+ also removes most <code>synchronized</code> pinning.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <p>All of these are <strong>threshold-gated</strong> — JFR records an event only when the block exceeds the configured duration, so brief blocking won't appear:</p>
            <ul>
              <li><code>jdk.JavaMonitorEnter</code> — contended <code>synchronized</code> acquisition, by monitor class.</li>
              <li><code>jdk.JavaMonitorWait</code> — <code>Object.wait()</code>, with a timed-out flag.</li>
              <li><code>jdk.ThreadPark</code> — <code>LockSupport.park</code>, by blocker class.</li>
              <li><code>jdk.ThreadSleep</code> — <code>Thread.sleep()</code>, with requested vs actual time.</li>
              <li><code>jdk.VirtualThreadPinned</code> — a pin exceeding its threshold (20ms by default).</li>
            </ul>
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
import type { TabBarItem } from '@/components/TabBar.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import FormattingService from '@shared/services/FormattingService';
import ProfileBlockingOperationsClient from '@/services/api/ProfileBlockingOperationsClient';
import { useTableView } from '@/composables/useTableView';
import type {
  BlockingOverview,
  ContentionStat,
  MonitorWaitStat,
  PinnedThreadEntry,
  SleepStat
} from '@/services/api/model/BlockingModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const overview = ref<BlockingOverview>();
const timeline = ref<TimeseriesData>();
const monitors = ref<ContentionStat[]>([]);
const parks = ref<ContentionStat[]>([]);
const pinned = ref<PinnedThreadEntry[]>([]);
const monitorWaits = ref<MonitorWaitStat[]>([]);
const sleeps = ref<SleepStat[]>([]);

const activeTab = ref('occurrences');

const monitorsView = useTableView<ContentionStat>(monitors, {
  searchableText: r => r.className
});
const parksView = useTableView<ContentionStat>(parks, {
  searchableText: r => r.className
});
const pinnedView = useTableView<PinnedThreadEntry>(pinned, {
  searchableText: r => r.thread ?? ''
});
const monitorWaitsView = useTableView<MonitorWaitStat>(monitorWaits, {
  searchableText: r => r.className
});
const sleepsView = useTableView<SleepStat>(sleeps, {
  searchableText: r => r.thread
});

const tabs = computed<TabBarItem[]>(() => [
  { id: 'occurrences', label: 'Occurrences', icon: 'graph-up' },
  {
    id: 'monitors',
    label: 'Lock Contention',
    icon: 'lock',
    badge: monitors.value.length || undefined
  },
  {
    id: 'waits',
    label: 'Monitor Waits',
    icon: 'hourglass-split',
    badge: monitorWaits.value.length || undefined
  },
  {
    id: 'parks',
    label: 'Thread Parks',
    icon: 'pause-circle',
    badge: parks.value.length || undefined
  },
  {
    id: 'sleeps',
    label: 'Thread Sleeps',
    icon: 'moon',
    badge: sleeps.value.length || undefined
  },
  {
    id: 'virtual-threads',
    label: 'Virtual Threads',
    icon: 'pin-angle',
    badge: pinned.value.length || undefined
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

let occurrencesChart: ApexCharts | null = null;

// One line per blocking type, occurrences/sec. x is seconds-from-start (as every Jeffrey timeline),
// formatted as a duration. Five series exceed TimeSeriesChart's 3-series limit, hence custom ApexCharts.
const createOccurrencesChart = async (): Promise<void> => {
  const rawSeries = timeline.value?.series ?? [];
  if (rawSeries.length === 0) {
    return;
  }

  await nextTick();
  const chartElement = document.getElementById('blocking-occurrences-chart');
  if (!chartElement) {
    return;
  }

  const series = rawSeries.map(serie => ({ name: serie.name, data: serie.data }));

  const options = {
    chart: {
      type: 'line' as const,
      height: 380,
      fontFamily: 'inherit',
      toolbar: { show: false },
      zoom: {
        enabled: true,
        type: 'x' as const,
        autoScaleYaxis: true
      }
    },
    series,
    dataLabels: { enabled: false },
    stroke: { curve: 'smooth' as const, width: 2 },
    xaxis: {
      type: 'numeric' as const,
      title: { text: 'Elapsed Time', style: { fontSize: '12px' } },
      labels: {
        style: { fontSize: '10px' },
        formatter: (value: string) => FormattingService.formatDuration(Number(value) * 1_000_000_000)
      }
    },
    yaxis: {
      title: { text: 'Occurrences / sec', style: { fontSize: '12px' } },
      labels: {
        style: { fontSize: '10px' },
        formatter: (value: number) => FormattingService.formatNumber(value)
      }
    },
    legend: { position: 'bottom' as const },
    tooltip: {
      x: { formatter: (value: number) => FormattingService.formatDuration(value * 1_000_000_000) }
    },
    grid: { borderColor: '#e7e7e7', strokeDashArray: 3 }
  } as ApexCharts.ApexOptions;

  if (occurrencesChart) {
    occurrencesChart.destroy();
  }
  occurrencesChart = new ApexCharts(chartElement, options);
  occurrencesChart.render();
};

// Clears any active x-axis zoom, returning the chart to its full starting range.
const resetOccurrencesZoom = (): void => {
  if (occurrencesChart) {
    occurrencesChart.updateOptions({ xaxis: { min: undefined, max: undefined } });
  }
};

watch(activeTab, newId => {
  if (newId === 'occurrences') {
    createOccurrencesChart();
  }
});

onUnmounted(() => {
  if (occurrencesChart) {
    occurrencesChart.destroy();
  }
});

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  return [
    {
      icon: 'lock',
      title: 'Monitor Blocked Time',
      value: FormattingService.formatDuration2Units(o.totalMonitorBlockedNanos),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Contended Classes', value: FormattingService.formatNumber(o.contendedMonitorCount) }
      ]
    },
    {
      icon: 'hourglass-split',
      title: 'Monitor Waits',
      value: FormattingService.formatNumber(o.waitCount),
      variant: 'warning' as const
    },
    {
      icon: 'pause-circle',
      title: 'Thread Parks',
      value: FormattingService.formatNumber(o.parkCount),
      variant: 'info' as const,
      breakdown: [{ label: 'Thread Sleeps', value: FormattingService.formatNumber(o.sleepCount) }]
    },
    {
      icon: 'pin-angle',
      title: 'Pinned Virtual Threads',
      value: FormattingService.formatNumber(o.pinnedCount),
      variant: 'success' as const
    }
  ];
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileBlockingOperationsClient(profileId);

    const [
      overviewResult,
      timelineResult,
      monitorsResult,
      parksResult,
      pinnedResult,
      waitsResult,
      sleepsResult
    ] = await Promise.all([
      client.getOverview(),
      client.getTimeline(),
      client.getMonitors(),
      client.getParks(),
      client.getPinned(),
      client.getMonitorWaits(),
      client.getSleeps()
    ]);

    overview.value = overviewResult;
    timeline.value = timelineResult;
    monitors.value = monitorsResult;
    parks.value = parksResult;
    pinned.value = pinnedResult;
    monitorWaits.value = waitsResult;
    sleeps.value = sleepsResult;

    loading.value = false;
    createOccurrencesChart();
  } catch (e) {
    console.error('Failed to load blocking operations data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.latency-container {
  width: 100%;
  color: var(--color-text);
}

.chart-container {
  width: 100%;
}

.chart-toolbar {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 0;
  padding-left: 60px;
}

.operation-name {
  font-weight: 500;
  max-width: 460px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.class-cell {
  max-width: 520px;
}
</style>
