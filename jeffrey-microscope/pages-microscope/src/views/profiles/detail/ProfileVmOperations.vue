<template>
  <div class="latency-container">
    <LoadingState v-if="loading" message="Loading VM operations data..." />
    <ErrorState v-else-if="error" message="Failed to load VM operations data" />

    <div v-else>
      <PageHeader
        title="VM Operations"
        description="JVM-internal stop-the-world activity beyond GC — VM operations and safepoints, the pauses the GC log doesn't explain"
        icon="bi-stopwatch"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- VM Operations -->
      <div v-show="activeTab === 'operations'">
        <EmptyState
          v-if="vmOperations.length === 0"
          icon="bi-stopwatch"
          title="No VM operation events recorded"
          description="This recording has no jdk.ExecuteVMOperation events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="vmOperationsView.query" search-placeholder="Filter operations...">
              <span class="toolbar-info">VM operations</span>
              <template #filters>
                <Badge key-label="Total" :value="vmOperationsView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>VM Operation</th>
              <th class="text-end">Count</th>
              <th class="text-end">Total Time</th>
              <th class="text-end">Max Time</th>
              <th>Properties</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="operation in vmOperationsView.visible" :key="operation.operation">
              <td class="operation-name">{{ operation.operation }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(operation.count) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(operation.totalNanos) }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(operation.maxNanos) }}</td>
              <td>
                <div class="d-flex gap-1">
                  <Badge v-if="operation.safepoint" value="Safepoint" variant="danger" size="xs" />
                  <Badge v-if="operation.blocking" value="Blocking" variant="warning" size="xs" />
                </div>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="vmOperationsView.visible.length"
              :match-count="vmOperationsView.matchCount"
              :total="vmOperationsView.total"
              :expanded="vmOperationsView.expanded"
              :page-size="vmOperationsView.pageSize"
              @toggle="vmOperationsView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Safepoints -->
      <div v-show="activeTab === 'safepoints'">
        <ChartDescription
          shows="Total time per second spent in safepoint VM operations (stop-the-world pauses beyond GC)."
          use-case="Spikes that don't line up with GC are tooling, agents, or biased-lock revocation."
        />
        <div class="chart-container mb-4">
          <TimeSeriesChart
            :primaryData="pauseSeries"
            primaryTitle="Safepoint Pause Time"
            :primaryAxisType="AxisFormatType.DURATION_IN_NANOS"
            :visibleMinutes="60"
          />
        </div>

        <DisabledEventsNotice
          v-if="!overview?.hasTimeToSafepoint"
          title="Time-to-safepoint data not available"
          action-label="Re-record with the event enabled"
          command="settings=profile,+jdk.SafepointStateSynchronization#enabled=true"
        >
          Measuring how long the JVM waits for all threads to <em>reach</em> a safepoint (TTSP) needs
          <code>jdk.SafepointStateSynchronization</code> events, which are disabled in the default and
          profile JFR configurations. The pause timeline above (the time spent <em>at</em> safepoints)
          is always available.
        </DisabledEventsNotice>
        <template v-else>
          <ChartDescription
            shows="Wall-clock per second waiting for every Java thread to reach a safepoint before a stop-the-world operation can begin."
            use-case="High time-to-safepoint points at threads slow to poll — long counted loops or JNI — not the operation itself."
          />
          <div class="chart-container">
            <TimeSeriesChart
              :primaryData="safepointSeries"
              primaryTitle="Time to Safepoint"
              :primaryAxisType="AxisFormatType.DURATION_IN_NANOS"
              :visibleMinutes="60"
            />
          </div>
        </template>

        <!--
          Who the JVM waited for. Every other safepoint reading on this page is recorded once per
          safepoint on a VM thread, so none of them knows whose slowness it measured — which is why
          the time-to-safepoint chart above can say the wait was long and then stop. jdk.SafepointLatency
          is recorded on each application thread instead, and is the only thing here that can name a
          culprit.
        -->
        <DisabledEventsNotice
          v-if="!overview?.hasSafepointOffenders"
          title="The threads that were waited for are not identified"
          action-label="Re-record with the event enabled"
          command="settings=profile,+jdk.SafepointLatency#enabled=true,+jdk.SafepointLatency#stackTrace=true"
        >
          Naming the thread that held a safepoint up needs <code>jdk.SafepointLatency</code>, which
          is disabled in <strong>both</strong> the default and profile JFR configurations. Note the
          second setting: <code>profile</code> turns its stack trace off even when the event is
          enabled, so without it you learn which thread was slow but never what it was running.
        </DisabledEventsNotice>
        <template v-else-if="offenders.length > 0">
          <ChartDescription
            shows="Threads ranked by the total time the JVM spent waiting for them to reach a safepoint."
            use-case="Names the thread the time-to-safepoint chart above can only measure — and its state says why: compiled Java means a loop with no poll point, native means a call that cannot be interrupted."
          />

          <DataTable>
            <template #toolbar>
              <TableToolbar v-model="offendersView.query" search-placeholder="Filter threads...">
                <span class="toolbar-info">threads holding up safepoints</span>
                <template #filters>
                  <!--
                    The measured count, not the row count: the table is capped at the worst threads,
                    and a reader comparing "20 rows" against a 400-thread application should be able
                    to see that the cap is a cap.
                  -->
                  <Badge
                    key-label="Measured"
                    :value="safepointLatency?.threadCount ?? 0"
                    variant="secondary"
                    size="s"
                    borderless
                  />
                </template>
              </TableToolbar>
            </template>
            <thead>
              <tr>
                <th>Thread</th>
                <th>State when asked to stop</th>
                <th class="text-end">Safepoints</th>
                <th class="text-end">Max</th>
                <th class="text-end">P99</th>
                <th class="text-end">Total</th>
                <th class="text-end">Share</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="offender in offendersView.visible" :key="offender.threadName">
                <td class="thread-name">{{ offender.threadName }}</td>
                <td>
                  <Badge
                    :value="offender.threadState"
                    :variant="
                      offender.threadState === '_thread_in_native' ? 'warning' : 'secondary'
                    "
                    size="xs"
                    :title="threadStateHint(offender.threadState)"
                  />
                </td>
                <td class="text-end">{{ FormattingService.formatNumber(offender.count) }}</td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(offender.maxNanos) }}
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(offender.p99Nanos) }}
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(offender.totalNanos) }}
                </td>
                <td class="text-end">{{ offenderShare(offender).toFixed(1) }}%</td>
              </tr>
            </tbody>
            <template #footer>
              <TableShowMore
                :shown="offendersView.visible.length"
                :match-count="offendersView.matchCount"
                :total="offendersView.total"
                :expanded="offendersView.expanded"
                :page-size="offendersView.pageSize"
                @toggle="offendersView.toggle"
              />
            </template>
          </DataTable>
        </template>
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding VM Operations & Safepoints"
          subtitle="How the JVM stops the world to do internal work — and what those operations are"
        >
          <AboutCallout variant="intro">
            <p>
              Many JVM tasks need every Java thread paused at a known-good state. The JVM achieves this
              with a <strong>safepoint</strong>: it asks all threads to stop at the next poll point,
              waits for the slowest one (that wait is <em>time-to-safepoint</em>), runs the
              <strong>VM operation</strong>, then releases everyone. GC is the most common reason, but
              far from the only one — and none of it shows up in a CPU profile.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-stopwatch" title="The Safepoint Protocol">
            <FeatureGrid>
              <FeatureCard icon="bi-sign-stop" variant="danger" title="What a safepoint is">
                A point where a thread's state (stack, registers, references) is consistent for the JVM
                to inspect or modify. Compiled code has <em>poll points</em> (loop back-edges, method
                returns) where a thread checks the safepoint flag and parks.
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-bottom" variant="warning" title="Time to Safepoint (TTSP)">
                The operation can't start until the <em>last</em> thread reaches a poll point. One
                thread in a long counted loop, a JNI critical section, or page-faulting can stall every
                other thread — TTSP is often more surprising than the operation's own duration.
              </FeatureCard>
              <FeatureCard icon="bi-gear-wide-connected" variant="info" title="The VM operation">
                The actual stop-the-world work, run by the VM thread while everyone is parked.
                "Safepoint pause time" measures how long these ran; TTSP measures the wait to begin.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-counterclockwise" variant="success" title="Why it's usually fine">
                Safepoints are cheap and frequent. They become a problem only when TTSP is high (a
                slow-to-poll thread) or an operation is unexpectedly long (a heap inspection, a class
                redefinition storm).
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-list-task" title="Types of VM Operation">
            <FeatureGrid>
              <FeatureCard icon="bi-recycle" variant="primary" title="GC collections">
                <code>G1CollectForAllocation</code>, <code>CGC_Operation</code>,
                <code>ParallelGCFailedAllocation</code>… the stop-the-world phases of garbage
                collection. Usually the bulk of safepoint time — see the GC page for detail.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-down-up" variant="warning" title="Deoptimization">
                <code>Deoptimize</code> — the JIT discards optimized code whose speculation was
                invalidated. Bursts point at deopt churn; see the Deoptimizations page.
              </FeatureCard>
              <FeatureCard icon="bi-unlock" variant="info" title="Bias revocation">
                <code>RevokeBias</code> / <code>BulkRevokeBias</code> — undoing biased locking when a
                second thread contends an object. Common in older JVMs; removed in JDK 15+.
              </FeatureCard>
              <FeatureCard icon="bi-card-list" variant="purple" title="Thread dumps & stacks">
                <code>ThreadDump</code>, <code>GetThreadListStackTraces</code>,
                <code>FindDeadlocks</code> — triggered by <code>jstack</code>, profilers, APM agents
                and monitoring. Frequent dumps add avoidable pauses.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-repeat" variant="danger" title="Class redefinition">
                <code>RedefineClasses</code> / <code>RetransformClasses</code> — instrumentation agents
                rewriting bytecode at runtime. Heavy use means recurring pauses plus metaspace churn.
              </FeatureCard>
              <FeatureCard icon="bi-camera" variant="neutral" title="Heap inspection">
                <code>HeapDumpOperation</code>, <code>HeapInspection</code>,
                <code>GC.heap_dump</code> — walking the whole heap. Rare but very long; usually a
                manual diagnostic action.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.ExecuteVMOperation</code> — every VM operation with its name and
                <code>safepoint</code> / <code>blocking</code> flags and duration. Enabled by default;
                drives the operations table and the pause timeline.
              </li>
              <li>
                <code>jdk.SafepointStateSynchronization</code> (with the <code>SafepointBegin</code> /
                <code>SafepointEnd</code> family) — time-to-safepoint and the safepoint phases.
                <strong>Off by default</strong>, so the TTSP chart is usually gated until enabled.
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
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
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
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileVmOperationsClient from '@/services/api/ProfileVmOperationsClient';
import { useTableView } from '@/composables/useTableView';
import type {
  SafepointLatencyData,
  SafepointOffender,
  VmOperationStat,
  VmOverview
} from '@/services/api/model/VmOperationModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const overview = ref<VmOverview>();
const vmOperations = ref<VmOperationStat[]>([]);
const pausesTimeline = ref<TimeseriesData>();
const safepointTimeline = ref<TimeseriesData>();
const safepointLatency = ref<SafepointLatencyData>();

const activeTab = ref('operations');

const vmOperationsView = useTableView<VmOperationStat>(vmOperations, {
  searchableText: r => r.operation
});

const offenders = computed<SafepointOffender[]>(() => safepointLatency.value?.offenders ?? []);

const offendersView = useTableView<SafepointOffender>(offenders, {
  searchableText: r => `${r.threadName} ${r.threadState}`
});

/**
 * The share of the ranking weight one thread accounts for.
 *
 * Taken against the summed latency rather than against elapsed time on purpose: threads reach a
 * safepoint concurrently, so the total is a sum of overlapping waits and means nothing as a
 * duration. As a denominator for "which thread dominates this list" it is exactly right.
 */
function offenderShare(offender: SafepointOffender): number {
  const total = safepointLatency.value?.totalNanos ?? 0;
  return total <= 0 ? 0 : (offender.totalNanos / total) * 100;
}

/**
 * The thread state in the terms the reader has to act in. `_thread_in_Java` and
 * `_thread_in_native` are the two that mean something different: the first is a loop the JIT
 * stripped the safepoint poll out of, the second a call the JVM cannot interrupt at all.
 */
function threadStateHint(state: string): string {
  if (state === '_thread_in_Java') {
    return 'Running compiled Java — usually a long counted loop with no poll point';
  }
  if (state === '_thread_in_native') {
    return 'Inside a native call, which the JVM cannot interrupt';
  }
  return state;
}

const pauseSeries = computed<number[][]>(() => pausesTimeline.value?.series?.[0]?.data ?? []);
const safepointSeries = computed<number[][]>(() => safepointTimeline.value?.series?.[0]?.data ?? []);

const tabs = computed<TabBarItem[]>(() => [
  {
    id: 'operations',
    label: 'VM Operations',
    icon: 'list-task',
    badge: vmOperations.value.length || undefined
  },
  { id: 'safepoints', label: 'Safepoints', icon: 'sign-stop' },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  return [
    {
      icon: 'stopwatch',
      title: 'Safepoint Pause Time',
      value: FormattingService.formatDuration2Units(o.totalSafepointPauseNanos),
      variant: 'highlight' as const,
      breakdown: [{ label: 'VM Operations', value: FormattingService.formatNumber(o.vmOperationCount) }]
    },
    {
      icon: 'exclamation-circle',
      title: 'Longest Pause',
      value: FormattingService.formatDuration2Units(o.longestPauseNanos),
      variant: 'danger' as const,
      breakdown: o.longestPauseOperation ? [{ label: 'Operation', value: o.longestPauseOperation }] : []
    },
    {
      icon: 'list-task',
      title: 'Operation Types',
      value: FormattingService.formatNumber(vmOperations.value.length),
      variant: 'info' as const
    },
    {
      icon: 'sign-stop',
      title: 'VM Operations',
      value: FormattingService.formatNumber(o.vmOperationCount),
      variant: 'success' as const
    }
  ];
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileVmOperationsClient(profileId);

    const [overviewResult, vmOpsResult, pausesResult, safepointResult, offendersResult] =
      await Promise.all([
        client.getOverview(),
        client.getVmOperations(),
        client.getPausesTimeline(),
        client.getSafepointTimeline(),
        client.getSafepointOffenders()
      ]);

    overview.value = overviewResult;
    vmOperations.value = vmOpsResult;
    pausesTimeline.value = pausesResult;
    safepointTimeline.value = safepointResult;
    safepointLatency.value = offendersResult;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load VM operations data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.latency-container {
  width: 100%;
  color: var(--color-text);
}

.chart-container {
  width: 100%;
}

.operation-name {
  font-weight: 500;
  max-width: 460px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}
</style>
