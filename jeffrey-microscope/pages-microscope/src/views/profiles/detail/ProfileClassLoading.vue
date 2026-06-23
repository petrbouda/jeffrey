<template>
  <div class="class-loading-container">
    <LoadingState v-if="loading" message="Loading class-loading data..." />
    <ErrorState v-else-if="error" message="Failed to load class-loading data" />

    <div v-else>
      <PageHeader
        title="Class Loading"
        description="Class loading and unloading over time, per-class-loader metaspace footprint, and bytecode instrumentation activity"
        icon="bi-box-seam"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Timeline -->
      <div v-show="activeTab === 'timeline'">
        <ChartDescription
          shows="Currently-loaded classes (loaded minus unloaded) and class-unloading activity over time."
          use-case="Steady growth in loaded classes is a classic metaspace-leak signal."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="loadedSeries"
            primaryTitle="Loaded Classes"
            :secondaryData="unloadedSeries"
            secondaryTitle="Unloaded Classes"
            :independentSecondaryAxis="true"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Class Loaders -->
      <div v-show="activeTab === 'class-loaders'">
        <EmptyState
          v-if="classLoaders.length === 0"
          icon="bi-diagram-3"
          title="No class-loader statistics recorded"
          description="This profile has no jdk.ClassLoaderStatistics events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="classLoadersView.query" search-placeholder="Filter class loaders...">
              <span class="toolbar-info">Class loaders</span>
              <template #filters>
                <Badge
                  key-label="Total"
                  :value="classLoadersView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <SortableTableHeader
                column="name"
                label="Class Loader"
                :sortColumn="sortColumn"
                :sortDirection="sortDirection"
                @sort="onSort"
              />
              <SortableTableHeader
                column="parentName"
                label="Parent"
                :sortColumn="sortColumn"
                :sortDirection="sortDirection"
                @sort="onSort"
              />
              <SortableTableHeader
                column="classCount"
                label="Classes"
                align="end"
                :sortColumn="sortColumn"
                :sortDirection="sortDirection"
                @sort="onSort"
              />
              <SortableTableHeader
                column="metaspaceBytes"
                label="Metaspace"
                align="end"
                :sortColumn="sortColumn"
                :sortDirection="sortDirection"
                @sort="onSort"
              />
              <SortableTableHeader
                column="blockBytes"
                label="Used"
                align="end"
                :sortColumn="sortColumn"
                :sortDirection="sortDirection"
                @sort="onSort"
              />
              <SortableTableHeader
                column="hiddenClassCount"
                label="Hidden"
                align="end"
                :sortColumn="sortColumn"
                :sortDirection="sortDirection"
                @sort="onSort"
              />
            </tr>
          </thead>
          <tbody>
            <tr v-for="(loader, index) in classLoadersView.visible" :key="index">
              <td class="class-cell" :title="loader.name">
                <ClassNameDisplay :class-name="loader.name" />
              </td>
              <td class="class-cell" :title="loader.parentName ?? ''">
                <ClassNameDisplay v-if="loader.parentName" :class-name="loader.parentName" />
                <span v-else class="text-muted">—</span>
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(loader.classCount) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(loader.metaspaceBytes) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(loader.blockBytes) }}</td>
              <td class="text-end">
                <Badge
                  v-if="loader.hiddenClassCount > 0"
                  :value="FormattingService.formatNumber(loader.hiddenClassCount)"
                  variant="warning"
                  size="xs"
                />
                <span v-else class="text-muted">0</span>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="classLoadersView.visible.length"
              :match-count="classLoadersView.matchCount"
              :total="classLoadersView.total"
              :expanded="classLoadersView.expanded"
              :page-size="classLoadersView.pageSize"
              @toggle="classLoadersView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Class Load Activity -->
      <div v-show="activeTab === 'class-loads'">
        <DisabledEventsNotice
          v-if="!classLoadActivity || classLoadActivity.slowest.length === 0"
          title="No per-class load events"
          action-label="Re-record with the event enabled"
          command="settings=profile,+jdk.ClassLoad#enabled=true"
        >
          <code>jdk.ClassLoad</code> events are disabled by default because of their overhead, so
          individual class-load timings aren't available. Enable the event in the recording
          configuration to see per-class load durations here.
        </DisabledEventsNotice>
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="classLoadsView.query" search-placeholder="Filter classes...">
              <span class="toolbar-info">Slowest class loads</span>
              <template #filters>
                <Badge
                  key-label="Showing"
                  :value="classLoadsView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
                <Badge
                  key-label="Total loads"
                  :value="FormattingService.formatNumber(classLoadActivity.totalCount)"
                  variant="primary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Class</th>
              <th class="text-end">Load Time</th>
              <th>Defining Class Loader</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(entry, index) in classLoadsView.visible" :key="index">
              <td class="class-cell" :title="entry.className ?? ''">
                <ClassNameDisplay v-if="entry.className" :class-name="entry.className" />
                <span v-else class="text-muted">—</span>
              </td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(entry.durationNanos) }}</td>
              <td class="class-cell" :title="entry.definingClassLoader ?? ''">
                <ClassNameDisplay v-if="entry.definingClassLoader" :class-name="entry.definingClassLoader" />
                <span v-else class="text-muted">—</span>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="classLoadsView.visible.length"
              :match-count="classLoadsView.matchCount"
              :total="classLoadsView.total"
              :expanded="classLoadsView.expanded"
              :page-size="classLoadsView.pageSize"
              @toggle="classLoadsView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Redefinitions -->
      <div v-show="activeTab === 'redefinitions'">
        <EmptyState
          v-if="!hasRedefinitions"
          icon="bi-arrow-repeat"
          title="No class redefinitions recorded"
          description="No jdk.ClassRedefinition or jdk.RetransformClasses events are present. These are produced by bytecode-instrumentation agents (JFR retransformation, APM agents, mocking frameworks)."
        />
        <template v-else>
          <div v-if="redefinitionData!.retransforms.length > 0" class="mb-4">
            <DataTable>
              <template #toolbar>
                <TableToolbar :show-search="false">
                  <span class="toolbar-info">Retransformation batches</span>
                  <template #filters>
                    <Badge
                      key-label="Total"
                      :value="retransformsView.total"
                      variant="secondary"
                      size="s"
                      borderless
                    />
                  </template>
                </TableToolbar>
              </template>
              <thead>
                <tr>
                  <th>Batch (Redefinition ID)</th>
                  <th class="text-end">Classes</th>
                  <th class="text-end">Duration</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(batch, index) in retransformsView.visible" :key="index">
                  <td>{{ batch.redefinitionId }}</td>
                  <td class="text-end">{{ FormattingService.formatNumber(batch.classCount) }}</td>
                  <td class="text-end">{{ FormattingService.formatDuration2Units(batch.durationNanos) }}</td>
                </tr>
              </tbody>
              <template #footer>
                <TableShowMore
                  :shown="retransformsView.visible.length"
                  :match-count="retransformsView.matchCount"
                  :total="retransformsView.total"
                  :expanded="retransformsView.expanded"
                  :page-size="retransformsView.pageSize"
                  @toggle="retransformsView.toggle"
                />
              </template>
            </DataTable>
          </div>

          <DataTable v-if="redefinitionData!.redefinitions.length > 0">
            <template #toolbar>
              <TableToolbar v-model="redefinitionsView.query" search-placeholder="Filter classes...">
                <span class="toolbar-info">Redefined classes</span>
                <template #filters>
                  <Badge
                    key-label="Total"
                    :value="FormattingService.formatNumber(redefinitionsView.matchCount)"
                    variant="primary"
                    size="s"
                    borderless
                  />
                </template>
              </TableToolbar>
            </template>
            <thead>
              <tr>
                <th>Class</th>
                <th class="text-end">Modifications</th>
                <th class="text-end">Batch</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(redef, index) in redefinitionsView.visible" :key="index">
                <td class="class-cell" :title="redef.className ?? ''">
                  <ClassNameDisplay v-if="redef.className" :class-name="redef.className" />
                  <span v-else class="text-muted">—</span>
                </td>
                <td class="text-end">{{ FormattingService.formatNumber(redef.modificationCount) }}</td>
                <td class="text-end">{{ redef.redefinitionId }}</td>
              </tr>
            </tbody>
            <template #footer>
              <TableShowMore
                :shown="redefinitionsView.visible.length"
                :match-count="redefinitionsView.matchCount"
                :total="redefinitionsView.total"
                :expanded="redefinitionsView.expanded"
                :page-size="redefinitionsView.pageSize"
                @toggle="redefinitionsView.toggle"
              />
            </template>
          </DataTable>
        </template>
      </div>

      <!-- How It Works Tab -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Class Loading"
          subtitle="How the JVM finds, links and retires classes — and where metaspace goes"
        >
          <AboutCallout variant="intro">
            <p>
              Classes are loaded lazily, the first time they're needed. A class loader reads the
              bytecode, the JVM verifies and links it, and it's initialized on first use. Every loaded
              class lives in <strong>metaspace</strong> (native memory) and is tied to the loader that
              defined it — which is why a class is only unloaded when its <em>entire</em> loader becomes
              unreachable.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-arrow-repeat" title="The Class Lifecycle">
            <FeatureGrid>
              <FeatureCard icon="bi-download" variant="info" title="Loading">
                A class loader locates the <code>.class</code> bytes and the JVM defines a
                <code>Class&lt;?&gt;</code>. Delegation means the request walks up to the parent loader
                first (bootstrap → platform → app).
              </FeatureCard>
              <FeatureCard icon="bi-link-45deg" variant="primary" title="Linking">
                Verification (bytecode safety), preparation (static fields to defaults) and resolution
                (symbolic references). Verification is a one-time CPU cost paid at load.
              </FeatureCard>
              <FeatureCard icon="bi-play-circle" variant="success" title="Initialization">
                The <code>&lt;clinit&gt;</code> runs static initializers, exactly once, on first active
                use — a common source of surprising first-call latency.
              </FeatureCard>
              <FeatureCard icon="bi-trash" variant="warning" title="Unloading">
                A class is unloaded only when its loader, all its classes and all their instances are
                unreachable together. Leaked loaders are the classic metaspace leak (hot redeploys).
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-graph-up" title="Reading the Charts">
            <FeatureGrid>
              <FeatureCard icon="bi-graph-up" variant="primary" title="Timeline">
                Loaded vs unloaded classes over time. A steadily climbing loaded count that never
                plateaus — especially with redeploys — signals a class-loader leak.
              </FeatureCard>
              <FeatureCard icon="bi-diagram-3" variant="info" title="Class Loaders">
                Per-loader class counts. Many loaders with overlapping classes is normal in containers;
                an ever-growing number of loaders is not.
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-split" variant="warning" title="Class Load Activity">
                Per-class load timings — slow loads point at verification cost or slow class sources.
                Needs the per-class event, which is off by default.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-repeat" variant="purple" title="Redefinitions">
                Classes retransformed by instrumentation agents (APM, profilers, mocking). Heavy
                redefinition adds metaspace churn and recompilation.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.ClassLoadingStatistics</code> — periodic cumulative loaded/unloaded counts.
                Enabled by default, so the Timeline always works.
              </li>
              <li>
                <code>jdk.ClassLoaderStatistics</code> — per-loader class counts and metaspace usage.
              </li>
              <li>
                <code>jdk.ClassLoad</code> / <code>jdk.ClassDefine</code> — one event per class with
                timing. <strong>Off by default</strong> (high volume), which is why Class Load Activity
                often shows the "enable the event" notice.
              </li>
              <li>
                <code>jdk.ClassRedefinition</code> / <code>jdk.RetransformClasses</code> — emitted by
                instrumentation agents.
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

import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import TabBar from '@shared/components/TabBar.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import SortableTableHeader from '@shared/components/table/SortableTableHeader.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import { useTableView } from '@/composables/useTableView';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import ChartDescription from '@shared/components/ChartDescription.vue';
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
import ProfileClassLoadingClient from '@/services/api/ProfileClassLoadingClient';
import type {
  ClassLoadActivity,
  ClassLoadEntry,
  ClassLoaderStat,
  ClassLoadingOverview,
  ClassRedefinitionStat,
  RedefinitionData,
  RetransformBatch
} from '@/services/api/model/ClassLoadingModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const overview = ref<ClassLoadingOverview>();
const timeline = ref<TimeseriesData>();
const classLoaders = ref<ClassLoaderStat[]>([]);
const classLoadActivity = ref<ClassLoadActivity>();
const redefinitionData = ref<RedefinitionData>();

const activeTab = ref('timeline');

type SortColumn = keyof Pick<
  ClassLoaderStat,
  'name' | 'parentName' | 'classCount' | 'metaspaceBytes' | 'blockBytes' | 'hiddenClassCount'
>;
const sortColumn = ref<SortColumn>('metaspaceBytes');
const sortDirection = ref<'asc' | 'desc'>('desc');

const loadedSeries = computed<number[][]>(() => timeline.value?.series?.[0]?.data ?? []);
const unloadedSeries = computed<number[][]>(() => timeline.value?.series?.[1]?.data ?? []);

const hasRedefinitions = computed(() => {
  const data = redefinitionData.value;
  return !!data && (data.redefinitions.length > 0 || data.retransforms.length > 0);
});

const tabs = computed<TabBarItem[]>(() => [
  { id: 'timeline', label: 'Timeline', icon: 'graph-up' },
  {
    id: 'class-loaders',
    label: 'Class Loaders',
    icon: 'diagram-3',
    badge: classLoaders.value.length
  },
  {
    id: 'class-loads',
    label: 'Class Load Activity',
    icon: 'hourglass-split',
    badge: classLoadActivity.value?.totalCount || undefined
  },
  {
    id: 'redefinitions',
    label: 'Redefinitions',
    icon: 'arrow-repeat',
    badge: redefinitionData.value?.redefinitions.length || undefined
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  const usedMetaspace = classLoaders.value.reduce((sum, loader) => sum + loader.blockBytes, 0);
  const retransformBatches = redefinitionData.value?.retransforms.length ?? 0;

  return [
    {
      icon: 'box-seam',
      title: 'Currently Loaded Classes',
      value: FormattingService.formatNumber(o.currentlyLoaded),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Total Loaded', value: FormattingService.formatNumber(o.totalLoaded) },
        { label: 'Total Unloaded', value: FormattingService.formatNumber(o.totalUnloaded) }
      ]
    },
    {
      icon: 'diagram-3',
      title: 'Class Loaders',
      value: FormattingService.formatNumber(o.classLoaderCount),
      variant: 'info' as const,
      breakdown: [
        { label: 'Hidden Classes', value: FormattingService.formatNumber(o.hiddenClassCount) }
      ]
    },
    {
      icon: 'memory',
      title: 'Metaspace Reserved',
      value: FormattingService.formatBytes(o.metaspaceUsedBytes),
      variant: 'warning' as const,
      breakdown: [{ label: 'Used', value: FormattingService.formatBytes(usedMetaspace) }]
    },
    {
      icon: 'arrow-repeat',
      title: 'Redefinitions',
      value: FormattingService.formatNumber(redefinitionData.value?.redefinitions.length ?? 0),
      variant: 'success' as const,
      breakdown: [
        { label: 'Retransform Batches', value: FormattingService.formatNumber(retransformBatches) }
      ]
    }
  ];
});

const sortedClassLoaders = computed<ClassLoaderStat[]>(() => {
  const column = sortColumn.value;
  const direction = sortDirection.value === 'asc' ? 1 : -1;
  return [...classLoaders.value].sort((a, b) => {
    const left = a[column];
    const right = b[column];
    if (typeof left === 'number' && typeof right === 'number') {
      return (left - right) * direction;
    }
    return String(left ?? '').localeCompare(String(right ?? '')) * direction;
  });
});

const classLoadersView = useTableView<ClassLoaderStat>(sortedClassLoaders, {
  searchableText: r => `${r.name} ${r.parentName ?? ''}`
});

const classLoadsView = useTableView<ClassLoadEntry>(
  () => classLoadActivity.value?.slowest ?? [],
  {
    searchableText: r => `${r.className ?? ''} ${r.definingClassLoader ?? ''}`
  }
);

const redefinitionsView = useTableView<ClassRedefinitionStat>(
  () => redefinitionData.value?.redefinitions ?? [],
  {
    searchableText: r => r.className ?? ''
  }
);

const retransformsView = useTableView<RetransformBatch>(
  () => redefinitionData.value?.retransforms ?? []
);

const onSort = (column: string) => {
  const typedColumn = column as SortColumn;
  if (sortColumn.value === typedColumn) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortColumn.value = typedColumn;
    sortDirection.value = 'desc';
  }
};

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileClassLoadingClient(profileId);

    const [overviewResult, timelineResult, classLoadersResult, classLoadsResult, redefinitionsResult] =
      await Promise.all([
        client.getOverview(),
        client.getTimeline(),
        client.getClassLoaders(),
        client.getClassLoads(),
        client.getRedefinitions()
      ]);

    overview.value = overviewResult;
    timeline.value = timelineResult;
    classLoaders.value = classLoadersResult;
    classLoadActivity.value = classLoadsResult;
    redefinitionData.value = redefinitionsResult;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load class-loading data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.class-loading-container {
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

.class-cell {
  max-width: 460px;
}
</style>
