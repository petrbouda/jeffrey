<template>
  <LoadingState v-if="loading" message="Loading virtual-thread analysis..." />

  <ErrorState v-else-if="error" message="Failed to load virtual-thread analysis" />

  <div v-else>
    <PageHeader
      title="Virtual Threads"
      description="Project Loom diagnostics: carrier pinning, submit failures, and thread lifecycle"
      icon="bi-pin-angle"
    />

    <div v-if="!hasData">
      <DisabledEventsNotice
        title="No virtual-thread events in this recording"
        icon="bi-pin-angle"
        action-label="Enable the virtual-thread events, then re-record and re-import"
        :command="enableCommand"
      >
        <p>
          Virtual threads (Project Loom — <code>Thread.ofVirtual()</code> and structured
          concurrency) run many lightweight threads on a small pool of OS <em>carrier</em> threads.
          The JVM reports their health through four JFR events, and this page stays empty until at
          least one of them is present in the recording.
        </p>
        <p>
          In the JDK's bundled <code>default</code> and <code>profile</code> configurations,
          <code>jdk.VirtualThreadPinned</code> and <code>jdk.VirtualThreadSubmitFailed</code> are
          <strong>already enabled</strong>, whereas <code>jdk.VirtualThreadStart</code> and
          <code>jdk.VirtualThreadEnd</code> are <strong>disabled</strong> because they are
          high-volume. So a fully empty page usually means the app never pinned past the pinning
          threshold, never failed a submit, and lifecycle events were turned off.
        </p>

        <template #action>
          <p><strong>What each event captures</strong></p>
          <ul>
            <li>
              <code>jdk.VirtualThreadPinned</code> — a virtual thread could not unmount from its
              carrier (a native frame, or before JDK 24 a <code>synchronized</code> block) and held
              it hostage. Carries a stack trace and the pinned duration. <strong>Enabled by
              default</strong>, but with a <code>threshold</code> of ~20&nbsp;ms, so shorter pins are
              not recorded. Drives the <em>Pinning</em> tab — the top Loom scalability footgun.
            </li>
            <li>
              <code>jdk.VirtualThreadSubmitFailed</code> — the scheduler rejected a virtual thread
              (carrier-pool rejection, executor shutdown, or a scheduling bug). <strong>Enabled by
              default.</strong> Drives the <em>Submit Failures</em> tab.
            </li>
            <li>
              <code>jdk.VirtualThreadStart</code> / <code>jdk.VirtualThreadEnd</code> — one event per
              virtual-thread creation and termination, used to derive the live-count trend.
              <strong>Disabled by default</strong> and high-volume — enable only for leak or
              spawn-rate analysis. Drives the <em>Lifecycle</em> tab.
            </li>
          </ul>

          <p><strong>Two ways to enable the events</strong></p>
          <p>
            <strong>A — inline, no extra file.</strong> Use the copyable command above: each
            <code>eventName#setting=value</code> entry is merged onto the bundled
            <code>profile</code> config, adding only the virtual-thread events (and setting the
            pinning <code>threshold</code> to <code>0ms</code> so every pin is captured).
          </p>
          <p>
            <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
            <code>virtual-threads.jfc</code> if you would rather keep the settings in a file:
          </p>
          <pre class="jfc-block">{{ jfcSnippet }}</pre>
          <ul>
            <li>
              At launch —
              <code>java -XX:StartFlightRecording=settings=profile,settings=virtual-threads.jfc,filename=app.jfr,dumponexit=true -jar app.jar</code>
            </li>
            <li>
              On a running JVM —
              <code>jcmd &lt;pid&gt; JFR.start name=vt settings=profile settings=virtual-threads.jfc filename=app.jfr</code>
            </li>
          </ul>

          <p>
            Either way, re-import the <code>.jfr</code> into Jeffrey afterwards. Raise the
            <code>threshold</code> (e.g. to <code>20ms</code>) to keep only the costly pins. Note that
            since JDK&nbsp;24 (JEP&nbsp;491) <code>synchronized</code> no longer pins virtual threads,
            so on modern runtimes pinning is rare and comes mostly from native frames — a quiet
            Pinning tab is often good news. To find <em>where</em> a pin happens, open the weighted
            <code>jdk.VirtualThreadPinned</code> flamegraph under Visualization → Flamegraphs.
          </p>
        </template>
      </DisabledEventsNotice>
    </div>

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
            <DonutWithLegend
              v-if="pinningTotalIncidents > 0"
              :data="pinningDistributionChart"
              :tooltip-formatter="(val: number) => FormattingService.formatNumber(val) + ' incidents'"
            />
            <EmptyState v-else icon="bi-check-circle" title="No pinning recorded" />
          </div>
          <div class="col-lg-7">
            <DataTable v-if="data!.topPinnedThreads.length > 0">
              <template #toolbar>
                <TableToolbar v-model="topPinnedThreadsView.query" search-placeholder="Filter threads...">
                  <span class="toolbar-info">Top Pinned Virtual Threads</span>
                  <template #filters>
                    <Badge
                      key-label="Total"
                      :value="topPinnedThreadsView.matchCount"
                      variant="secondary"
                      size="s"
                      borderless
                    />
                  </template>
                </TableToolbar>
              </template>
              <thead>
                <tr>
                  <th>Virtual Thread</th>
                  <th class="text-end">Incidents</th>
                  <th class="text-end">Total Pinned</th>
                  <th class="text-end">Max Pinned</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(t, i) in topPinnedThreadsView.visible" :key="i">
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
              <template #footer>
                <TableShowMore
                  :shown="topPinnedThreadsView.visible.length"
                  :match-count="topPinnedThreadsView.matchCount"
                  :total="topPinnedThreadsView.total"
                  :expanded="topPinnedThreadsView.expanded"
                  :page-size="topPinnedThreadsView.pageSize"
                  @toggle="topPinnedThreadsView.toggle"
                />
              </template>
            </DataTable>
            <EmptyState
              v-if="data!.topPinnedThreads.length === 0"
              icon="bi-check-circle"
              title="No pinning recorded"
            />
          </div>
        </div>

        <div class="row mt-4">
          <div class="col-12">
            <DataTable v-if="data!.pinningReasons.length > 0">
              <template #toolbar>
                <TableToolbar v-model="pinningReasonsView.query" search-placeholder="Filter reasons...">
                  <span class="toolbar-info">Pinning by Reason</span>
                  <template #filters>
                    <Badge
                      key-label="Total"
                      :value="pinningReasonsView.matchCount"
                      variant="secondary"
                      size="s"
                      borderless
                    />
                  </template>
                </TableToolbar>
              </template>
              <thead>
                <tr>
                  <th>Reason</th>
                  <th class="text-end">Incidents</th>
                  <th class="text-end">Total Pinned</th>
                  <th class="text-end">Max Pinned</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(r, i) in pinningReasonsView.visible" :key="i">
                  <td>{{ r.reason }}</td>
                  <td class="text-end">{{ FormattingService.formatNumber(r.count) }}</td>
                  <td class="text-end">
                    {{ FormattingService.formatDuration2Units(r.totalNanos) }}
                  </td>
                  <td class="text-end">
                    {{ FormattingService.formatDuration2Units(r.maxNanos) }}
                  </td>
                </tr>
              </tbody>
              <template #footer>
                <TableShowMore
                  :shown="pinningReasonsView.visible.length"
                  :match-count="pinningReasonsView.matchCount"
                  :total="pinningReasonsView.total"
                  :expanded="pinningReasonsView.expanded"
                  :page-size="pinningReasonsView.pageSize"
                  @toggle="pinningReasonsView.toggle"
                />
              </template>
            </DataTable>
            <EmptyState
              v-if="data!.pinningReasons.length === 0"
              icon="bi-check-circle"
              title="No pinning recorded"
              description="The pinnedReason field is reported by JDK 26+ recordings."
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
        <DataTable v-if="data!.submitFailures.length > 0">
          <template #toolbar>
            <TableToolbar v-model="submitFailuresView.query" search-placeholder="Filter submit failures...">
              <span class="toolbar-info">Submit Failures</span>
              <template #filters>
                <Badge
                  key-label="Total"
                  :value="submitFailuresView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Time</th>
              <th>Virtual Thread</th>
              <th>Exception</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(f, i) in submitFailuresView.visible" :key="i">
              <td>
                {{ FormattingService.formatDuration2Units(f.timeOffsetMillis * 1_000_000) }}
              </td>
              <td>{{ f.threadName }}</td>
              <td>{{ f.exceptionMessage }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="submitFailuresView.visible.length"
              :match-count="submitFailuresView.matchCount"
              :total="submitFailuresView.total"
              :expanded="submitFailuresView.expanded"
              :page-size="submitFailuresView.pageSize"
              @toggle="submitFailuresView.toggle"
            />
          </template>
        </DataTable>
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

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Virtual Threads"
          subtitle="Why pinning, submit failures, and lifecycle decide Loom scalability"
        >
          <AboutCallout variant="intro">
            <p>
              Virtual threads (Project Loom) run many lightweight threads on a small pool of OS
              <em>carrier</em> threads. The signal that matters most for throughput is
              <strong>pinning</strong>: when a virtual thread cannot unmount from its carrier (e.g.
              inside a native frame), it holds the carrier hostage and defeats the scalability
              benefit. This page surfaces that pinning over time, by duration, and by thread —
              alongside submit failures and the thread lifecycle.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-pin-angle" title="What the Views Show">
            <FeatureGrid>
              <FeatureCard icon="bi-pin-angle" variant="danger" title="Pinning">
                Occurrences and total pinned time over the recording, plus a duration distribution,
                the top pinned threads, and pinning by reason. The top Loom scalability footgun.
              </FeatureCard>
              <FeatureCard icon="bi-exclamation-octagon" variant="warning" title="Submit Failures">
                Virtual threads the scheduler rejected — carrier-pool rejection, executor shutdown,
                or a scheduling bug — with the time, thread, and exception message.
              </FeatureCard>
              <FeatureCard icon="bi-activity" variant="info" title="Lifecycle">
                Creation and completion counts and the derived live-count trend. A live count that
                climbs without bound signals a leak; spikes signal runaway spawning.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="A quiet Pinning tab is often good news" icon="bi-lightbulb-fill">
            Since JDK&nbsp;24 (JEP&nbsp;491) <code>synchronized</code> no longer pins virtual
            threads, so on modern runtimes pinning is rare and comes mostly from native frames. To
            find <em>where</em> a pin happens, open the weighted <code>jdk.VirtualThreadPinned</code>
            flamegraph under Visualization → Flamegraphs.
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.VirtualThreadPinned</code> — a virtual thread that couldn't unmount from
                its carrier; carries a stack trace and the pinned duration.
                <strong>Enabled by default</strong>, with a <code>threshold</code> of ~20&nbsp;ms so
                shorter pins are not recorded. Drives the Pinning tab.
              </li>
              <li>
                <code>jdk.VirtualThreadSubmitFailed</code> — the scheduler rejected a virtual thread.
                <strong>Enabled by default.</strong> Drives the Submit Failures tab.
              </li>
              <li>
                <code>jdk.VirtualThreadStart</code> / <code>jdk.VirtualThreadEnd</code> — one event
                per virtual-thread creation and termination, used to derive the live-count trend.
                <strong>Disabled by default</strong> and high-volume — enable only for leak or
                spawn-rate analysis. Drives the Lifecycle tab.
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
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import type { DonutChartData } from '@/components/DonutWithLegend.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import Badge from '@/components/Badge.vue';
import { useTableView } from '@/composables/useTableView';
import ProfileVirtualThreadsClient from '@/services/api/ProfileVirtualThreadsClient';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type VirtualThreadData from '@/services/api/model/VirtualThreadModels';

const route = useRoute();

const enableCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.VirtualThreadStart#enabled=true,jdk.VirtualThreadEnd#enabled=true,jdk.VirtualThreadPinned#enabled=true,jdk.VirtualThreadPinned#threshold=0ms,jdk.VirtualThreadSubmitFailed#enabled=true,filename=app.jfr,dumponexit=true -jar app.jar';

const jfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.VirtualThreadStart">
    <setting name="enabled">true</setting>
  </event>
  <event name="jdk.VirtualThreadEnd">
    <setting name="enabled">true</setting>
  </event>
  <event name="jdk.VirtualThreadPinned">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
    <setting name="threshold">0 ms</setting>
  </event>
  <event name="jdk.VirtualThreadSubmitFailed">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
  </event>
</configuration>`;

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<VirtualThreadData>();

const topPinnedThreadsView = useTableView(() => data.value?.topPinnedThreads ?? [], {
  searchableText: t => t.threadName
});
const pinningReasonsView = useTableView(() => data.value?.pinningReasons ?? [], {
  searchableText: r => r.reason
});
const submitFailuresView = useTableView(() => data.value?.submitFailures ?? [], {
  searchableText: f => `${f.threadName} ${f.exceptionMessage ?? ''}`
});

const tabs = [
  { id: 'pinning', label: 'Pinning', icon: 'pin-angle' },
  { id: 'submit-failures', label: 'Submit Failures', icon: 'exclamation-octagon' },
  { id: 'lifecycle', label: 'Lifecycle', icon: 'activity' },
  { id: 'about', label: 'How It Works', icon: 'book' }
];
const activeTab = ref(tabs[0].id);

const hasData = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return false;
  }
  return h.pinningCount > 0 || h.submitFailedCount > 0 || h.startedCount > 0;
});

// Sequential severity ramp for the ordinal duration buckets (short pinning → long pinning).
const PINNING_BUCKET_COLORS = ['#5cb85c', '#9acd4e', '#f0ad4e', '#ed7d31', '#d9534f', '#9b59b6'];

const pinningTotalIncidents = computed<number>(() =>
  (data.value?.pinningDistribution ?? []).reduce((sum, bucket) => sum + bucket.count, 0)
);

const pinningDistributionChart = computed<DonutChartData>(() => {
  const buckets = data.value?.pinningDistribution ?? [];
  const colorFor = (i: number) => PINNING_BUCKET_COLORS[i % PINNING_BUCKET_COLORS.length];
  return {
    series: buckets.map((b) => b.count),
    labels: buckets.map((b) => b.label),
    colors: buckets.map((_, i) => colorFor(i)),
    totalLabel: 'Incidents',
    totalValue: FormattingService.formatNumber(pinningTotalIncidents.value),
    legendItems: buckets.map((b, i) => ({
      color: colorFor(i),
      label: b.label,
      value: FormattingService.formatNumber(b.count)
    }))
  };
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

.hint {
  font-size: 0.85rem;
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  gap: 0.4rem;
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
