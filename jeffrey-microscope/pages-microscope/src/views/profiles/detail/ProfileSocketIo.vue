<template>
  <div class="io-container">
    <LoadingState v-if="loading" message="Loading socket I/O data..." />
    <ErrorState v-else-if="error" message="Failed to load socket I/O data" />

    <div v-else>
      <PageHeader
        title="Socket I/O"
        description="Blocking network I/O — read/write throughput, the slowest operations, and the busiest peers"
        icon="bi-ethernet"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Total -->
      <div v-show="activeTab === 'total'">
        <MetricTileSwitch
          v-model="metric"
          :tiles="metricTiles"
          aria-label="Socket I/O metric"
          class="mb-3"
        />
        <ChartDescription :shows="metricDescription.shows" :use-case="metricDescription.useCase" />
        <div class="chart-container">
          <TimeSeriesChart
            :key="metric"
            :primaryData="primarySeries"
            :primaryTitle="primaryTitle"
            :secondaryData="secondarySeries"
            :secondaryTitle="secondaryTitle"
            :primaryAxisType="metricAxisType"
            :secondaryAxisType="metricAxisType"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Peer Timeline -->
      <div v-show="activeTab === 'peer-timeline'">
        <ChartDescription
          shows="Every busy peer as a sparkline; pick one for its full read/write timeline."
          use-case="Recognise the peer behind a spike by its shape, before reading a single name."
        />

        <EmptyState
          v-if="peerTimelines.length === 0"
          icon="bi-hdd-network"
          title="No peer traffic recorded"
          description="Per-peer timelines come from jdk.SocketRead and jdk.SocketWrite, which are threshold-gated in the bundled configs. Re-record with threshold=0ms to capture every operation."
        />
        <template v-else>
          <div class="gallery-toolbar">
            <span class="toolbar-info">Top peers by bytes</span>
            <Badge
              key-label="Showing"
              :value="`${peerTimelines.length} of ${peers.length}`"
              variant="secondary"
              size="s"
              borderless
            />
            <span class="toolbar-hint">Click a tile to open its timeline</span>
          </div>

          <div class="peer-gallery">
            <IoEndpointSparklineTile
              v-for="timeline in peerTimelines"
              :key="timeline.endpoint.target"
              :target="timeline.endpoint.target"
              :points="timeline.throughput.data"
              :bytes="timeline.endpoint.bytes"
              :share-of-bytes="shareOfBytes(timeline.endpoint.bytes)"
              :selected="timeline.endpoint.target === selectedPeer"
              @select="selectPeer"
            />
          </div>

          <div class="selected-peer-card">
            <div class="selected-peer-header">
              <Badge value="Single peer" variant="violet" size="xs" borderless />
              <span class="selected-peer-target" :title="selectedPeer ?? ''">{{ selectedPeer }}</span>
              <span class="selected-peer-summary">{{ selectedPeerSummary }}</span>
            </div>
            <!--
              v-if, not v-show: ApexCharts measures its container on mount, and this tab starts
              hidden — mounting the chart only once the tab is open keeps it from sizing to zero.
            -->
            <div class="selected-peer-body">
              <LoadingIndicator v-if="peerTimelineLoading" text="Loading peer timeline..." />
              <TimeSeriesChart
                v-else-if="activeTab === 'peer-timeline'"
                :key="selectedPeer ?? 'none'"
                :primaryData="selectedPeerReadSeries"
                primaryTitle="Bytes Read / sec"
                :secondaryData="selectedPeerWriteSeries"
                secondaryTitle="Bytes Written / sec"
                :primaryColor="peerReadColor"
                :secondaryColor="peerWriteColor"
                :primaryAxisType="AxisFormatType.BYTES"
                :secondaryAxisType="AxisFormatType.BYTES"
                :visibleMinutes="60"
              />
            </div>
          </div>
        </template>
      </div>

      <!-- Top Peers -->
      <div v-show="activeTab === 'peers'">
        <DisabledEventsNotice
          v-if="peers.length === 0"
          title="No socket I/O recorded"
          icon="bi-hdd-network"
          action-label="Un-gate the socket I/O events, then re-record and re-import"
          :command="ioEnableCommand"
        >
          <p>
            Per-peer totals come from <code>jdk.SocketRead</code> and <code>jdk.SocketWrite</code>. In
            the bundled <code>default</code>/<code>profile</code> configs these are
            <strong>enabled but threshold-gated</strong> (≈1&nbsp;ms) and throttled, so light or fast
            socket I/O leaves this empty — only operations that block longer than the threshold are
            recorded. The copyable command keeps the <code>profile</code> config and sets both events
            to <code>threshold=0ms</code> to capture every operation.
          </p>

          <template #action>
            <p>
              <strong>A — inline, no extra file.</strong> Use the copyable command above: it keeps the
              bundled <code>profile</code> config and adds <code>jdk.SocketRead#threshold=0ms</code> /
              <code>jdk.SocketWrite#threshold=0ms</code> so every read and write is captured.
            </p>
            <p>
              <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
              <code>socket-io.jfc</code> and record with
              <code>settings=profile,settings=socket-io.jfc</code>:
            </p>
            <pre class="jfc-block">{{ ioJfcSnippet }}</pre>
            <p>
              Re-import the <code>.jfr</code> afterwards. Raise the <code>threshold</code> (e.g. to
              <code>1ms</code>) to keep only the slow, latency-relevant operations — chatty or slow
              socket I/O is a classic source of tail latency against a slow or saturated peer.
            </p>
          </template>
        </DisabledEventsNotice>
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="peersView.query" search-placeholder="Filter peers...">
              <span class="toolbar-info">Socket peers</span>
              <template #filters>
                <Badge key-label="Total" :value="peersView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Peer (host:port)</th>
              <th class="text-end">Operations</th>
              <th class="text-end">Bytes</th>
              <th class="share-col">Share of Bytes</th>
              <th class="text-end">Total Time</th>
              <th class="text-end">Max Time</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="peer in peersView.visible" :key="peer.target">
              <td class="target-cell" :title="peer.target">{{ peer.target }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(peer.opCount) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(peer.bytes) }}</td>
              <td>
                <div class="share-bar">
                  <div class="share-bar-fill" :style="{ width: shareWidth(peer.bytes) + '%' }"></div>
                </div>
              </td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(peer.totalNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(peer.maxNanos) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="peersView.visible.length"
              :match-count="peersView.matchCount"
              :total="peersView.total"
              :expanded="peersView.expanded"
              :page-size="peersView.pageSize"
              @toggle="peersView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Slowest Operations -->
      <div v-show="activeTab === 'slowest'">
        <DisabledEventsNotice
          v-if="slowest.length === 0"
          title="No socket operations recorded"
          icon="bi-hourglass-split"
          action-label="Un-gate the socket I/O events, then re-record and re-import"
          :command="ioEnableCommand"
        >
          <p>
            The slowest-operations list is built from individual <code>jdk.SocketRead</code> /
            <code>jdk.SocketWrite</code> events, which are <strong>enabled but threshold-gated</strong>
            (≈1&nbsp;ms) and throttled in the bundled configs. With no qualifying operations the list
            stays empty — the command above captures every read/write by setting
            <code>threshold=0ms</code>.
          </p>
        </DisabledEventsNotice>
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="slowestView.query" search-placeholder="Filter operations...">
              <span class="toolbar-info">Slowest operations</span>
              <template #filters>
                <Badge key-label="Showing" :value="slowestView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Direction</th>
              <th>Peer</th>
              <th class="text-end">Bytes</th>
              <th class="text-end">Duration</th>
              <th>Thread</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(op, index) in slowestView.visible" :key="index">
              <td><Badge :value="op.kind" :variant="kindVariant(op.kind)" size="xs" borderless /></td>
              <td class="target-cell" :title="op.target">{{ op.target }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(op.bytes) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(op.durationNanos) }}</td>
              <td class="text-muted">{{ op.thread ?? '—' }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="slowestView.visible.length"
              :match-count="slowestView.matchCount"
              :total="slowestView.total"
              :expanded="slowestView.expanded"
              :page-size="slowestView.pageSize"
              @toggle="slowestView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Socket I/O"
          subtitle="Where blocking network reads and writes cost you latency"
        >
          <AboutCallout variant="intro">
            <p>
              A blocking socket read or write parks the calling thread until the kernel has data (or
              has accepted the bytes). That wait is real latency — and unlike CPU work, it doesn't show
              up in a flame graph. This page attributes that time to the peers (<code>host:port</code>)
              your application talks to.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-ethernet" title="What the Views Show">
            <FeatureGrid>
              <FeatureCard icon="bi-graph-up" variant="primary" title="Throughput">
                Bytes read vs written per second. A read plateau usually means you're waiting on a slow
                upstream; a write plateau means a slow or backpressured downstream.
              </FeatureCard>
              <FeatureCard icon="bi-hdd-network" variant="info" title="Top Peers">
                Every <code>host:port</code> ranked by bytes, with op count and total/max time. The
                share bar shows which peer dominates your network I/O.
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-split" variant="warning" title="Slowest Operations">
                Individual reads/writes ordered by duration — the long tail that hurts p99. A single
                multi-second read is often a connect/timeout to an unhealthy peer.
              </FeatureCard>
              <FeatureCard icon="bi-clock" variant="danger" title="Total vs Max time">
                High <em>total</em> time on a peer with low max = many small chatty calls (consider
                batching); high <em>max</em> = one slow call (a timeout or saturated link).
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.SocketRead</code> — host, address, port, <code>bytesRead</code>, timeout and
                an end-of-stream flag, plus the operation <code>duration</code>.
              </li>
              <li>
                <code>jdk.SocketWrite</code> — host, address, port and <code>bytesWritten</code> with
                duration.
              </li>
            </ul>
            <p>
              Both are <strong>threshold-gated</strong>: JFR records an event only when the operation
              blocks longer than the configured threshold, so very fast I/O won't appear.
            </p>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import TabBar from '@shared/components/TabBar.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import MetricTileSwitch from '@shared/components/MetricTileSwitch.vue';
import type { MetricTile } from '@shared/components/MetricTileSwitch.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartDescription from '@shared/components/ChartDescription.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import Badge from '@shared/components/Badge.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import IoEndpointSparklineTile from '@/components/io/IoEndpointSparklineTile.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import LoadingIndicator from '@shared/components/LoadingIndicator.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import ChartColors from '@shared/services/ChartColors';
import FormattingService from '@shared/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import { useTableView } from '@/composables/useTableView';
import ProfileSocketIoClient from '@/services/api/ProfileSocketIoClient';
import type {
  IoEndpoint,
  IoEndpointTimeline,
  IoOperation,
  IoOverview
} from '@/services/api/model/IoModels';
import type { Variant } from '@shared/types/ui';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

const route = useRoute();

const ioEnableCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=0ms,jdk.SocketWrite#enabled=true,jdk.SocketWrite#threshold=0ms,filename=app.jfr,dumponexit=true -jar app.jar';

const ioJfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.SocketRead">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
    <setting name="threshold">0 ms</setting>
  </event>
  <event name="jdk.SocketWrite">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
    <setting name="threshold">0 ms</setting>
  </event>
</configuration>`;

const loading = ref(true);
const error = ref(false);

const overview = ref<IoOverview>();
const timeline = ref<TimeseriesData>();
const slowest = ref<IoOperation[]>([]);
const peers = ref<IoEndpoint[]>([]);
const peerTimelines = ref<IoEndpointTimeline[]>([]);

// Peer Timeline tab: the gallery tile that is open, and its own read/write timeline. The gallery
// sparklines arrive with the tab, the full-resolution series only for the peer actually selected.
const selectedPeer = ref<string | null>(null);
const selectedPeerTimeline = ref<TimeseriesData>();
const peerTimelineLoading = ref(false);

const slowestView = useTableView<IoOperation>(slowest, {
  searchableText: (r) => `${r.target} ${r.thread ?? ''}`
});
const peersView = useTableView<IoEndpoint>(peers, {
  searchableText: (r) => r.target
});

const activeTab = ref('total');

// The /timeline payload carries both metrics at once — bytes in series 0/1, operation counts in
// series 2/3 — so switching is instant and needs no refetch.
type IoMetric = 'bytes' | 'count';

const metric = ref<IoMetric>('bytes');

const readSeries = computed<number[][]>(() => timeline.value?.series?.[0]?.data ?? []);
const writeSeries = computed<number[][]>(() => timeline.value?.series?.[1]?.data ?? []);
const readCountSeries = computed<number[][]>(() => timeline.value?.series?.[2]?.data ?? []);
const writeCountSeries = computed<number[][]>(() => timeline.value?.series?.[3]?.data ?? []);

const isBytes = computed(() => metric.value === 'bytes');

const primarySeries = computed<number[][]>(() =>
  isBytes.value ? readSeries.value : readCountSeries.value
);
const secondarySeries = computed<number[][]>(() =>
  isBytes.value ? writeSeries.value : writeCountSeries.value
);
const primaryTitle = computed(() => (isBytes.value ? 'Bytes Read / sec' : 'Reads / sec'));
const secondaryTitle = computed(() => (isBytes.value ? 'Bytes Written / sec' : 'Writes / sec'));
const metricAxisType = computed(() =>
  isBytes.value ? AxisFormatType.BYTES : AxisFormatType.NUMBER
);

const metricDescription = computed(() =>
  isBytes.value
    ? {
        shows: 'Bytes read and written per second over socket connections.',
        useCase:
          'Sustained read/write plateaus that line up with latency spikes point at a slow or saturated peer.'
      }
    : {
        shows: 'Socket read and write operations performed per second.',
        useCase:
          'A high call rate moving few bytes means chatty, undersized reads — the fix is buffering, not bandwidth.'
      }
);

const metricTiles = computed<MetricTile[]>(() => {
  const o = overview.value;
  return [
    {
      id: 'bytes',
      icon: 'arrow-down-up',
      caption: 'Throughput',
      value: FormattingService.formatBytes(o?.bytesRead ?? 0),
      subLabel: `read · ${FormattingService.formatBytes(o?.bytesWritten ?? 0)} written`
    },
    {
      id: 'count',
      icon: 'hdd-network',
      caption: 'Invocations',
      value: FormattingService.formatNumber(o?.opCount ?? 0),
      subLabel: 'socket operations'
    }
  ];
});

const maxPeerBytes = computed(() => peers.value.reduce((max, p) => Math.max(max, p.bytes), 0));
const shareWidth = (bytes: number): number =>
  maxPeerBytes.value > 0 ? (bytes / maxPeerBytes.value) * 100 : 0;

const totalPeerBytes = computed(() => peers.value.reduce((sum, p) => sum + p.bytes, 0));
const shareOfBytes = (bytes: number): number =>
  totalPeerBytes.value > 0 ? bytes / totalPeerBytes.value : 0;

// Violet and amber mark peer-scoped series everywhere on this page — the same violet as the
// "Single peer" badge — so a peer chart is never mistaken for the aggregate one.
const peerReadColor = ChartColors.chartColor('color-violet');
const peerWriteColor = ChartColors.chartColor('color-amber');

const selectedPeerReadSeries = computed<number[][]>(
  () => selectedPeerTimeline.value?.series?.[0]?.data ?? []
);
const selectedPeerWriteSeries = computed<number[][]>(
  () => selectedPeerTimeline.value?.series?.[1]?.data ?? []
);

const selectedPeerSummary = computed<string>(() => {
  const endpoint = peers.value.find(peer => peer.target === selectedPeer.value);
  if (!endpoint) {
    return '';
  }
  return [
    FormattingService.formatBytes(endpoint.bytes),
    `${FormattingService.formatNumber(endpoint.opCount)} ops`,
    `max ${FormattingService.formatDuration2Units(endpoint.maxNanos)}`
  ].join(' · ');
});

const tabs = computed<TabBarItem[]>(() => [
  { id: 'total', label: 'Total', icon: 'graph-up' },
  {
    id: 'peer-timeline',
    label: 'Peer Timeline',
    icon: 'grid-3x2-gap',
    badge: peerTimelines.value.length || undefined
  },
  {
    id: 'peers',
    label: 'Top Peers',
    icon: 'hdd-network',
    badge: peers.value.length || undefined
  },
  {
    id: 'slowest',
    label: 'Slowest Operations',
    icon: 'hourglass-split',
    badge: slowest.value.length || undefined
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const kindVariant = (kind: string): Variant => (kind.includes('Write') ? 'warning' : 'info');

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  return [
    {
      icon: 'arrow-down',
      title: 'Bytes Read',
      value: FormattingService.formatBytes(o.bytesRead),
      variant: 'highlight' as const
    },
    {
      icon: 'arrow-up',
      title: 'Bytes Written',
      value: FormattingService.formatBytes(o.bytesWritten),
      variant: 'info' as const
    },
    {
      icon: 'hdd-network',
      title: 'Socket Operations',
      value: FormattingService.formatNumber(o.opCount),
      variant: 'success' as const
    },
    {
      icon: 'hourglass-split',
      title: 'Slowest Operation',
      value: FormattingService.formatDuration2Units(o.slowestNanos),
      variant: 'warning' as const,
      breakdown: o.slowestTarget ? [{ label: 'Peer', value: o.slowestTarget }] : []
    }
  ];
});

let client: ProfileSocketIoClient;

const selectPeer = async (target: string): Promise<void> => {
  if (selectedPeer.value === target) {
    return;
  }
  selectedPeer.value = target;
  peerTimelineLoading.value = true;
  try {
    selectedPeerTimeline.value = await client.getTimeline(target);
  } catch (e) {
    console.error('Failed to load peer timeline:', e);
    selectedPeerTimeline.value = undefined;
  } finally {
    peerTimelineLoading.value = false;
  }
};

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    client = new ProfileSocketIoClient(profileId);

    const [overviewResult, timelineResult, slowestResult, peersResult, peerTimelinesResult] =
      await Promise.all([
        client.getOverview(),
        client.getTimeline(),
        client.getSlowest(),
        client.getPeers(),
        client.getPeerTimelines()
      ]);

    overview.value = overviewResult;
    timeline.value = timelineResult;
    slowest.value = slowestResult;
    peers.value = peersResult;
    peerTimelines.value = peerTimelinesResult;

    loading.value = false;

    // The gallery opens on the heaviest peer so the tab is never an empty chart frame.
    const heaviestPeer = peerTimelinesResult[0];
    if (heaviestPeer) {
      await selectPeer(heaviestPeer.endpoint.target);
    }
  } catch (e) {
    console.error('Failed to load socket I/O data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.io-container {
  width: 100%;
  color: var(--color-text);
}

.chart-container {
  width: 100%;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.gallery-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.toolbar-hint {
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.peer-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.selected-peer-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
  overflow: hidden;
}

.selected-peer-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-light);
}

.selected-peer-target {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-peer-summary {
  margin-left: auto;
  font-size: 0.76rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.selected-peer-body {
  padding: 12px 16px;
}

.target-cell {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.82rem;
  max-width: 460px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-col {
  width: 180px;
}

.share-bar {
  width: 100%;
  height: 6px;
  background: var(--color-lighter);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.share-bar-fill {
  height: 100%;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
}

.jfc-block {
  margin: 8px 0 12px;
  padding: 12px 14px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--color-text);
  overflow-x: auto;
  white-space: pre;
}
</style>
