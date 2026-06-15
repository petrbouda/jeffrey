<template>
  <LoadingState v-if="loading" message="Loading G1 analysis..." />

  <ErrorState v-else-if="error" message="Failed to load G1 analysis" />

  <div v-else>
    <PageHeader
      title="G1 Analysis"
      description="Deep-dive into G1 behaviour: pause anatomy, heap regions, evacuation and concurrent marking"
      icon="bi-diagram-3"
    />

    <EmptyState
      v-if="!hasData"
      icon="bi-diagram-3"
      title="No G1 events in this recording"
      description="This profile was not produced with G1, or the G1 JFR events were not enabled."
    />

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Pause Phases -->
      <div v-show="activeTab === 'phases'">
        <ChartDescription
          shows="Where each G1 pause spends its time, aggregated by sub-phase"
          use-case="Find the dominant pause cost — Object Copy (evacuation), remembered-set work, or reference processing"
        />
        <DataTable v-if="data!.pausePhases.length > 0">
          <template #toolbar>
            <TableToolbar v-model="pausePhasesView.query" search-placeholder="Filter phases...">
              <span class="toolbar-info">Pause Phases</span>
              <template #filters>
                <Badge key-label="Total" :value="pausePhasesView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Phase</th>
              <th>Level</th>
              <th class="text-end">Count</th>
              <th class="text-end">Total</th>
              <th class="text-end">Avg</th>
              <th class="text-end">Max</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="phase in pausePhasesView.visible" :key="`${phase.level}-${phase.name}`">
              <td>{{ phase.name }}</td>
              <td><Badge :value="levelLabel(phase.level)" variant="secondary" size="s" /></td>
              <td class="text-end">{{ FormattingService.formatNumber(phase.count) }}</td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(phase.totalNanos) }}
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(phase.avgNanos) }}
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(phase.maxNanos) }}
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="pausePhasesView.visible.length"
              :match-count="pausePhasesView.matchCount"
              :total="pausePhasesView.total"
              :expanded="pausePhasesView.expanded"
              :page-size="pausePhasesView.pageSize"
              @toggle="pausePhasesView.toggle"
            />
          </template>
        </DataTable>
        <DisabledEventsNotice
          v-if="data!.pausePhases.length === 0"
          title="No pause-phase events"
          icon="bi-bar-chart-steps"
          action-label="Record with the detailed GC tier, then re-record and re-import"
          :command="detailedGcCommand"
        >
          <p>
            The phase breakdown is built from the detailed phase-pause sub-events
            (<code>jdk.GCPhasePause*</code>). These belong to G1's <strong>detailed</strong> GC tier:
            the bundled <code>default</code> config records GC at the <code>normal</code> detail
            level, where they are effectively <strong>off</strong>, while the <code>profile</code>
            config sets the GC detail level to <code>detailed</code> and turns them on.
          </p>
          <p>
            Re-record with <code>settings=profile</code> (which selects the <code>detailed</code> GC
            level), or enable the specific events inline with the command above.
          </p>

          <template #action>
            <p>
              <strong>A — inline, no extra file.</strong> Use the copyable command above: it keeps
              the bundled <code>profile</code> config and adds the detailed-tier G1 GC events on top.
            </p>
            <p>
              <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
              <code>gc-detailed.jfc</code> and record with
              <code>settings=profile,settings=gc-detailed.jfc</code>:
            </p>
            <pre class="jfc-block">{{ gcDetailedJfcSnippet }}</pre>
            <p>Re-import the <code>.jfr</code> afterwards to populate the phase breakdown.</p>
          </template>
        </DisabledEventsNotice>
      </div>

      <!-- Heap Regions -->
      <div v-show="activeTab === 'regions'">
        <ChartDescription
          shows="Heap-region composition over time and the per-region layout at each snapshot"
          use-case="Spot humongous-allocation pressure and how Eden/Survivor/Old occupancy evolves"
        />
        <TimeSeriesChart
          :primary-data="edenData"
          :secondary-data="survivorData"
          :tertiary-data="oldData"
          primary-title="Eden"
          secondary-title="Survivor"
          tertiary-title="Old"
          :primary-axis-type="AxisFormatType.BYTES"
          :secondary-axis-type="AxisFormatType.BYTES"
          :tertiary-axis-type="AxisFormatType.BYTES"
          :visible-minutes="60"
          primary-color="#4285F4"
          secondary-color="#34A853"
          tertiary-color="#8E44AD"
          :stacked="true"
        />
        <h6 class="section-title mt-4">Region Layout</h6>
        <G1RegionHeatmap :snapshots="data!.regionSnapshots" />
      </div>

      <!-- Evacuation -->
      <div v-show="activeTab === 'evacuation'">
        <ChartDescription
          shows="Per-collection evacuation cost and to-space exhaustion (evacuation failures)"
          use-case="Evacuation failures are the usual cause of surprise Full GCs and pause spikes"
        />
        <div v-if="data!.evacuationFailures.length > 0" class="alert-banner mb-3">
          <i class="bi bi-exclamation-triangle-fill"></i>
          {{ data!.header.evacuationFailureCount }} evacuation failure(s) detected across
          {{ data!.evacuationFailures.length }} collection(s) — G1 ran out of to-space.
        </div>
        <DataTable v-if="data!.evacuations.length > 0">
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">Evacuations</span>
              <template #filters>
                <Badge key-label="Total" :value="evacuationsView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>GC Id</th>
              <th class="text-end">CSet Regions</th>
              <th class="text-end">Used Before</th>
              <th class="text-end">Used After</th>
              <th class="text-end">Bytes Copied</th>
              <th class="text-end">Regions Freed</th>
              <th class="text-end">Failures</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ev in evacuationsView.visible" :key="ev.gcId">
              <td>{{ ev.gcId }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(ev.cSetRegions) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(ev.cSetUsedBefore) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(ev.cSetUsedAfter) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(ev.bytesCopied) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(ev.regionsFreed) }}</td>
              <td class="text-end">
                <Badge
                  v-if="failureCount(ev.gcId) > 0"
                  :value="failureCount(ev.gcId)"
                  variant="danger"
                  size="s"
                />
                <span v-else>-</span>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="evacuationsView.visible.length"
              :match-count="evacuationsView.matchCount"
              :total="evacuationsView.total"
              :expanded="evacuationsView.expanded"
              :page-size="evacuationsView.pageSize"
              @toggle="evacuationsView.toggle"
            />
          </template>
        </DataTable>
        <DisabledEventsNotice
          v-if="data!.evacuations.length === 0"
          title="No evacuation events"
          icon="bi-box-arrow-right"
          action-label="Record with the detailed GC tier, then re-record and re-import"
          :command="detailedGcCommand"
        >
          <p>
            Per-collection evacuation cost comes from <code>jdk.EvacuationInformation</code>, a
            detailed-tier GC event. The bundled <code>default</code> config records GC at the
            <code>normal</code> detail level, where it is effectively <strong>off</strong>; the
            <code>profile</code> config sets the GC detail level to <code>detailed</code> and turns
            it on. Re-record with <code>settings=profile</code>, or enable the specific events inline
            with the command above.
          </p>
        </DisabledEventsNotice>
      </div>

      <!-- Marking (IHOP / MMU) -->
      <div v-show="activeTab === 'marking'">
        <ChartDescription
          shows="Adaptive IHOP threshold vs old-generation occupancy"
          use-case="When occupancy crosses the threshold, G1 starts a concurrent marking cycle — verify marking keeps up with allocation"
        />
        <TimeSeriesChart
          :primary-data="ihopThresholdData"
          :secondary-data="ihopOccupancyData"
          primary-title="IHOP Threshold"
          secondary-title="Old Gen Occupancy"
          :primary-axis-type="AxisFormatType.BYTES"
          :secondary-axis-type="AxisFormatType.BYTES"
          :visible-minutes="60"
          primary-color="#FBBC04"
          secondary-color="#EA4335"
          :x-annotations="missedPauseAnnotations"
        />
        <DataTable v-if="data!.mmu.length > 0" class="mt-4">
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">Pause-Target Adherence (MMU)</span>
              <template #filters>
                <Badge key-label="Total" :value="mmuView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>GC Id</th>
              <th class="text-end">GC Time</th>
              <th class="text-end">Pause Target</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in mmuView.visible" :key="m.gcId">
              <td>{{ m.gcId }}</td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(m.gcTimeNanos) }}
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(m.pauseTargetNanos) }}
              </td>
              <td>
                <Badge
                  :value="m.gcTimeNanos > m.pauseTargetNanos ? 'Missed' : 'Met'"
                  :variant="m.gcTimeNanos > m.pauseTargetNanos ? 'danger' : 'success'"
                  size="s"
                />
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="mmuView.visible.length"
              :match-count="mmuView.matchCount"
              :total="mmuView.total"
              :expanded="mmuView.expanded"
              :page-size="mmuView.pageSize"
              @toggle="mmuView.toggle"
            />
          </template>
        </DataTable>
        <EmptyState v-if="data!.mmu.length === 0" icon="bi-clock-history" title="No MMU events" />
      </div>

      <!-- Anomalies -->
      <div v-show="activeTab === 'anomalies'">
        <ChartDescription
          shows="Explicit System.gc() calls and GC-locker stalls"
          use-case="Explicit GCs are usually a bug; GC-locker stalls delay collection while threads sit in JNI critical sections"
        />
        <DataTable v-if="data!.systemGcs.length > 0">
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">Explicit GC Calls (System.gc())</span>
              <template #filters>
                <Badge key-label="Total" :value="systemGcsView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Time</th>
              <th class="text-end">Duration</th>
              <th>Concurrent</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(sg, i) in systemGcsView.visible" :key="i">
              <td>
                {{ FormattingService.formatDuration2Units(sg.timeOffsetMillis * 1_000_000) }}
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(sg.durationNanos) }}
              </td>
              <td>
                <Badge
                  :value="sg.invokedConcurrent ? 'Yes' : 'No'"
                  :variant="sg.invokedConcurrent ? 'info' : 'warning'"
                  size="s"
                />
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="systemGcsView.visible.length"
              :match-count="systemGcsView.matchCount"
              :total="systemGcsView.total"
              :expanded="systemGcsView.expanded"
              :page-size="systemGcsView.pageSize"
              @toggle="systemGcsView.toggle"
            />
          </template>
        </DataTable>
        <EmptyState
          v-if="data!.systemGcs.length === 0"
          icon="bi-check-circle"
          title="No explicit System.gc() calls"
        />

        <DataTable v-if="data!.gcLockers.length > 0" class="mt-4">
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">GC Locker Stalls</span>
              <template #filters>
                <Badge key-label="Total" :value="gcLockersView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Time</th>
              <th class="text-end">Duration</th>
              <th class="text-end">Threads in Critical Section</th>
              <th class="text-end">Threads Stalled</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(gl, i) in gcLockersView.visible" :key="i">
              <td>
                {{ FormattingService.formatDuration2Units(gl.timeOffsetMillis * 1_000_000) }}
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(gl.durationNanos) }}
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(gl.lockCount) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(gl.stallCount) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="gcLockersView.visible.length"
              :match-count="gcLockersView.matchCount"
              :total="gcLockersView.total"
              :expanded="gcLockersView.expanded"
              :page-size="gcLockersView.pageSize"
              @toggle="gcLockersView.toggle"
            />
          </template>
        </DataTable>
        <EmptyState
          v-if="data!.gcLockers.length === 0"
          icon="bi-check-circle"
          title="No GC-locker stalls"
        />
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding G1 Analysis"
          subtitle="Reconstructing G1's pause anatomy, regions, evacuation and marking from JFR"
        >
          <AboutCallout variant="intro">
            <p>
              G1 splits the heap into equal-sized regions that play the role of Eden, Survivor, Old
              or Humongous. Collections evacuate live objects out of a collection set; running out of
              to-space triggers an <strong>evacuation failure</strong> and, often, a Full GC. A
              concurrent marking cycle starts when old-generation occupancy crosses the adaptive
              <strong>IHOP</strong> threshold. This page reconstructs that behaviour from the
              G1-specific JFR events.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-diagram-3" title="What the Views Show">
            <FeatureGrid>
              <FeatureCard icon="bi-bar-chart-steps" variant="primary" title="Pause Phases">
                Where each G1 pause spends its time, aggregated by sub-phase. Surfaces the dominant
                cost — Object Copy (evacuation), remembered-set work, or reference processing.
              </FeatureCard>
              <FeatureCard icon="bi-grid-3x3" variant="info" title="Heap Regions">
                Eden / Survivor / Old composition over time plus the per-region layout at each
                snapshot. Spots humongous-allocation pressure and shifting occupancy.
              </FeatureCard>
              <FeatureCard icon="bi-box-arrow-right" variant="warning" title="Evacuation">
                Per-collection evacuation cost — collection-set size, bytes copied, regions freed —
                and to-space exhaustion (evacuation failures), the usual cause of surprise Full GCs.
              </FeatureCard>
              <FeatureCard icon="bi-graph-up" variant="success" title="IHOP & Marking">
                The adaptive IHOP threshold vs old-generation occupancy, plus pause-target adherence
                (MMU). Confirms concurrent marking keeps up with allocation.
              </FeatureCard>
              <FeatureCard icon="bi-exclamation-triangle" variant="danger" title="Anomalies">
                Explicit <code>System.gc()</code> calls (usually a bug) and GC-locker stalls that
                delay collection while threads sit in JNI critical sections.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.GCPhasePause</code> and its level sub-phases
                (<code>jdk.GCPhasePauseLevel1</code>…<code>4</code>) — the pause-phase anatomy.
              </li>
              <li>
                <code>jdk.G1HeapRegionInformation</code> — per-region type and occupancy snapshots
                behind the region composition and layout.
              </li>
              <li>
                <code>jdk.EvacuationInformation</code> — per-collection collection-set, copied bytes
                and freed regions; failures mark to-space exhaustion.
              </li>
              <li><code>jdk.GCLocker</code> — GC-locker stalls in the Anomalies view.</li>
              <li>
                <code>jdk.GarbageCollection</code> — collection-level timing that surfaces explicit
                <code>System.gc()</code> calls.
              </li>
            </ul>
            <p>
              The detailed-tier events — <code>jdk.GCPhasePause</code> sub-phases and
              <code>jdk.EvacuationInformation</code> — are effectively <strong>off</strong> in the
              bundled <code>default</code> config (GC at the <code>normal</code> detail level) and
              <strong>on</strong> with <code>profile</code> (which selects the <code>detailed</code>
              GC level). Re-record with <code>settings=profile</code> to populate those views.
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
import TimeSeriesChart, { type ChartAnnotation } from '@/components/TimeSeriesChart.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import Badge from '@/components/Badge.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import G1RegionHeatmap from '@/components/gc/G1RegionHeatmap.vue';
import { useTableView } from '@/composables/useTableView';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type G1AnalysisData from '@/services/api/model/G1AnalysisData';

const route = useRoute();

const detailedGcCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.GCPhasePause#enabled=true,jdk.EvacuationInformation#enabled=true,filename=app.jfr,dumponexit=true -jar app.jar';

const gcDetailedJfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.GCPhasePause">
    <setting name="enabled">true</setting>
    <setting name="threshold">0 ms</setting>
  </event>
  <event name="jdk.EvacuationInformation">
    <setting name="enabled">true</setting>
  </event>
</configuration>`;

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<G1AnalysisData>();

const tabs = [
  { id: 'phases', label: 'Pause Phases', icon: 'bar-chart-steps' },
  { id: 'regions', label: 'Heap Regions', icon: 'grid-3x3' },
  { id: 'evacuation', label: 'Evacuation', icon: 'box-arrow-right' },
  { id: 'marking', label: 'IHOP & Marking', icon: 'graph-up' },
  { id: 'anomalies', label: 'Anomalies', icon: 'exclamation-triangle' },
  { id: 'about', label: 'How It Works', icon: 'book' }
];
const activeTab = ref(tabs[0].id);

const hasData = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return false;
  }
  return h.youngCount > 0 || h.mixedCount > 0 || h.fullCount > 0 || h.regionCount > 0;
});

const edenData = computed(() => data.value?.regionComposition.series?.[0]?.data ?? []);
const survivorData = computed(() => data.value?.regionComposition.series?.[1]?.data ?? []);
const oldData = computed(() => data.value?.regionComposition.series?.[2]?.data ?? []);
const ihopThresholdData = computed(() => data.value?.ihopTimeline.series?.[0]?.data ?? []);
const ihopOccupancyData = computed(() => data.value?.ihopTimeline.series?.[1]?.data ?? []);

// Mark each collection that blew its pause target (gcTime > pauseTarget) on the IHOP timeline.
// The IHOP series x-axis is in seconds-from-start, so convert the entry offset from millis.
const missedPauseAnnotations = computed<ChartAnnotation[]>(() =>
  (data.value?.mmu ?? [])
    .filter(m => m.gcTimeNanos > m.pauseTargetNanos)
    .map(m => ({
      x: m.timeOffsetMillis / 1000,
      label: 'Pause Target',
      color: '#EA4335',
      tooltipHtml:
        '<div class="tsc-anno-tip-title">⚠ Pause target missed</div>' +
        `<div>GC <strong>${m.gcId}</strong></div>` +
        `<div class="tsc-anno-tip-time">GC time ${FormattingService.formatDuration2Units(m.gcTimeNanos)} ` +
        `&gt; target ${FormattingService.formatDuration2Units(m.pauseTargetNanos)}</div>`
    }))
);

const failureByGcId = computed(() => {
  const map = new Map<number, number>();
  for (const failure of data.value?.evacuationFailures ?? []) {
    map.set(failure.gcId, failure.count);
  }
  return map;
});

const failureCount = (gcId: number): number => failureByGcId.value.get(gcId) ?? 0;

const pausePhasesView = useTableView(() => data.value?.pausePhases ?? [], {
  searchableText: p => p.name
});
const evacuationsView = useTableView(() => data.value?.evacuations ?? []);
const mmuView = useTableView(() => data.value?.mmu ?? []);
const systemGcsView = useTableView(() => data.value?.systemGcs ?? []);
const gcLockersView = useTableView(() => data.value?.gcLockers ?? []);

const levelLabel = (level: number): string => {
  if (level < 0) {
    return 'Parallel';
  }
  if (level === 0) {
    return 'Pause';
  }
  return `Level ${level}`;
};

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'recycle',
      title: 'Collections',
      value: FormattingService.formatNumber(h.youngCount + h.mixedCount + h.fullCount),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Young', value: FormattingService.formatNumber(h.youngCount) },
        { label: 'Mixed', value: FormattingService.formatNumber(h.mixedCount) },
        { label: 'Full', value: FormattingService.formatNumber(h.fullCount) }
      ]
    },
    {
      icon: 'clock',
      title: 'Pause Time',
      value: FormattingService.formatDuration2Units(h.totalPauseNanos),
      variant: 'info' as const,
      breakdown: [
        { label: 'Avg', value: FormattingService.formatDuration2Units(h.avgPauseNanos) },
        { label: 'P99', value: FormattingService.formatDuration2Units(h.p99PauseNanos) },
        { label: 'Max', value: FormattingService.formatDuration2Units(h.maxPauseNanos) }
      ]
    },
    {
      icon: 'exclamation-triangle',
      title: 'Evacuation Failures',
      value: FormattingService.formatNumber(h.evacuationFailureCount),
      variant: h.evacuationFailureCount > 0 ? ('danger' as const) : ('success' as const)
    },
    {
      icon: 'grid-3x3',
      title: 'Heap Regions',
      value: FormattingService.formatNumber(h.regionCount),
      variant: 'warning' as const
    }
  ];
});

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const client = new ProfileGCClient(route.params.profileId as string);
    data.value = await client.getG1Analysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading G1 analysis:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(loadData);
</script>

<style scoped>
.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.section-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
  margin-bottom: 0.5rem;
}

.alert-banner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: var(--color-bg-card);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  color: var(--color-danger);
  font-size: 0.85rem;
  font-weight: 600;
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
