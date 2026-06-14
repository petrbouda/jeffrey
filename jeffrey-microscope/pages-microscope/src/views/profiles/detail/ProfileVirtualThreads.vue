<template>
  <LoadingState v-if="loading" message="Loading virtual-thread analysis..." />

  <ErrorState v-else-if="error" message="Failed to load virtual-thread analysis" />

  <div v-else>
    <PageHeader
      title="Virtual Threads"
      description="Project Loom diagnostics: carrier pinning, submit failures, and thread lifecycle"
      icon="bi-pin-angle"
    />

    <EmptyState
      v-if="!hasData"
      icon="bi-pin-angle"
      title="No virtual-thread events in this recording"
      description="This profile contains no virtual-thread pinning, submit-failure, or lifecycle events."
    />

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Pinning -->
      <div v-show="activeTab === 'pinning'">
        <ChartDescription
          shows="Pinning occurrences and total pinned time per second"
          use-case="Pinning ties a virtual thread to its carrier and serializes work — the top Loom scalability footgun"
        />
        <TimeSeriesChart
          :primary-data="pinningCountData"
          :secondary-data="pinningTimeData"
          primary-title="Pinning Events"
          secondary-title="Pinned Time"
          :primary-axis-type="AxisFormatType.NUMBER"
          :secondary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :independent-secondary-axis="true"
          :visible-minutes="60"
          primary-color="#EA4335"
          secondary-color="#FBBC04"
        />

        <div class="row mt-4">
          <div class="col-lg-5">
            <h6 class="section-title">Pinning Duration Distribution</h6>
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                  <tr>
                    <th>Duration</th>
                    <th class="text-end">Incidents</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="bucket in data!.pinningDistribution" :key="bucket.label">
                    <td>{{ bucket.label }}</td>
                    <td class="text-end">{{ FormattingService.formatNumber(bucket.count) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="col-lg-7">
            <h6 class="section-title">Top Pinned Virtual Threads</h6>
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                  <tr>
                    <th>Virtual Thread</th>
                    <th class="text-end">Incidents</th>
                    <th class="text-end">Total Pinned</th>
                    <th class="text-end">Max Pinned</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(t, i) in data!.topPinnedThreads" :key="i">
                    <td>{{ t.threadName }}</td>
                    <td class="text-end">{{ FormattingService.formatNumber(t.count) }}</td>
                    <td class="text-end">
                      {{ FormattingService.formatDuration2Units(t.totalNanos) }}
                    </td>
                    <td class="text-end">
                      {{ FormattingService.formatDuration2Units(t.maxNanos) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <EmptyState
              v-if="data!.topPinnedThreads.length === 0"
              icon="bi-check-circle"
              title="No pinning recorded"
            />
          </div>
        </div>

        <p class="hint mt-3">
          <i class="bi bi-lightbulb"></i>
          To see <em>where</em> the pinning happens, open the weighted
          <strong>jdk.VirtualThreadPinned</strong> flamegraph in the Visualization → Flamegraphs
          section.
        </p>
      </div>

      <!-- Submit Failures -->
      <div v-show="activeTab === 'submit-failures'">
        <ChartDescription
          shows="Virtual threads that failed to be submitted to a carrier"
          use-case="Submit failures usually indicate carrier-pool rejection, executor shutdown, or a scheduling bug"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Time</th>
                <th>Virtual Thread</th>
                <th>Exception</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(f, i) in data!.submitFailures" :key="i">
                <td>
                  {{ FormattingService.formatDuration2Units(f.timeOffsetMillis * 1_000_000) }}
                </td>
                <td>{{ f.threadName }}</td>
                <td>{{ f.exceptionMessage }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.submitFailures.length === 0"
          icon="bi-check-circle"
          title="No submit failures"
          description="No jdk.VirtualThreadSubmitFailed events were recorded."
        />
      </div>

      <!-- Lifecycle -->
      <div v-show="activeTab === 'lifecycle'">
        <ChartDescription
          shows="Virtual-thread creation, completion, and the derived live count over time"
          use-case="A live count that climbs without bound signals a virtual-thread leak; spikes signal runaway spawning"
        />
        <TimeSeriesChart
          v-if="hasLifecycle"
          :primary-data="startedData"
          :secondary-data="endedData"
          :tertiary-data="liveData"
          primary-title="Started"
          secondary-title="Ended"
          tertiary-title="Live"
          :primary-axis-type="AxisFormatType.NUMBER"
          :secondary-axis-type="AxisFormatType.NUMBER"
          :tertiary-axis-type="AxisFormatType.NUMBER"
          :visible-minutes="60"
          primary-color="#34A853"
          secondary-color="#9AA0A6"
          tertiary-color="#4285F4"
        />
        <EmptyState
          v-else
          icon="bi-activity"
          title="No lifecycle events"
          description="Enable jdk.VirtualThreadStart and jdk.VirtualThreadEnd in the recording to see creation/completion and live-count trends (disabled by default and high-volume)."
        />
      </div>

      <!-- About -->
      <div v-show="activeTab === 'about'">
        <ConfigurationSection title="How Virtual Thread Analysis Works" icon="bi-info-circle">
          <p class="about-text">
            Virtual threads (Project Loom) run many lightweight threads on a small pool of carrier
            threads. The signal that matters most for throughput is <strong>pinning</strong>: when a
            virtual thread cannot unmount from its carrier (e.g. inside a native frame), it blocks
            the carrier and defeats the scalability benefit. This page surfaces pinning over time,
            by duration, and by thread; <strong>submit failures</strong> (a functionality/bug
            signal); and, when enabled, the virtual-thread <strong>lifecycle</strong> for leak and
            spawn-rate analysis.
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
import ProfileVirtualThreadsClient from '@/services/api/ProfileVirtualThreadsClient';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type VirtualThreadData from '@/services/api/model/VirtualThreadModels';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<VirtualThreadData>();

const tabs = [
  { id: 'pinning', label: 'Pinning', icon: 'pin-angle' },
  { id: 'submit-failures', label: 'Submit Failures', icon: 'exclamation-octagon' },
  { id: 'lifecycle', label: 'Lifecycle', icon: 'activity' },
  { id: 'about', label: 'About', icon: 'info-circle' }
];
const activeTab = ref(tabs[0].id);

const hasData = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return false;
  }
  return h.pinningCount > 0 || h.submitFailedCount > 0 || h.startedCount > 0;
});

const hasLifecycle = computed(() => (data.value?.header.startedCount ?? 0) > 0);

const pinningCountData = computed(() => data.value?.pinningTimeline.series?.[0]?.data ?? []);
const pinningTimeData = computed(() => data.value?.pinningTimeline.series?.[1]?.data ?? []);
const startedData = computed(() => data.value?.lifecycle.series?.[0]?.data ?? []);
const endedData = computed(() => data.value?.lifecycle.series?.[1]?.data ?? []);
const liveData = computed(() => data.value?.lifecycle.series?.[2]?.data ?? []);

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'pin-angle',
      title: 'Pinning Events',
      value: FormattingService.formatNumber(h.pinningCount),
      variant: 'danger' as const,
      breakdown: [
        { label: 'Total', value: FormattingService.formatDuration2Units(h.totalPinnedNanos) },
        { label: 'Max', value: FormattingService.formatDuration2Units(h.maxPinnedNanos) }
      ]
    },
    {
      icon: 'exclamation-octagon',
      title: 'Submit Failures',
      value: FormattingService.formatNumber(h.submitFailedCount),
      variant: h.submitFailedCount > 0 ? ('warning' as const) : ('success' as const)
    },
    {
      icon: 'activity',
      title: 'Peak Live',
      value: FormattingService.formatNumber(h.peakLiveCount),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Started', value: FormattingService.formatNumber(h.startedCount) },
        { label: 'Ended', value: FormattingService.formatNumber(h.endedCount) }
      ]
    }
  ];
});

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const client = new ProfileVirtualThreadsClient(route.params.profileId as string);
    data.value = await client.getData();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading virtual-thread analysis:', err);
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

.hint {
  font-size: 0.85rem;
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
</style>
