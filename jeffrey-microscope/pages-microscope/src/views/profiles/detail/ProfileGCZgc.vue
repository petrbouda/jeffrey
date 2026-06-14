<template>
  <LoadingState v-if="loading" message="Loading ZGC analysis..." />

  <ErrorState v-else-if="error" message="Failed to load ZGC analysis" />

  <div v-else>
    <PageHeader
      title="ZGC Analysis"
      description="Deep-dive into ZGC behaviour: allocation stalls, generational cycles, pages and relocation"
      icon="bi-cpu"
    />

    <EmptyState
      v-if="!hasData"
      icon="bi-cpu"
      title="No ZGC events in this recording"
      description="This profile was not produced with ZGC, or the ZGC JFR events were not enabled."
    />

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Allocation Stalls -->
      <div v-show="activeTab === 'stalls'">
        <ChartDescription
          shows="Allocation-stall count and total stall time per second"
          use-case="Allocation stalls are the only place ZGC pauses become visible to application threads — the top low-latency signal"
        />
        <TimeSeriesChart
          :primary-data="stallCountData"
          :secondary-data="stallTimeData"
          primary-title="Allocation Stalls"
          secondary-title="Stall Time"
          :primary-axis-type="AxisFormatType.NUMBER"
          :secondary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :independent-secondary-axis="true"
          :visible-minutes="60"
          primary-color="#EA4335"
          secondary-color="#FBBC04"
        />

        <div class="row mt-4">
          <div class="col-lg-5">
            <h6 class="section-title">Stalls by Page Type</h6>
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                  <tr>
                    <th>Type</th>
                    <th class="text-end">Count</th>
                    <th class="text-end">Total</th>
                    <th class="text-end">Max</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="st in data!.stallTypes" :key="st.type">
                    <td><Badge :value="st.type" variant="secondary" size="s" /></td>
                    <td class="text-end">{{ FormattingService.formatNumber(st.count) }}</td>
                    <td class="text-end">
                      {{ FormattingService.formatDuration2Units(st.totalNanos) }}
                    </td>
                    <td class="text-end">
                      {{ FormattingService.formatDuration2Units(st.maxNanos) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <EmptyState
              v-if="data!.stallTypes.length === 0"
              icon="bi-check-circle"
              title="No allocation stalls"
            />
          </div>
          <div class="col-lg-7">
            <h6 class="section-title">Top Stalling Threads</h6>
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                  <tr>
                    <th>Thread</th>
                    <th class="text-end">Stalls</th>
                    <th class="text-end">Total Stall Time</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="site in data!.stallSites" :key="site.threadName">
                    <td>{{ site.threadName }}</td>
                    <td class="text-end">{{ FormattingService.formatNumber(site.count) }}</td>
                    <td class="text-end">
                      {{ FormattingService.formatDuration2Units(site.totalNanos) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <EmptyState
              v-if="data!.stallSites.length === 0"
              icon="bi-check-circle"
              title="No stalling threads"
            />
          </div>
        </div>
      </div>

      <!-- Cycles -->
      <div v-show="activeTab === 'cycles'">
        <ChartDescription
          shows="Generational ZGC collection cycles (young and old)"
          use-case="Confirm collection frequency and the tenuring threshold driving promotion to the old generation"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>GC Id</th>
                <th>Generation</th>
                <th class="text-end">Duration</th>
                <th class="text-end">Tenuring Threshold</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="cycle in data!.cycles" :key="`${cycle.generation}-${cycle.gcId}`">
                <td>{{ cycle.gcId }}</td>
                <td>
                  <Badge
                    :value="cycle.generation"
                    :variant="cycle.generation === 'Old' ? 'warning' : 'info'"
                    size="s"
                  />
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(cycle.durationNanos) }}
                </td>
                <td class="text-end">
                  {{ cycle.generation === 'Young' ? cycle.tenuringThreshold : '-' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.cycles.length === 0"
          icon="bi-recycle"
          title="No ZGC cycles recorded"
        />
      </div>

      <!-- Pages & Memory -->
      <div v-show="activeTab === 'pages'">
        <ChartDescription
          shows="Page-allocation throughput over time"
          use-case="High sustained page allocation with stalls indicates the collector cannot keep up with the allocation rate"
        />
        <TimeSeriesChart
          :primary-data="pageAllocationData"
          primary-title="Page Allocation"
          :primary-axis-type="AxisFormatType.BYTES"
          :visible-minutes="60"
          primary-color="#4285F4"
        />
        <h6 class="section-title mt-4">Uncommitted Memory (returned to OS)</h6>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Time</th>
                <th class="text-end">Uncommitted</th>
                <th class="text-end">Duration</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(u, i) in data!.uncommits" :key="i">
                <td>
                  {{ FormattingService.formatDuration2Units(u.timeOffsetMillis * 1_000_000) }}
                </td>
                <td class="text-end">{{ FormattingService.formatBytes(u.uncommittedBytes) }}</td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(u.durationNanos) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.uncommits.length === 0"
          icon="bi-arrow-down-circle"
          title="No uncommit events"
        />
      </div>

      <!-- Relocation -->
      <div v-show="activeTab === 'relocation'">
        <ChartDescription
          shows="Relocation-set composition per cycle (pages)"
          use-case="Large relocation sets increase concurrent work; empty pages are reclaimed without relocation"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Time</th>
                <th class="text-end">Total Pages</th>
                <th class="text-end">Empty Pages</th>
                <th class="text-end">Relocated Pages</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in data!.relocations" :key="i">
                <td>
                  {{ FormattingService.formatDuration2Units(r.timeOffsetMillis * 1_000_000) }}
                </td>
                <td class="text-end">{{ FormattingService.formatNumber(r.total) }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(r.empty) }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(r.relocate) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.relocations.length === 0"
          icon="bi-arrows-move"
          title="No relocation-set events"
        />
      </div>

      <!-- About -->
      <div v-show="activeTab === 'about'">
        <ConfigurationSection title="How ZGC Analysis Works" icon="bi-info-circle">
          <p class="about-text">
            ZGC is a concurrent, region-based collector whose pauses are sub-millisecond. The signal
            that matters for latency is the <strong>allocation stall</strong>: when the application
            allocates faster than ZGC can reclaim memory, mutator threads are stalled until a page
            is available. This page surfaces stalls, generational young/old cycles, page-allocation
            throughput, memory returned to the OS, and relocation-set sizes.
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
import ProfileGCClient from '@/services/api/ProfileGCClient';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type ZgcAnalysisData from '@/services/api/model/ZgcAnalysisData';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<ZgcAnalysisData>();

const tabs = [
  { id: 'stalls', label: 'Allocation Stalls', icon: 'exclamation-octagon' },
  { id: 'cycles', label: 'GC Cycles', icon: 'recycle' },
  { id: 'pages', label: 'Pages & Memory', icon: 'hdd-stack' },
  { id: 'relocation', label: 'Relocation', icon: 'arrows-move' },
  { id: 'about', label: 'About', icon: 'info-circle' }
];
const activeTab = ref(tabs[0].id);

const hasData = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return false;
  }
  return h.stallCount > 0 || h.youngCycles > 0 || h.oldCycles > 0;
});

const stallCountData = computed(() => data.value?.stallTimeline.series?.[0]?.data ?? []);
const stallTimeData = computed(() => data.value?.stallTimeline.series?.[1]?.data ?? []);
const pageAllocationData = computed(() => data.value?.pageAllocation.series?.[0]?.data ?? []);

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'recycle',
      title: 'Collection Cycles',
      value: FormattingService.formatNumber(h.youngCycles + h.oldCycles),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Young', value: FormattingService.formatNumber(h.youngCycles) },
        { label: 'Old', value: FormattingService.formatNumber(h.oldCycles) }
      ]
    },
    {
      icon: 'exclamation-octagon',
      title: 'Allocation Stalls',
      value: FormattingService.formatNumber(h.stallCount),
      variant: 'danger' as const,
      breakdown: [
        { label: 'Total', value: FormattingService.formatDuration2Units(h.totalStallNanos) },
        { label: 'Max', value: FormattingService.formatDuration2Units(h.maxStallNanos) }
      ]
    },
    {
      icon: 'hdd-stack',
      title: 'Pages Allocated',
      value: FormattingService.formatBytes(h.pagesAllocatedBytes),
      variant: 'info' as const
    },
    {
      icon: 'arrow-down-circle',
      title: 'Memory Uncommitted',
      value: FormattingService.formatBytes(h.uncommittedBytes),
      variant: 'success' as const
    }
  ];
});

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const client = new ProfileGCClient(route.params.profileId as string);
    data.value = await client.getZgcAnalysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading ZGC analysis:', err);
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
</style>
