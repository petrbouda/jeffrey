<template>
  <div class="system-container">
    <LoadingState v-if="loading" message="Loading system data..." />
    <ErrorState v-else-if="error" message="Failed to load system data" />

    <div v-else>
      <PageHeader
        title="System & Host"
        description="JVM vs. machine CPU, network utilization, context switches, and competing host processes — decide whether the problem is your JVM or the box"
        icon="bi-cpu"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- CPU -->
      <div v-show="activeTab === 'cpu'">
        <ChartDescription
          shows="Machine-total CPU vs the JVM's own user/system CPU over time."
          use-case="The gap is other processes on the host — the noisy-neighbour signal; high JVM-system time points at syscall-heavy work."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="machineCpuSeries"
            primaryTitle="Machine Total"
            :secondaryData="jvmUserSeries"
            secondaryTitle="JVM User"
            :tertiaryData="jvmSystemSeries"
            tertiaryTitle="JVM System"
            :primaryAxisType="AxisFormatType.PERCENT_IN_HUNDREDTHS"
            :secondaryAxisType="AxisFormatType.PERCENT_IN_HUNDREDTHS"
            :tertiaryAxisType="AxisFormatType.PERCENT_IN_HUNDREDTHS"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Network -->
      <div v-show="activeTab === 'network'">
        <ChartDescription
          shows="Read/write throughput per network interface."
          use-case="Correlate latency spikes with NIC saturation."
        >
          <template #actions>
            <select
              v-if="networkInterfaces.length > 0"
              v-model="selectedInterface"
              class="form-select form-select-sm interface-select"
            >
              <option v-for="iface in networkInterfaces" :key="iface" :value="iface">
                {{ iface }}
              </option>
            </select>
          </template>
        </ChartDescription>
        <EmptyState
          v-if="networkInterfaces.length === 0"
          icon="bi-wifi-off"
          title="No network utilization recorded"
          description="This recording has no jdk.NetworkUtilization events."
        />
        <div v-else class="chart-container">
          <TimeSeriesChart
            :key="selectedInterface"
            :primaryData="networkReadSeries"
            primaryTitle="Read /s"
            :secondaryData="networkWriteSeries"
            secondaryTitle="Write /s"
            :primaryAxisType="AxisFormatType.BYTES"
            :secondaryAxisType="AxisFormatType.BYTES"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Context Switches -->
      <div v-show="activeTab === 'context-switches'">
        <ChartDescription
          shows="OS thread context switches per second."
          use-case="A persistently high rate signals thread oversubscription or lock churn — threads fighting the scheduler instead of working."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="contextSwitchSeries"
            primaryTitle="Context Switches / sec"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Host Processes -->
      <div v-show="activeTab === 'processes'">
        <EmptyState
          v-if="processes.length === 0"
          icon="bi-pc-display"
          title="No host processes recorded"
          description="This recording has no jdk.SystemProcess events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="processesView.query" search-placeholder="Filter processes...">
              <span class="toolbar-info">Host processes</span>
              <template #filters>
                <Badge key-label="Total" :value="processesView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th class="pid-column">PID</th>
              <th>Command</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="process in processesView.visible" :key="process.pid">
              <td class="text-muted">{{ process.pid }}</td>
              <td :title="process.commandLine">
                <div class="path-display">
                  <code class="path-name">{{ processName(process.commandLine) }}</code>
                  <span v-if="process.commandLine" class="path-dir">{{ process.commandLine }}</span>
                </div>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="processesView.visible.length"
              :match-count="processesView.matchCount"
              :total="processesView.total"
              :expanded="processesView.expanded"
              :page-size="processesView.pageSize"
              @toggle="processesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- How It Works Tab -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding System & Host"
          subtitle="Telling apart your JVM's CPU use from the machine it shares"
        >
          <AboutCallout variant="intro">
            <p>
              Your JVM rarely has the machine to itself. This page separates <em>your</em> CPU usage
              from total <em>machine</em> usage, so you can tell whether a slowdown is your code or a
              noisy neighbour, and surfaces the host-level signals — network, context switches, other
              processes — that the in-JVM views can't see.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-cpu" title="What the Signals Mean">
            <FeatureGrid>
              <FeatureCard icon="bi-cpu-fill" variant="primary" title="JVM vs Machine CPU">
                JVM user+system is what your process consumes; machine total is the whole box. A large
                gap (machine high, JVM low) is the noisy-neighbour signature — something else on the
                host is competing for CPU.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-left-right" variant="warning" title="Context Switches">
                How often the OS swaps threads on/off cores. A high rate means oversubscription or heavy
                blocking/wakeups — threads spend time scheduling instead of working.
              </FeatureCard>
              <FeatureCard icon="bi-ethernet" variant="info" title="Network Utilization">
                Per-interface read/write rates (bits per second). Plateaus that coincide with latency
                point at a saturated link rather than CPU.
              </FeatureCard>
              <FeatureCard icon="bi-diagram-2" variant="neutral" title="Host Processes">
                Other processes the JVM saw on the host — useful for attributing the "other-process" CPU
                share to a concrete culprit (a sidecar, a cron job).
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="Is it me or the box?" icon="bi-lightbulb-fill">
            High machine CPU with low JVM CPU = the host is the bottleneck (or a container CPU limit —
            see Container Configuration). High JVM CPU = profile your own code (flame graphs, allocation,
            GC).
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.CPULoad</code> — periodic JVM user/system and machine-total CPU. Enabled by
                default.
              </li>
              <li>
                <code>jdk.ThreadContextSwitchRate</code> — context switches per second.
              </li>
              <li>
                <code>jdk.NetworkUtilization</code> — per-interface read/write rates.
              </li>
              <li>
                <code>jdk.SystemProcess</code> — a periodic snapshot of host processes (pid + command
                line), de-duplicated to the latest per pid.
              </li>
            </ul>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
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
import ProfileSystemClient from '@/services/api/ProfileSystemClient';
import type { SystemOverview, SystemProcessInfo } from '@/services/api/model/SystemModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const overview = ref<SystemOverview>();
const cpuTimeline = ref<TimeseriesData>();
const networkInterfaces = ref<string[]>([]);
const selectedInterface = ref<string>('');
const networkTimeline = ref<TimeseriesData>();
const contextSwitchTimeline = ref<TimeseriesData>();
const processes = ref<SystemProcessInfo[]>([]);

const processesView = useTableView<SystemProcessInfo>(processes, {
  searchableText: r => `${r.pid} ${r.commandLine}`
});

const activeTab = ref('cpu');

let client: ProfileSystemClient;

const machineCpuSeries = computed<number[][]>(() => cpuTimeline.value?.series?.[0]?.data ?? []);
const jvmUserSeries = computed<number[][]>(() => cpuTimeline.value?.series?.[1]?.data ?? []);
const jvmSystemSeries = computed<number[][]>(() => cpuTimeline.value?.series?.[2]?.data ?? []);
const networkReadSeries = computed<number[][]>(() => networkTimeline.value?.series?.[0]?.data ?? []);
const networkWriteSeries = computed<number[][]>(() => networkTimeline.value?.series?.[1]?.data ?? []);
const contextSwitchSeries = computed<number[][]>(() => contextSwitchTimeline.value?.series?.[0]?.data ?? []);

const tabs = computed<TabBarItem[]>(() => [
  { id: 'cpu', label: 'CPU', icon: 'cpu' },
  {
    id: 'network',
    label: 'Network',
    icon: 'wifi',
    badge: networkInterfaces.value.length || undefined
  },
  { id: 'context-switches', label: 'Context Switches', icon: 'arrow-left-right' },
  {
    id: 'processes',
    label: 'Host Processes',
    icon: 'pc-display',
    badge: processes.value.length || undefined
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const formatBasisPoints = (bp: number): string => (bp / 100).toFixed(1) + '%';

// Executable name from a command line: basename of an absolute path, otherwise the first token
// (keeps kernel-thread names like "kworker/5:0" intact).
const processName = (commandLine: string): string => {
  const first = (commandLine ?? '').trim().split(/\s+/)[0] ?? '';
  if (first.startsWith('/')) {
    const slash = first.lastIndexOf('/');
    return slash >= 0 ? first.substring(slash + 1) : first;
  }
  return first || '—';
};

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  return [
    {
      icon: 'cpu',
      title: 'Machine CPU',
      value: formatBasisPoints(o.maxMachineCpuBp),
      variant: 'highlight' as const,
      breakdown: [{ label: 'Average', value: formatBasisPoints(o.avgMachineCpuBp) }]
    },
    {
      icon: 'cup-hot',
      title: 'JVM CPU (avg)',
      value: formatBasisPoints(o.avgJvmCpuBp),
      variant: 'info' as const,
      breakdown: [{ label: 'Other Processes', value: formatBasisPoints(o.avgOtherCpuBp) }]
    },
    {
      icon: 'arrow-left-right',
      title: 'Context Switches (max)',
      value: `${FormattingService.formatNumber(o.maxContextSwitchRateHz)}/s`,
      variant: 'warning' as const
    },
    {
      icon: 'pc-display',
      title: 'Host Processes',
      value: FormattingService.formatNumber(o.processCount),
      variant: 'success' as const,
      breakdown: [
        { label: 'Network Interfaces', value: FormattingService.formatNumber(o.networkInterfaceCount) }
      ]
    }
  ];
});

const loadNetworkTimeline = async (networkInterface: string) => {
  if (!networkInterface) {
    return;
  }
  try {
    networkTimeline.value = await client.getNetworkTimeline(networkInterface);
  } catch (e) {
    console.error('Failed to load network timeline:', e);
  }
};

watch(selectedInterface, networkInterface => {
  loadNetworkTimeline(networkInterface);
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    client = new ProfileSystemClient(profileId);

    const [overviewResult, cpuResult, interfacesResult, contextSwitchResult, processesResult] =
      await Promise.all([
        client.getOverview(),
        client.getCpuTimeline(),
        client.getNetworkInterfaces(),
        client.getContextSwitchTimeline(),
        client.getProcesses()
      ]);

    overview.value = overviewResult;
    cpuTimeline.value = cpuResult;
    networkInterfaces.value = interfacesResult;
    contextSwitchTimeline.value = contextSwitchResult;
    processes.value = processesResult;

    if (interfacesResult.length > 0) {
      selectedInterface.value = interfacesResult[0];
    }

    loading.value = false;
  } catch (e) {
    console.error('Failed to load system data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.system-container {
  width: 100%;
  color: var(--color-text);
}

.interface-select {
  min-width: 140px;
}

.chart-container {
  width: 100%;
}

.pid-column {
  width: 110px;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.path-display {
  display: flex;
  flex-direction: column;
  max-width: 720px;
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
