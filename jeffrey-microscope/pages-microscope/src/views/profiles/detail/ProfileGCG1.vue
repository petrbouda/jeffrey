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
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
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
              <tr v-for="phase in data!.pausePhases" :key="`${phase.level}-${phase.name}`">
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
          </table>
        </div>
        <EmptyState
          v-if="data!.pausePhases.length === 0"
          icon="bi-bar-chart-steps"
          title="No pause-phase events"
          description="Enable jdk.GCPhasePause* events in the recording for the phase breakdown."
        />
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
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
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
              <tr v-for="ev in data!.evacuations" :key="ev.gcId">
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
          </table>
        </div>
        <EmptyState
          v-if="data!.evacuations.length === 0"
          icon="bi-box-arrow-right"
          title="No evacuation events"
          description="Enable jdk.EvacuationInformation in the recording."
        />
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
        />
        <h6 class="section-title mt-4">Pause-Target Adherence (MMU)</h6>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>GC Id</th>
                <th class="text-end">GC Time</th>
                <th class="text-end">Pause Target</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in data!.mmu" :key="m.gcId">
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
          </table>
        </div>
        <EmptyState v-if="data!.mmu.length === 0" icon="bi-clock-history" title="No MMU events" />
      </div>

      <!-- Anomalies -->
      <div v-show="activeTab === 'anomalies'">
        <ChartDescription
          shows="Explicit System.gc() calls and GC-locker stalls"
          use-case="Explicit GCs are usually a bug; GC-locker stalls delay collection while threads sit in JNI critical sections"
        />
        <h6 class="section-title">Explicit GC Calls (System.gc())</h6>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Time</th>
                <th class="text-end">Duration</th>
                <th>Concurrent</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(sg, i) in data!.systemGcs" :key="i">
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
          </table>
        </div>
        <EmptyState
          v-if="data!.systemGcs.length === 0"
          icon="bi-check-circle"
          title="No explicit System.gc() calls"
        />

        <h6 class="section-title mt-4">GC Locker Stalls</h6>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Time</th>
                <th class="text-end">Duration</th>
                <th class="text-end">Threads in Critical Section</th>
                <th class="text-end">Threads Stalled</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(gl, i) in data!.gcLockers" :key="i">
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
          </table>
        </div>
        <EmptyState
          v-if="data!.gcLockers.length === 0"
          icon="bi-check-circle"
          title="No GC-locker stalls"
        />
      </div>

      <!-- About -->
      <div v-show="activeTab === 'about'">
        <ConfigurationSection title="How G1 Analysis Works" icon="bi-info-circle">
          <p class="about-text">
            G1 splits the heap into equal-sized regions that play the role of Eden, Survivor, Old or
            Humongous. Collections evacuate live objects out of a collection set; running out of
            to-space triggers an <strong>evacuation failure</strong> and, often, a Full GC. A
            concurrent marking cycle starts when old-generation occupancy crosses the adaptive
            <strong>IHOP</strong> threshold. This page reconstructs that behaviour from the
            G1-specific JFR events: pause-phase anatomy, region composition and layout, evacuation
            cost and failures, IHOP/MMU marking behaviour, and explicit-GC / GC-locker anomalies.
          </p>
        </ConfigurationSection>
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
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import ConfigurationSection from '@/components/ConfigurationSection.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
import G1RegionHeatmap from '@/components/gc/G1RegionHeatmap.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type G1AnalysisData from '@/services/api/model/G1AnalysisData';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<G1AnalysisData>();

const tabs = [
  { id: 'phases', label: 'Pause Phases', icon: 'bar-chart-steps' },
  { id: 'regions', label: 'Heap Regions', icon: 'grid-3x3' },
  { id: 'evacuation', label: 'Evacuation', icon: 'box-arrow-right' },
  { id: 'marking', label: 'IHOP & Marking', icon: 'graph-up' },
  { id: 'anomalies', label: 'Anomalies', icon: 'exclamation-triangle' },
  { id: 'about', label: 'About', icon: 'info-circle' }
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

const failureByGcId = computed(() => {
  const map = new Map<number, number>();
  for (const failure of data.value?.evacuationFailures ?? []) {
    map.set(failure.gcId, failure.count);
  }
  return map;
});

const failureCount = (gcId: number): number => failureByGcId.value.get(gcId) ?? 0;

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
.section-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
  margin-bottom: 0.5rem;
}

.about-text {
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--color-dark);
  margin: 0;
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
</style>
