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

      <!-- Swap -->
      <div v-show="activeTab === 'swap'">
        <ChartDescription
          shows="OS swap space — total swap and how much is in use, over the recording."
          use-case="Rising used-swap on a JVM host is a red flag: paging the heap to disk causes huge GC pauses and latency. Ideally used swap stays flat near zero."
        />
        <EmptyState
          v-if="!hasSwapData"
          icon="bi-hdd"
          title="No swap data recorded"
          description="This recording has no jdk.SwapSpace events (swap tracking is platform-dependent)."
        />
        <div v-else class="chart-container">
          <TimeSeriesChart
            :primaryData="swapUsedSeries"
            primaryTitle="Used"
            :secondaryData="swapTotalSeries"
            secondaryTitle="Total"
            :primaryAxisType="AxisFormatType.BYTES"
            :secondaryAxisType="AxisFormatType.BYTES"
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
                <Badge
                  key-label="Total"
                  :value="processesView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
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

        <h6 class="subsection-title">Launched during recording</h6>
        <EmptyState
          v-if="launchedProcesses.length === 0"
          icon="bi-terminal"
          title="No subprocesses launched"
          description="The JVM started no child processes during the recording (no jdk.ProcessStart events)."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar
              v-model="launchedProcessesView.query"
              search-placeholder="Filter subprocesses..."
            >
              <span class="toolbar-info">Launched subprocesses</span>
              <template #filters>
                <Badge
                  key-label="Total"
                  :value="launchedProcessesView.matchCount"
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
              <th class="pid-column">PID</th>
              <th>Command</th>
              <th>Directory</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(proc, index) in launchedProcessesView.visible" :key="index">
              <td>
                {{ FormattingService.formatDuration2Units(proc.timeOffsetMillis * 1_000_000) }}
              </td>
              <td class="text-muted">{{ proc.pid }}</td>
              <td :title="proc.command ?? ''">
                <code class="path-name">{{ proc.command ?? '—' }}</code>
              </td>
              <td class="text-muted" :title="proc.directory ?? ''">{{ proc.directory ?? '—' }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="launchedProcessesView.visible.length"
              :match-count="launchedProcessesView.matchCount"
              :total="launchedProcessesView.total"
              :expanded="launchedProcessesView.expanded"
              :page-size="launchedProcessesView.pageSize"
              @toggle="launchedProcessesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Modules -->
      <div v-show="activeTab === 'modules'">
        <ChartDescription
          shows="The module graph from jdk.ModuleRequire (dependencies) and jdk.ModuleExport (package exports), captured at JVM startup."
          use-case="Confirm which modules a JItPL app reads and what's exported (and to whom) — useful when chasing IllegalAccess / module-resolution issues or auditing the runtime module set."
        />
        <h6 class="subsection-title">Requires</h6>
        <EmptyState
          v-if="moduleRequires.length === 0"
          icon="bi-box"
          title="No module dependencies recorded"
          description="This recording has no jdk.ModuleRequire events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar
              v-model="moduleRequiresView.query"
              search-placeholder="Filter dependencies..."
            >
              <span class="toolbar-info">Module dependencies</span>
              <template #filters>
                <Badge
                  key-label="Edges"
                  :value="moduleRequiresView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Source Module</th>
              <th>Requires</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(edge, index) in moduleRequiresView.visible" :key="index">
              <td>
                <code>{{ edge.source ?? 'unnamed' }}</code>
              </td>
              <td>
                <code>{{ edge.required ?? 'unnamed' }}</code>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="moduleRequiresView.visible.length"
              :match-count="moduleRequiresView.matchCount"
              :total="moduleRequiresView.total"
              :expanded="moduleRequiresView.expanded"
              :page-size="moduleRequiresView.pageSize"
              @toggle="moduleRequiresView.toggle"
            />
          </template>
        </DataTable>

        <h6 class="subsection-title">Exports</h6>
        <EmptyState
          v-if="moduleExports.length === 0"
          icon="bi-box-arrow-up-right"
          title="No package exports recorded"
          description="This recording has no jdk.ModuleExport events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="moduleExportsView.query" search-placeholder="Filter exports...">
              <span class="toolbar-info">Package exports</span>
              <template #filters>
                <Badge
                  key-label="Exports"
                  :value="moduleExportsView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Exported Package</th>
              <th>Target</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(exp, index) in moduleExportsView.visible" :key="index">
              <td>
                <code>{{ exp.packageName ?? '—' }}</code>
              </td>
              <td>
                <Badge
                  v-if="!exp.targetModule"
                  value="unqualified"
                  variant="info"
                  size="xs"
                  borderless
                />
                <code v-else>{{ exp.targetModule }}</code>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="moduleExportsView.visible.length"
              :match-count="moduleExportsView.matchCount"
              :total="moduleExportsView.total"
              :expanded="moduleExportsView.expanded"
              :page-size="moduleExportsView.pageSize"
              @toggle="moduleExportsView.toggle"
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
              from total <em>machine</em> usage, so you can tell whether a slowdown is your code or
              a noisy neighbour, and surfaces the host-level signals — network, context switches,
              other processes — that the in-JVM views can't see.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-cpu" title="What the Signals Mean">
            <FeatureGrid>
              <FeatureCard icon="bi-cpu-fill" variant="primary" title="JVM vs Machine CPU">
                JVM user+system is what your process consumes; machine total is the whole box. A
                large gap (machine high, JVM low) is the noisy-neighbour signature — something else
                on the host is competing for CPU.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-left-right" variant="warning" title="Context Switches">
                How often the OS swaps threads on/off cores. A high rate means oversubscription or
                heavy blocking/wakeups — threads spend time scheduling instead of working.
              </FeatureCard>
              <FeatureCard icon="bi-ethernet" variant="info" title="Network Utilization">
                Per-interface read/write rates (bits per second). Plateaus that coincide with
                latency point at a saturated link rather than CPU.
              </FeatureCard>
              <FeatureCard icon="bi-diagram-2" variant="neutral" title="Host Processes">
                Other processes the JVM saw on the host — useful for attributing the "other-process"
                CPU share to a concrete culprit (a sidecar, a cron job).
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="Is it me or the box?" icon="bi-lightbulb-fill">
            High machine CPU with low JVM CPU = the host is the bottleneck (or a container CPU limit
            — see Container Configuration). High JVM CPU = profile your own code (flame graphs,
            allocation, GC).
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.CPULoad</code> — periodic JVM user/system and machine-total CPU. Enabled
                by default.
              </li>
              <li><code>jdk.ThreadContextSwitchRate</code> — context switches per second.</li>
              <li><code>jdk.NetworkUtilization</code> — per-interface read/write rates.</li>
              <li>
                <code>jdk.SystemProcess</code> — a periodic snapshot of host processes (pid +
                command line), de-duplicated to the latest per pid.
              </li>
              <li>
                <code>jdk.ProcessStart</code> — a subprocess the JVM launched (pid, command,
                directory), shown under Host Processes as "Launched during recording".
              </li>
              <li>
                <code>jdk.SwapSpace</code> — periodic OS swap total/free, behind the Swap tab.
              </li>
              <li>
                <code>jdk.ModuleRequire</code> / <code>jdk.ModuleExport</code> — the startup module
                graph (dependencies and package exports), behind the Modules tab.
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
import type {
  LaunchedProcessInfo,
  ModuleEdge,
  ModuleExport,
  SystemOverview,
  SystemProcessInfo
} from '@/services/api/model/SystemModels';
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
const launchedProcesses = ref<LaunchedProcessInfo[]>([]);
const swapTimeline = ref<TimeseriesData>();
const moduleRequires = ref<ModuleEdge[]>([]);
const moduleExports = ref<ModuleExport[]>([]);

const processesView = useTableView<SystemProcessInfo>(processes, {
  searchableText: r => `${r.pid} ${r.commandLine}`
});
const launchedProcessesView = useTableView<LaunchedProcessInfo>(launchedProcesses, {
  searchableText: r => `${r.pid} ${r.command ?? ''} ${r.directory ?? ''}`
});
const moduleRequiresView = useTableView<ModuleEdge>(moduleRequires, {
  searchableText: r => `${r.source ?? ''} ${r.required ?? ''}`
});
const moduleExportsView = useTableView<ModuleExport>(moduleExports, {
  searchableText: r => `${r.packageName ?? ''} ${r.targetModule ?? ''}`
});

const activeTab = ref('cpu');

let client: ProfileSystemClient;

const machineCpuSeries = computed<number[][]>(() => cpuTimeline.value?.series?.[0]?.data ?? []);
const jvmUserSeries = computed<number[][]>(() => cpuTimeline.value?.series?.[1]?.data ?? []);
const jvmSystemSeries = computed<number[][]>(() => cpuTimeline.value?.series?.[2]?.data ?? []);
const networkReadSeries = computed<number[][]>(
  () => networkTimeline.value?.series?.[0]?.data ?? []
);
const networkWriteSeries = computed<number[][]>(
  () => networkTimeline.value?.series?.[1]?.data ?? []
);
const contextSwitchSeries = computed<number[][]>(
  () => contextSwitchTimeline.value?.series?.[0]?.data ?? []
);
const swapTotalSeries = computed<number[][]>(() => swapTimeline.value?.series?.[0]?.data ?? []);
const swapUsedSeries = computed<number[][]>(() => swapTimeline.value?.series?.[1]?.data ?? []);
const hasSwapData = computed<boolean>(() => swapTotalSeries.value.some(point => point[1] > 0));

const tabs = computed<TabBarItem[]>(() => [
  { id: 'cpu', label: 'CPU', icon: 'cpu' },
  {
    id: 'network',
    label: 'Network',
    icon: 'wifi',
    badge: networkInterfaces.value.length || undefined
  },
  { id: 'context-switches', label: 'Context Switches', icon: 'arrow-left-right' },
  { id: 'swap', label: 'Swap', icon: 'hdd' },
  {
    id: 'processes',
    label: 'Host Processes',
    icon: 'pc-display',
    badge: processes.value.length || undefined
  },
  {
    id: 'modules',
    label: 'Modules',
    icon: 'boxes',
    badge: moduleRequires.value.length || undefined
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
        {
          label: 'Network Interfaces',
          value: FormattingService.formatNumber(o.networkInterfaceCount)
        }
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

    const [
      overviewResult,
      cpuResult,
      interfacesResult,
      contextSwitchResult,
      processesResult,
      swapResult,
      launchedResult,
      moduleRequiresResult,
      moduleExportsResult
    ] = await Promise.all([
      client.getOverview(),
      client.getCpuTimeline(),
      client.getNetworkInterfaces(),
      client.getContextSwitchTimeline(),
      client.getProcesses(),
      client.getSwapTimeline(),
      client.getLaunchedProcesses(),
      client.getModuleRequires(),
      client.getModuleExports()
    ]);

    overview.value = overviewResult;
    cpuTimeline.value = cpuResult;
    networkInterfaces.value = interfacesResult;
    contextSwitchTimeline.value = contextSwitchResult;
    processes.value = processesResult;
    swapTimeline.value = swapResult;
    launchedProcesses.value = launchedResult;
    moduleRequires.value = moduleRequiresResult;
    moduleExports.value = moduleExportsResult;

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

.subsection-title {
  margin: 1.5rem 0 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
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
