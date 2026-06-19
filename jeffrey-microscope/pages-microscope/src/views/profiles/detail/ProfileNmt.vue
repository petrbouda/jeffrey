<template>
  <div class="nmt-container">
    <LoadingState v-if="loading" message="Loading native-memory-tracking data..." />
    <ErrorState v-else-if="error" message="Failed to load native-memory-tracking data" />

    <div v-else>
      <PageHeader
        title="Native Memory Tracking"
        description="The JVM's own native-memory accounting — reserved vs committed bytes per category (Thread, Class, Code, GC, …). Find which subsystem is growing when the heap looks fine but RSS climbs."
        icon="bi-pie-chart"
      />

      <div v-if="overview?.hasNmtData" class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Categories -->
      <div v-show="activeTab === 'categories'">
        <DisabledEventsNotice
          v-if="!overview?.hasNmtData"
          title="Native Memory Tracking not enabled"
          action-label="Launch the JVM with NMT enabled"
          command="-XX:NativeMemoryTracking=summary"
        >
          Native Memory Tracking breaks native memory down by category. Its
          <code>jdk.NativeMemoryUsage</code> events are emitted only when the JVM is started with
          <code>-XX:NativeMemoryTracking=summary</code> (or <code>detail</code>) — this recording has
          none.
          <template #action>
            <p>
              Then capture the recording with the <code>profile</code> settings
              (<code>settings=profile</code>) so the NMT JFR events are included, and re-import it.
            </p>
          </template>
        </DisabledEventsNotice>
        <EmptyState
          v-else-if="categories.length === 0"
          icon="bi-pie-chart"
          title="No per-category NMT data"
          description="This recording has jdk.NativeMemoryUsageTotal but no per-category jdk.NativeMemoryUsage events — only the Reserved vs Committed totals are available."
        />
        <template v-else>
          <ChartDescription
            shows="Committed bytes per NMT category over time (top categories; the rest grouped as Other)."
            use-case="A category climbing steadily is the native-memory leak signal — growing Thread points at a thread leak, Class/Metaspace at a classloader leak."
          />
          <div class="chart-container">
            <TimeSeriesChart
              :seriesData="categorySeries"
              :stacked="true"
              :primaryAxisType="AxisFormatType.BYTES"
              :visibleMinutes="60"
            />
          </div>

          <DataTable class="mt-4">
            <template #toolbar>
              <TableToolbar v-model="categoriesView.query" search-placeholder="Filter categories...">
                <span class="toolbar-info">Category breakdown</span>
                <template #filters>
                  <Badge key-label="Total" :value="categoriesView.matchCount" variant="secondary" size="s" borderless />
                </template>
              </TableToolbar>
            </template>
            <thead>
              <tr>
                <th>Category</th>
                <th class="text-end">Reserved</th>
                <th class="text-end">Committed</th>
                <th class="share-col">Share of Committed</th>
                <th class="text-end">Growth</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="category in categoriesView.visible" :key="category.category">
                <td class="category-name">{{ category.category }}</td>
                <td class="text-end">{{ FormattingService.formatBytes(category.reservedBytes) }}</td>
                <td class="text-end">{{ FormattingService.formatBytes(category.committedBytes) }}</td>
                <td>
                  <div class="share-bar">
                    <div class="share-bar-fill" :style="{ width: shareWidth(category.committedBytes) + '%' }"></div>
                  </div>
                </td>
                <td class="text-end">
                  <Badge
                    :value="growthLabel(category.growthBytes)"
                    :variant="growthVariant(category.growthBytes)"
                    size="xs"
                    borderless
                  />
                </td>
              </tr>
            </tbody>
            <template #footer>
              <TableShowMore
                :shown="categoriesView.visible.length"
                :match-count="categoriesView.matchCount"
                :total="categoriesView.total"
                :expanded="categoriesView.expanded"
                :page-size="categoriesView.pageSize"
                @toggle="categoriesView.toggle"
              />
            </template>
          </DataTable>
        </template>
      </div>

      <!-- Reserved vs Committed -->
      <div v-show="activeTab === 'totals'">
        <DisabledEventsNotice
          v-if="!overview?.hasNmtData"
          title="Native Memory Tracking not enabled"
          action-label="Launch the JVM with NMT enabled"
          command="-XX:NativeMemoryTracking=summary"
        >
          The total reserved vs committed timeline comes from <code>jdk.NativeMemoryUsageTotal</code>,
          emitted only when the JVM is started with <code>-XX:NativeMemoryTracking=summary</code>.
          <template #action>
            <p>Re-record with NMT on and the <code>profile</code> settings, then re-import.</p>
          </template>
        </DisabledEventsNotice>
        <template v-else>
          <ChartDescription
            shows="Total reserved (address space mapped) vs committed (actually backed by memory) over time."
            use-case="A large reserved/committed gap is normal (lazy mappings); climbing committed is what drives RSS up."
          />
          <div class="chart-container">
            <TimeSeriesChart
              :primaryData="totalCommittedSeries"
              primaryTitle="Committed"
              :secondaryData="totalReservedSeries"
              secondaryTitle="Reserved"
              :primaryAxisType="AxisFormatType.BYTES"
              :secondaryAxisType="AxisFormatType.BYTES"
              :visibleMinutes="60"
            />
          </div>
        </template>
      </div>

      <!-- RSS vs Tracked -->
      <div v-show="activeTab === 'rss-vs-tracked'">
        <DisabledEventsNotice
          v-if="!overview?.hasNmtData"
          title="Native Memory Tracking not enabled"
          action-label="Launch the JVM with NMT enabled"
          command="-XX:NativeMemoryTracking=summary"
        >
          This overlay compares RSS against total NMT committed, so it needs
          <code>jdk.NativeMemoryUsageTotal</code> — emitted only with
          <code>-XX:NativeMemoryTracking=summary</code>.
          <template #action>
            <p>Re-record with NMT on and the <code>profile</code> settings, then re-import.</p>
          </template>
        </DisabledEventsNotice>
        <template v-else>
          <ChartDescription>
            <template #shows>Resident set size (what the OS sees) vs the memory NMT accounts for.</template>
            <template #use-case>
              The gap (RSS − committed) approximates untracked memory — raw <code>malloc</code>, thread
              stacks beyond guard pages, and mappings NMT doesn't see.
            </template>
          </ChartDescription>
          <div class="chart-container">
            <TimeSeriesChart
              :primaryData="rssSeries"
              primaryTitle="Resident Set Size"
              :secondaryData="trackedCommittedSeries"
              secondaryTitle="NMT Committed"
              :primaryAxisType="AxisFormatType.BYTES"
              :secondaryAxisType="AxisFormatType.BYTES"
              :visibleMinutes="60"
            />
          </div>
        </template>
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Native Memory Tracking"
          subtitle="How the JVM accounts for memory outside the Java heap"
        >
          <AboutCallout variant="intro">
            <p>
              The Java heap is only part of a JVM's memory. Thread stacks, JIT-compiled code, class
              metadata, GC structures and internal buffers all live in <strong>native</strong> memory.
              Native Memory Tracking (NMT) is the JVM's own accounting of that memory, broken down by
              category — the tool for "the heap is healthy but the container got OOM-killed".
            </p>
          </AboutCallout>

          <AboutSection icon="bi-rulers" title="Reserved vs Committed">
            <FeatureGrid>
              <FeatureCard icon="bi-bounding-box" variant="info" title="Reserved">
                Address space the JVM has mapped but may not be using yet. Reserved memory costs
                nothing physically — it's a promise the OS can hand back pages later.
              </FeatureCard>
              <FeatureCard icon="bi-hdd-fill" variant="primary" title="Committed">
                The portion actually backed by physical memory (or swap). This is what the OS charges
                the process and what shows up in RSS — climbing committed is what matters.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-bug" title="Leak Signatures">
            <FeatureGrid>
              <FeatureCard icon="bi-diagram-2" variant="warning" title="Thread">
                Growing committed Thread memory means thread stacks accumulating — an unbounded or
                leaking thread pool.
              </FeatureCard>
              <FeatureCard icon="bi-box-seam" variant="danger" title="Class / Metaspace">
                Steadily rising Class memory points at a classloader leak — typically dynamic proxies
                or repeated redeploys.
              </FeatureCard>
              <FeatureCard icon="bi-cpu" variant="success" title="Code">
                A large or growing Code category is the JIT code cache — heavy under churn or with many
                compiled methods.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-toggles" title="Enabling NMT">
            <p>
              NMT is off by default. Start the JVM with <code>-XX:NativeMemoryTracking=summary</code>
              (or <code>detail</code>) and capture the recording with the <code>profile</code>
              settings so the <code>jdk.NativeMemoryUsage</code> / <code>jdk.NativeMemoryUsageTotal</code>
              events are included. There is a small runtime overhead (~5–10%), so it's usually left off
              in production unless a native-memory problem is being investigated.
            </p>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.NativeMemoryUsage</code> — periodic per-category reserved/committed. The
                <code>type</code> field is a constant-pool label (like <code>gcName</code>), so it
                serializes as a plain category string.
              </li>
              <li>
                <code>jdk.NativeMemoryUsageTotal</code> — periodic process-wide reserved/committed
                totals; the "peak committed" metric is the max of this series (there is no peak event).
              </li>
            </ul>
            <p>
              Both are emitted only when the JVM runs with <code>-XX:NativeMemoryTracking</code> — they
              don't exist in a recording made without it, which is why the data tabs show the enable
              notice instead.
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
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import ChartDescription from '@/components/ChartDescription.vue';
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
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileNmtClient from '@/services/api/ProfileNmtClient';
import type { NmtCategory, NmtOverview } from '@/services/api/model/NmtModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const overview = ref<NmtOverview>();
const categories = ref<NmtCategory[]>([]);
const categoryTimeline = ref<TimeseriesData>();
const totalTimeline = ref<TimeseriesData>();
const rssVsTracked = ref<TimeseriesData>();

const activeTab = ref('categories');

const categoriesView = useTableView<NmtCategory>(categories, {
  searchableText: category => category.category
});

const categorySeries = computed(() =>
  (categoryTimeline.value?.series ?? []).map(serie => ({ name: serie.name, data: serie.data }))
);

const totalCommittedSeries = computed<number[][]>(() => totalTimeline.value?.series?.[0]?.data ?? []);
const totalReservedSeries = computed<number[][]>(() => totalTimeline.value?.series?.[1]?.data ?? []);
const rssSeries = computed<number[][]>(() => rssVsTracked.value?.series?.[0]?.data ?? []);
const trackedCommittedSeries = computed<number[][]>(() => rssVsTracked.value?.series?.[1]?.data ?? []);

const maxCommitted = computed(() =>
  categories.value.reduce((max, category) => Math.max(max, category.committedBytes), 0)
);

const shareWidth = (bytes: number): number =>
  maxCommitted.value > 0 ? (bytes / maxCommitted.value) * 100 : 0;

const growthLabel = (growthBytes: number): string => {
  if (growthBytes === 0) {
    return '0 B';
  }
  const sign = growthBytes > 0 ? '+' : '−';
  return `${sign}${FormattingService.formatBytes(Math.abs(growthBytes))}`;
};

const growthVariant = (growthBytes: number): 'warning' | 'success' | 'secondary' => {
  if (growthBytes > 0) {
    return 'warning';
  }
  if (growthBytes < 0) {
    return 'success';
  }
  return 'secondary';
};

const tabs = computed<TabBarItem[]>(() => [
  {
    id: 'categories',
    label: 'Categories',
    icon: 'pie-chart',
    badge: categories.value.length || undefined
  },
  { id: 'totals', label: 'Reserved vs Committed', icon: 'graph-up-arrow' },
  { id: 'rss-vs-tracked', label: 'RSS vs Tracked', icon: 'rulers' },
  { id: 'about', label: 'How It Works', icon: 'info-circle' }
]);

const metricsData = computed(() => {
  if (!overview.value || !overview.value.hasNmtData) {
    return [];
  }
  const o = overview.value;
  return [
    {
      icon: 'hdd-fill',
      title: 'Total Committed',
      value: FormattingService.formatBytes(o.totalCommittedBytes),
      variant: 'highlight' as const,
      breakdown: [{ label: 'Reserved', value: FormattingService.formatBytes(o.totalReservedBytes) }]
    },
    {
      icon: 'graph-up-arrow',
      title: 'Peak Committed',
      value: FormattingService.formatBytes(o.peakCommittedBytes),
      variant: 'warning' as const
    },
    {
      icon: 'pie-chart',
      title: 'Largest Category',
      value: o.largestCategory ?? '—',
      variant: 'info' as const,
      breakdown: [
        { label: 'Committed', value: FormattingService.formatBytes(o.largestCategoryCommittedBytes) }
      ]
    },
    {
      icon: 'rulers',
      title: 'Untracked (RSS − Committed)',
      value: FormattingService.formatBytes(o.untrackedBytes),
      variant: 'success' as const,
      breakdown: [{ label: 'Categories', value: FormattingService.formatNumber(o.categoryCount) }]
    }
  ];
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileNmtClient(profileId);

    const [overviewResult, categoriesResult, categoryTimelineResult, totalTimelineResult, rssResult] =
      await Promise.all([
        client.getOverview(),
        client.getCategories(),
        client.getCategoryTimeline(),
        client.getTotalTimeline(),
        client.getRssVsTracked()
      ]);

    overview.value = overviewResult;
    categories.value = categoriesResult;
    categoryTimeline.value = categoryTimelineResult;
    totalTimeline.value = totalTimelineResult;
    rssVsTracked.value = rssResult;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load native-memory-tracking data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.nmt-container {
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

.category-name {
  font-weight: 500;
  color: var(--color-text);
}

.share-col {
  width: 200px;
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
</style>
