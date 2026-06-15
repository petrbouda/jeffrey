<template>
  <div class="allocations-container">
    <LoadingState v-if="loading" message="Loading allocation data..." />
    <ErrorState v-else-if="error" message="Failed to load allocation data" />

    <div v-else>
      <PageHeader
        title="Heap Allocations"
        description="Where heap allocation pressure comes from — allocation rate over time, the in-TLAB vs outside-TLAB split, and the top allocated types"
        icon="bi-box"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Allocation Rate -->
      <div v-show="activeTab === 'rate'">
        <ChartDescription use-case="A high steady rate drives GC frequency; spikes often precede young-GC pauses.">
          <template #shows>
            Bytes allocated per second across the recording.<template v-if="overview?.sampled"> Derived from sampled allocation events.</template>
          </template>
        </ChartDescription>
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="rateSeries"
            primaryTitle="Allocated Bytes / sec"
            :primaryAxisType="AxisFormatType.BYTES"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Before/After GC -->
      <div v-show="activeTab === 'heap-gc'">
        <ChartDescription
          shows="Heap used just before and just after each garbage collection — the classic sawtooth."
          use-case="A steadily rising post-GC floor means the live set is growing — a leak or unbounded cache, not just churn."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="heapBeforeAfterGcSeries"
            primaryTitle="Before/After GC"
            :primaryAxisType="AxisFormatType.BYTES"
            :visibleMinutes="60"
            primaryColor="#007bff"
            timeUnit="seconds"
          />
        </div>
      </div>

      <!-- Top Allocated Types -->
      <div v-show="activeTab === 'types'">
        <DisabledEventsNotice
          v-if="topTypes.length === 0"
          title="No allocation events recorded"
          icon="bi-box"
          action-label="Re-enable allocation profiling, then re-record and re-import"
          :command="enableCommand"
        >
          <p>
            The top-types breakdown is built from the JVM's allocation profiler. Since JDK&nbsp;16,
            the modern sampler <code>jdk.ObjectAllocationSample</code> (one throttled event per
            sampled allocation, carrying the class and a statistical weight) is
            <strong>enabled by default</strong> in both the bundled <code>default</code> and
            <code>profile</code> configs — at ~150 samples/s in <code>default</code> and 300/s in
            <code>profile</code> — and it supersedes the older, higher-volume
            <code>jdk.ObjectAllocationInNewTLAB</code> / <code>jdk.ObjectAllocationOutsideTLAB</code>
            pair (off by default).
          </p>
          <p>
            Because the sampler is on out of the box, an empty Allocations tab almost always means
            allocation profiling was deliberately turned <strong>off</strong> in a custom or minimal
            configuration — re-enable it (or simply record with <code>settings=profile</code>) and
            the page will populate.
          </p>

          <template #action>
            <p>
              <strong>A — inline, no extra file.</strong> Use the copyable command above: it records
              with the bundled <code>profile</code> config and explicitly forces
              <code>jdk.ObjectAllocationSample#enabled=true</code> so the sampler is never gated off.
            </p>
            <p>
              <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
              <code>allocations.jfc</code> and record with
              <code>settings=profile,settings=allocations.jfc</code>:
            </p>
            <pre class="jfc-block">{{ jfcSnippet }}</pre>
            <p>
              Re-import the <code>.jfr</code> into Jeffrey afterwards. The sampler is intentionally
              low-overhead — its weighted totals are statistically scaled estimates, ideal for
              proportions and trends rather than an exact byte count.
            </p>
          </template>
        </DisabledEventsNotice>
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="typesView.query" search-placeholder="Filter classes...">
              <span class="toolbar-info">Top allocated types</span>
              <template #filters>
                <Badge key-label="Showing" :value="typesView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th class="rank-col">#</th>
              <th>Class</th>
              <th class="text-end">Allocated</th>
              <th class="text-end">Events</th>
              <th class="share-col">Share</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(type, index) in typesView.visible" :key="type.className">
              <td class="text-muted">{{ index + 1 }}</td>
              <td class="class-cell" :title="type.className">
                <ClassNameDisplay :class-name="type.className" />
              </td>
              <td class="text-end">{{ FormattingService.formatBytes(type.bytes) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(type.count) }}</td>
              <td>
                <div class="share-bar">
                  <div class="share-bar-fill" :style="{ width: shareWidth(type.bytes) + '%' }"></div>
                </div>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="typesView.visible.length"
              :match-count="typesView.matchCount"
              :total="typesView.total"
              :expanded="typesView.expanded"
              :page-size="typesView.pageSize"
              @toggle="typesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- How It Works Tab -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Allocations"
          subtitle="Where the JVM puts new objects — and why allocation rate drives GC"
        >
          <AboutCallout variant="intro">
            <p>
              Allocating an object in the JVM is usually just bumping a pointer — extremely cheap. The
              cost shows up <em>later</em>: everything you allocate must eventually be collected, so a
              high allocation rate is the single biggest driver of GC frequency. This page shows how
              fast you allocate and which types dominate.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-box" title="How Allocation Works">
            <FeatureGrid>
              <FeatureCard icon="bi-box-seam" variant="success" title="TLAB (the fast path)">
                Each thread gets a Thread-Local Allocation Buffer — a private slice of Eden. Allocating
                is a lock-free pointer bump within it, so threads never contend. Almost all objects are
                born here.
              </FeatureCard>
              <FeatureCard icon="bi-box-arrow-up" variant="warning" title="Outside TLAB">
                Objects too large for the remaining TLAB (or larger than the TLAB itself) are allocated
                directly in the heap, taking a slower shared path. A high outside-TLAB share points at
                large arrays/buffers.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-repeat" variant="danger" title="Allocation → GC pressure">
                Fast allocation fills Eden quickly, triggering more young collections. Reducing
                allocation churn (object reuse, primitives, streaming) is often the cheapest way to cut
                GC overhead.
              </FeatureCard>
              <FeatureCard icon="bi-rulers" variant="info" title="Why bytes are estimates">
                JFR <em>samples</em> allocations rather than recording every one. Each sample carries a
                weight, so totals here are statistically scaled estimates — great for proportions and
                trends, not an exact byte count.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-graph-up" title="Reading the Charts">
            <FeatureGrid>
              <FeatureCard icon="bi-graph-up" variant="primary" title="Allocation Rate">
                Bytes allocated per second over time. Sustained high rates or spikes that line up with
                latency point at allocation-driven GC — correlate with the GC page.
              </FeatureCard>
              <FeatureCard icon="bi-list-ol" variant="info" title="Top Allocated Types">
                Which classes account for the most allocated bytes, with a share bar. <code>byte[]</code>,
                <code>char[]</code> and boxed types dominating usually means I/O buffers, strings or
                autoboxing churn.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.ObjectAllocationSample</code> — the modern, throttled allocation sampler
                (one event per ~N bytes per thread) with a weight. Low overhead, used when present.
              </li>
              <li>
                <code>jdk.ObjectAllocationInNewTLAB</code> / <code>jdk.ObjectAllocationOutsideTLAB</code>
                — the older pair, emitted when a new TLAB is needed or an allocation bypasses the TLAB.
                Higher volume; used as a fallback.
              </li>
            </ul>
            <p>
              Both carry the allocated class and size; the page prefers the sampled event and falls
              back to the TLAB pair, which is why the available detail depends on the recording's
              configuration.
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
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import Badge from '@/components/Badge.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileAllocationClient from '@/services/api/ProfileAllocationClient';
import ProfileHeapMemoryClient from '@/services/api/ProfileHeapMemoryClient';
import HeapMemoryTimeseriesType from '@/services/api/model/HeapMemoryTimeseriesType';
import type { AllocatedType, AllocationOverview } from '@/services/api/model/AllocationModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const enableCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.ObjectAllocationSample#enabled=true,filename=app.jfr,dumponexit=true -jar app.jar';

const jfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.ObjectAllocationSample">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
    <setting name="throttle">300/s</setting>
  </event>
</configuration>`;

const loading = ref(true);
const error = ref(false);

const overview = ref<AllocationOverview>();
const timeline = ref<TimeseriesData>();
const topTypes = ref<AllocatedType[]>([]);

const typesView = useTableView<AllocatedType>(topTypes, { searchableText: r => r.className });

const activeTab = ref('rate');

const heapBeforeAfterGcSeries = ref<number[][]>([]);

const rateSeries = computed<number[][]>(() => timeline.value?.series?.[0]?.data ?? []);
const maxTypeBytes = computed(() => (topTypes.value.length > 0 ? topTypes.value[0].bytes : 0));

const shareWidth = (bytes: number): number => {
  if (maxTypeBytes.value === 0) {
    return 0;
  }
  return Math.max(2, Math.round((bytes / maxTypeBytes.value) * 100));
};

const tabs = computed<TabBarItem[]>(() => [
  { id: 'rate', label: 'Allocation Rate', icon: 'graph-up' },
  { id: 'heap-gc', label: 'Before/After GC', icon: 'memory' },
  {
    id: 'types',
    label: 'Top Allocated Types',
    icon: 'box',
    badge: topTypes.value.length || undefined
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  const tlabBreakdown = o.sampled
    ? []
    : [
        { label: 'In TLAB', value: FormattingService.formatBytes(o.inTlabBytes) },
        { label: 'Outside TLAB', value: FormattingService.formatBytes(o.outsideTlabBytes) }
      ];
  return [
    {
      icon: 'box',
      title: 'Total Allocated',
      value: FormattingService.formatBytes(o.totalBytes),
      variant: 'highlight' as const,
      breakdown: tlabBreakdown
    },
    {
      icon: 'diagram-2',
      title: 'Distinct Types',
      value: FormattingService.formatNumber(o.distinctTypes),
      variant: 'info' as const
    },
    {
      icon: 'trophy',
      title: 'Dominant Type',
      value: o.dominantType ? simpleName(o.dominantType) : '—',
      variant: 'warning' as const
    },
    {
      icon: 'collection',
      title: 'Source',
      value: o.sampled ? 'Sampled' : 'TLAB events',
      variant: 'success' as const
    }
  ];
});

const simpleName = (className: string): string => {
  const lastDot = className.lastIndexOf('.');
  return lastDot > 0 ? className.substring(lastDot + 1) : className;
};

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileAllocationClient(profileId);
    const heapMemoryClient = new ProfileHeapMemoryClient(profileId);

    const [overviewResult, timelineResult, topTypesResult, heapBeforeAfterGcResult] =
      await Promise.all([
        client.getOverview(),
        client.getTimeline(),
        client.getTopTypes(),
        heapMemoryClient.getTimeseries(HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC)
      ]);

    overview.value = overviewResult;
    timeline.value = timelineResult;
    topTypes.value = topTypesResult;
    heapBeforeAfterGcSeries.value = heapBeforeAfterGcResult.data;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load allocation data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.allocations-container {
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

.rank-col {
  width: 48px;
}

.class-cell {
  max-width: 520px;
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
