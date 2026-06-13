<template>
  <div class="native-memory-container">
    <LoadingState v-if="loading" message="Loading native-memory data..." />
    <ErrorState v-else-if="error" message="Failed to load native-memory data" />

    <div v-else>
      <PageHeader
        title="Native Memory"
        description="Resident set size vs. heap usage, direct buffer growth, and loaded native libraries — investigate off-heap memory and container OOM kills"
        icon="bi-memory"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- RSS vs Heap -->
      <div v-show="activeTab === 'rss'">
        <ChartDescription
          shows="Process resident set size vs the Java heap used over time."
          use-case="A widening gap while the heap stays flat means native (off-heap) memory is growing."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="rssSeries"
            primaryTitle="Resident Set Size"
            :secondaryData="heapUsedSeries"
            secondaryTitle="Heap Used"
            :primaryAxisType="AxisFormatType.BYTES"
            :secondaryAxisType="AxisFormatType.BYTES"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Direct Buffers -->
      <div v-show="activeTab === 'direct-buffers'">
        <ChartDescription
          shows="NIO direct (off-heap) buffer memory and count over time."
          use-case="A climbing count is the classic NIO/Netty buffer-leak pattern."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="bufferMemorySeries"
            primaryTitle="Direct Buffer Memory"
            :secondaryData="bufferCountSeries"
            secondaryTitle="Buffer Count"
            :primaryAxisType="AxisFormatType.BYTES"
            :secondaryAxisType="AxisFormatType.NUMBER"
            :independentSecondaryAxis="true"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Native Libraries -->
      <div v-show="activeTab === 'native-libraries'">
        <EmptyState
          v-if="nativeLibraries.length === 0"
          icon="bi-collection"
          title="No native libraries recorded"
          description="This recording has no jdk.NativeLibrary events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="librariesView.query" search-placeholder="Filter libraries...">
              <span class="toolbar-info">Native libraries</span>
              <template #filters>
                <Badge key-label="Total" :value="librariesView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Library</th>
              <th class="text-end">Mapped Size</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="library in librariesView.visible" :key="library.name">
              <td :title="library.name">
                <div class="path-display">
                  <code class="path-name">{{ fileName(library.name) }}</code>
                  <span v-if="dirName(library.name)" class="path-dir">{{ dirName(library.name) }}</span>
                </div>
              </td>
              <td class="text-end">
                {{ library.mappedBytes > 0 ? FormattingService.formatBytes(library.mappedBytes) : '—' }}
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="librariesView.visible.length"
              :match-count="librariesView.matchCount"
              :total="librariesView.total"
              :expanded="librariesView.expanded"
              :page-size="librariesView.pageSize"
              @toggle="librariesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- How It Works Tab -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Native Memory"
          subtitle="The memory the OS sees vs. the Java heap — and where the gap comes from"
        >
          <AboutCallout variant="intro">
            <p>
              A JVM process uses far more memory than the Java heap. Thread stacks, the code cache,
              class metadata, direct buffers, GC structures and the mapped binaries of native libraries
              all live <em>outside</em> the heap. This page tracks the process's resident set size (RSS)
              — what the OS actually charges — and the off-heap regions that explain the gap.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-rulers" title="RSS vs Heap">
            <FeatureGrid>
              <FeatureCard icon="bi-memory" variant="primary" title="Resident Set Size">
                The physical memory the OS has committed to the process — the number that gets you
                OOM-killed in a container. It includes the heap <em>and</em> everything off-heap.
              </FeatureCard>
              <FeatureCard icon="bi-pie-chart" variant="info" title="The RSS − Heap gap">
                When RSS climbs while the heap stays flat, the growth is native: direct buffers, thread
                stacks, metaspace, JIT code or a native leak. NMT (the next page) breaks that gap down
                by category.
              </FeatureCard>
              <FeatureCard icon="bi-hdd-stack" variant="warning" title="Direct buffers">
                <code>ByteBuffer.allocateDirect</code> and Netty pooled buffers allocate off-heap and are
                freed only when their owning objects are collected. A climbing buffer count is the classic
                NIO/Netty leak.
              </FeatureCard>
              <FeatureCard icon="bi-collection" variant="success" title="Native libraries">
                Mapped <code>.so</code>/<code>.dll</code> files (the JVM itself, JDK libs, JNI deps). Large
                mapped sizes are normal; this is mostly an inventory of what's loaded.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="Container OOM with a healthy heap?" icon="bi-lightbulb-fill">
            This is the page for it. A flat heap but rising RSS means the leak is off-heap — start with
            direct buffers here, then open <em>Native Memory Tracking</em> to see which JVM category is
            growing.
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.ResidentSetSize</code> — periodic process RSS and peak. Drives the RSS timeline
                and the overview metrics.
              </li>
              <li>
                <code>jdk.DirectBufferStatistics</code> — count, used and total capacity of direct NIO
                buffers.
              </li>
              <li>
                <code>jdk.NativeLibrary</code> — each loaded native library with its mapped address range
                (mapped size = top − base).
              </li>
              <li>
                <code>jdk.GCHeapSummary</code> — heap committed/used, overlaid so you can see the
                heap-vs-RSS gap.
              </li>
            </ul>
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
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import ChartDescription from '@/components/ChartDescription.vue';
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
import ProfileNativeMemoryClient from '@/services/api/ProfileNativeMemoryClient';
import type { NativeLibraryInfo, NativeMemoryOverview } from '@/services/api/model/NativeMemoryModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const overview = ref<NativeMemoryOverview>();
const rssTimeline = ref<TimeseriesData>();
const directBufferTimeline = ref<TimeseriesData>();
const nativeLibraries = ref<NativeLibraryInfo[]>([]);

const librariesView = useTableView<NativeLibraryInfo>(nativeLibraries, {
  searchableText: (library) => library.name
});

const activeTab = ref('rss');

// Split a library path into its file name and parent directory for a two-line cell.
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

const rssSeries = computed<number[][]>(() => rssTimeline.value?.series?.[0]?.data ?? []);
const heapUsedSeries = computed<number[][]>(() => rssTimeline.value?.series?.[1]?.data ?? []);
const bufferMemorySeries = computed<number[][]>(() => directBufferTimeline.value?.series?.[0]?.data ?? []);
const bufferCountSeries = computed<number[][]>(() => directBufferTimeline.value?.series?.[1]?.data ?? []);

const tabs = computed<TabBarItem[]>(() => [
  { id: 'rss', label: 'RSS vs Heap', icon: 'graph-up-arrow' },
  { id: 'direct-buffers', label: 'Direct Buffers', icon: 'hdd-stack' },
  {
    id: 'native-libraries',
    label: 'Native Libraries',
    icon: 'collection',
    badge: nativeLibraries.value.length || undefined
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  const growthSign = o.rssGrowthBytes >= 0 ? '+' : '−';
  return [
    {
      icon: 'memory',
      title: 'Peak RSS',
      value: FormattingService.formatBytes(o.peakRssBytes),
      variant: 'highlight' as const,
      breakdown: [{ label: 'Final', value: FormattingService.formatBytes(o.finalRssBytes) }]
    },
    {
      icon: 'graph-up-arrow',
      title: 'RSS Growth',
      value: `${growthSign}${FormattingService.formatBytes(Math.abs(o.rssGrowthBytes))}`,
      variant: 'warning' as const
    },
    {
      icon: 'hdd-stack',
      title: 'Direct Buffer Memory',
      value: FormattingService.formatBytes(o.directBufferMemoryUsed),
      variant: 'info' as const,
      breakdown: [{ label: 'Buffers', value: FormattingService.formatNumber(o.directBufferCount) }]
    },
    {
      icon: 'collection',
      title: 'Native Libraries',
      value: FormattingService.formatNumber(o.nativeLibraryCount),
      variant: 'success' as const
    }
  ];
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileNativeMemoryClient(profileId);

    const [overviewResult, rssResult, buffersResult, librariesResult] = await Promise.all([
      client.getOverview(),
      client.getRssTimeline(),
      client.getDirectBufferTimeline(),
      client.getNativeLibraries()
    ]);

    overview.value = overviewResult;
    rssTimeline.value = rssResult;
    directBufferTimeline.value = buffersResult;
    nativeLibraries.value = librariesResult;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load native-memory data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.native-memory-container {
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

.path-display {
  display: flex;
  flex-direction: column;
  max-width: 640px;
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
