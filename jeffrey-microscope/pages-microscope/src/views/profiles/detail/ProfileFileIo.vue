<template>
  <div class="io-container">
    <LoadingState v-if="loading" message="Loading file I/O data..." />
    <ErrorState v-else-if="error" message="Failed to load file I/O data" />

    <div v-else>
      <PageHeader
        title="File I/O"
        description="Blocking file I/O — read/write throughput, the slowest operations, and the busiest files and directories"
        icon="bi-file-earmark"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Throughput -->
      <div v-show="activeTab === 'throughput'">
        <ChartDescription
          shows="Bytes read and written per second over file descriptors."
          use-case="Sustained throughput or spikes that line up with latency point at heavy disk I/O or an uncached hot file."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="readSeries"
            primaryTitle="Bytes Read / sec"
            :secondaryData="writeSeries"
            secondaryTitle="Bytes Written / sec"
            :primaryAxisType="AxisFormatType.BYTES"
            :secondaryAxisType="AxisFormatType.BYTES"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Top Files -->
      <div v-show="activeTab === 'files'">
        <EmptyState
          v-if="files.length === 0"
          icon="bi-file-earmark"
          title="No file I/O recorded"
          description="This recording has no jdk.FileRead / jdk.FileWrite events (often disabled by default)."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="filesView.query" search-placeholder="Filter files...">
              <span class="toolbar-info">Files</span>
              <template #filters>
                <Badge key-label="Total" :value="filesView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>File</th>
              <th class="text-end">Operations</th>
              <th class="text-end">Bytes</th>
              <th class="share-col">Share of Bytes</th>
              <th class="text-end">Total Time</th>
              <th class="text-end">Max Time</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="file in filesView.visible" :key="file.target">
              <td class="path-cell" :title="file.target">
                <code class="path-name">{{ fileName(file.target) }}</code>
                <span v-if="dirName(file.target)" class="path-dir">{{ dirName(file.target) }}</span>
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(file.opCount) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(file.bytes) }}</td>
              <td>
                <div class="share-bar">
                  <div class="share-bar-fill" :style="{ width: shareWidth(file.bytes, maxFileBytes) + '%' }"></div>
                </div>
              </td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(file.totalNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(file.maxNanos) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="filesView.visible.length"
              :match-count="filesView.matchCount"
              :total="filesView.total"
              :expanded="filesView.expanded"
              :page-size="filesView.pageSize"
              @toggle="filesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- By Directory -->
      <div v-show="activeTab === 'directories'">
        <EmptyState
          v-if="directories.length === 0"
          icon="bi-folder"
          title="No file I/O recorded"
          description="This recording has no jdk.FileRead / jdk.FileWrite events to aggregate by directory."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="directoriesView.query" search-placeholder="Filter directories...">
              <span class="toolbar-info">By directory</span>
              <template #filters>
                <Badge key-label="Total" :value="directoriesView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Directory</th>
              <th class="text-end">Operations</th>
              <th class="text-end">Bytes</th>
              <th class="share-col">Share of Bytes</th>
              <th class="text-end">Total Time</th>
              <th class="text-end">Max Time</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="dir in directoriesView.visible" :key="dir.target">
              <td class="target-cell" :title="dir.target">{{ dir.target }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(dir.opCount) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(dir.bytes) }}</td>
              <td>
                <div class="share-bar">
                  <div class="share-bar-fill" :style="{ width: shareWidth(dir.bytes, maxDirBytes) + '%' }"></div>
                </div>
              </td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(dir.totalNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(dir.maxNanos) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="directoriesView.visible.length"
              :match-count="directoriesView.matchCount"
              :total="directoriesView.total"
              :expanded="directoriesView.expanded"
              :page-size="directoriesView.pageSize"
              @toggle="directoriesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Slowest Operations -->
      <div v-show="activeTab === 'slowest'">
        <EmptyState
          v-if="slowest.length === 0"
          icon="bi-hourglass-split"
          title="No file operations recorded"
          description="This recording has no file I/O events."
        />
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
              <th>File</th>
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
          title="Understanding File I/O"
          subtitle="Where blocking file reads and writes cost you latency"
        >
          <AboutCallout variant="intro">
            <p>
              A file read or write blocks the calling thread until the OS satisfies it — instantly from
              the page cache, or slowly from disk on a cache miss or <code>fsync</code>. This page
              attributes that wait to the files and directories your application touches.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-hdd" title="What the Views Show">
            <FeatureGrid>
              <FeatureCard icon="bi-graph-up" variant="primary" title="Throughput">
                Bytes read vs written per second. Heavy sustained writes can mean logging or flushing;
                heavy reads on the same files suggest a missing in-memory cache.
              </FeatureCard>
              <FeatureCard icon="bi-file-earmark" variant="info" title="Top Files">
                Individual files ranked by bytes, with a share bar. A single file dominating is the
                cue to cache it, batch writes, or move it off the hot path.
              </FeatureCard>
              <FeatureCard icon="bi-folder" variant="success" title="By Directory">
                The same I/O rolled up per parent directory — surfaces a hot log dir or data dir even
                when it's spread across many rotating files.
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-split" variant="warning" title="Slowest Operations">
                Individual reads/writes by duration. A slow write is often an <code>fsync</code>/flush;
                a slow read is a page-cache miss hitting the disk.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.FileRead</code> — path, <code>bytesRead</code>, an end-of-stream flag and the
                operation <code>duration</code>.
              </li>
              <li><code>jdk.FileWrite</code> — path and <code>bytesWritten</code> with duration.</li>
            </ul>
            <p>
              Both are <strong>threshold-gated</strong> and frequently disabled in default recording
              configurations, so file tabs may be empty even when the app does file I/O.
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

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import type { TabBarItem } from '@/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import { useTableView } from '@/composables/useTableView';
import ProfileFileIoClient from '@/services/api/ProfileFileIoClient';
import type { IoEndpoint, IoOperation, IoOverview } from '@/services/api/model/IoModels';
import type { Variant } from '@/types/ui';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const overview = ref<IoOverview>();
const timeline = ref<TimeseriesData>();
const slowest = ref<IoOperation[]>([]);
const files = ref<IoEndpoint[]>([]);
const directories = ref<IoEndpoint[]>([]);

const slowestView = useTableView<IoOperation>(slowest, {
  searchableText: (r) => `${r.target} ${r.thread ?? ''}`
});
const filesView = useTableView<IoEndpoint>(files, {
  searchableText: (r) => r.target
});
const directoriesView = useTableView<IoEndpoint>(directories, {
  searchableText: (r) => r.target
});

const activeTab = ref('throughput');

const readSeries = computed<number[][]>(() => timeline.value?.series?.[0]?.data ?? []);
const writeSeries = computed<number[][]>(() => timeline.value?.series?.[1]?.data ?? []);

const maxFileBytes = computed(() => files.value.reduce((max, f) => Math.max(max, f.bytes), 0));
const maxDirBytes = computed(() => directories.value.reduce((max, d) => Math.max(max, d.bytes), 0));
const shareWidth = (bytes: number, max: number): number => (max > 0 ? (bytes / max) * 100 : 0);

const tabs = computed<TabBarItem[]>(() => [
  { id: 'throughput', label: 'Throughput', icon: 'graph-up' },
  {
    id: 'files',
    label: 'Top Files',
    icon: 'file-earmark',
    badge: files.value.length || undefined
  },
  {
    id: 'directories',
    label: 'By Directory',
    icon: 'folder',
    badge: directories.value.length || undefined
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

const fileName = (path: string): string => {
  const trimmed = path.replace(/\/+$/, '');
  const slash = trimmed.lastIndexOf('/');
  return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
};

const dirName = (path: string): string => {
  const trimmed = path.replace(/\/+$/, '');
  const slash = trimmed.lastIndexOf('/');
  return slash > 0 ? trimmed.substring(0, slash) : '';
};

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
      icon: 'file-earmark',
      title: 'File Operations',
      value: FormattingService.formatNumber(o.opCount),
      variant: 'success' as const
    },
    {
      icon: 'hourglass-split',
      title: 'Slowest Operation',
      value: FormattingService.formatDuration2Units(o.slowestNanos),
      variant: 'warning' as const,
      breakdown: o.slowestTarget ? [{ label: 'File', value: o.slowestTarget }] : []
    }
  ];
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileFileIoClient(profileId);

    const [overviewResult, timelineResult, slowestResult, filesResult, directoriesResult] =
      await Promise.all([
        client.getOverview(),
        client.getTimeline(),
        client.getSlowest(),
        client.getFiles(),
        client.getDirectories()
      ]);

    overview.value = overviewResult;
    timeline.value = timelineResult;
    slowest.value = slowestResult;
    files.value = filesResult;
    directories.value = directoriesResult;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load file I/O data:', e);
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

.path-cell {
  display: flex;
  flex-direction: column;
  max-width: 560px;
}

.path-name {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text);
  background-color: transparent;
  white-space: nowrap;
}

.path-dir {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-text-muted);
  margin-top: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
