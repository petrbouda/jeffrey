<template>
  <div class="jit-compilation-container">
    <LoadingState v-if="loading" message="Loading JIT compilation data..." />
    <ErrorState v-else-if="error" message="Failed to load JIT compilation data" />

    <div v-else>
      <!-- Header Section with Stats Overview -->
      <PageHeader
        title="JIT Compilation"
        description="Real-time insights into Java Just-In-Time compilation performance"
        icon="bi-lightning-charge-fill"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData">
          <template #title-action-0>
            <i
              class="bi bi-info-circle text-muted compilation-info-icon"
              @click="showCompilationsModal"
              title="Click for detailed explanation of Standard vs OSR Compilation"
              style="cursor: pointer"
            ></i>
          </template>
          <template #title-action-1>
            <i
              class="bi bi-info-circle text-muted compilation-info-icon"
              @click="showTooltipModal"
              title="Click for detailed explanation of Bailouts vs Invalidations"
              style="cursor: pointer"
            ></i>
          </template>
          <template #title-action-2>
            <i
              class="bi bi-info-circle text-muted compilation-info-icon"
              @click="showNMethodsModal"
              title="Click for detailed explanation of nMethods"
              style="cursor: pointer"
            ></i>
          </template>
        </StatsTable>
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Activity -->
      <div v-show="activeTab === 'activity'">
        <ChartDescription
          shows="Compilation work sampled by the CPU profiler across the recording."
          use-case="An early burst is normal warmup; sustained activity afterward can signal compilation churn."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="timeseriesData?.data"
            :primaryTitle="timeseriesData?.name"
            :visibleMinutes="60"
          />
        </div>

        <template v-if="hasQueueData">
          <ChartDescription
            class="mt-4"
            shows="C1 and C2 compiler queue depth over time."
            use-case="A sustained C2 backlog during warmup means hot methods are waiting to be optimized."
          />
          <div class="chart-container">
            <TimeSeriesChart
              :primaryData="c1QueueSeries"
              primaryTitle="C1 Queue"
              :secondaryData="c2QueueSeries"
              secondaryTitle="C2 Queue"
              :visibleMinutes="60"
            />
          </div>
        </template>
      </div>

      <!-- Long Compilations -->
      <div v-show="activeTab === 'compilations'">
        <EmptyState
          v-if="compilationsData.length === 0"
          icon="bi-lightning"
          title="No long compilations recorded"
          description="No compilation exceeded the long-compilation threshold in this recording."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="compilationsView.query" search-placeholder="Filter methods...">
              <span class="toolbar-info">Long compilations</span>
              <template #filters>
                <Badge
                  key-label="Threshold"
                  :value="`${statisticsData?.compileMethodThreshold}ms`"
                  variant="primary"
                  size="s"
                  borderless
                />
                <Badge
                  key-label="Total"
                  :value="compilationsView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th class="id-col">ID</th>
              <th>Method</th>
              <th class="text-center">Level</th>
              <th class="text-end">Time</th>
              <th class="text-end">Code Size</th>
              <th class="text-end">Status</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="compilation in compilationsView.visible"
              :key="compilation.compileId"
              :class="{ 'table-danger': !compilation.succeded }"
              :title="compilation.method"
            >
              <td class="text-muted">{{ compilation.compileId }}</td>
              <td>
                <div class="method-cell">
                  <div class="d-flex align-items-center gap-2 mb-1">
                    <span class="method-name">{{ getClassMethodName(compilation.method) }}</span>
                    <Badge :value="compilation.compiler" variant="primary" size="xs" borderless />
                    <Badge v-if="compilation.isOsr" value="OSR" variant="info" size="xs" borderless />
                  </div>
                  <span class="method-path text-muted small">{{
                    getPackage(compilation.method)
                  }}</span>
                </div>
              </td>
              <td class="text-center">{{ compilation.compileLevel }}</td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(compilation.duration) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(compilation.codeSize) }}</td>
              <td class="text-end">
                <Badge v-if="compilation.succeded" value="Success" variant="success" size="s" borderless />
                <Badge v-else value="Failed" variant="danger" size="s" borderless />
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="compilationsView.visible.length"
              :match-count="compilationsView.matchCount"
              :total="compilationsView.total"
              :expanded="compilationsView.expanded"
              :page-size="compilationsView.pageSize"
              @toggle="compilationsView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Code Cache -->
      <div v-show="activeTab === 'code-cache'">
        <EmptyState
          v-if="!codeCacheData || codeCacheData.segments.length === 0"
          icon="bi-hdd-stack"
          title="No code cache statistics recorded"
          description="This recording has no jdk.CodeCacheStatistics events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="codeCacheView.query" search-placeholder="Filter heaps...">
              <span class="toolbar-info">Code cache heaps</span>
              <template #filters>
                <Badge
                  key-label="Code Heaps"
                  :value="codeCacheView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
                <Badge
                  v-if="codeCacheData.codeCacheFullCount > 0"
                  key-label="Code Cache Full"
                  :value="codeCacheData.codeCacheFullCount"
                  variant="danger"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Code Heap</th>
              <th class="text-end">Used</th>
              <th class="text-end">Reserved</th>
              <th class="text-end">Methods</th>
              <th class="text-end">Adaptors</th>
              <th class="text-end">Full Count</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="segment in codeCacheView.visible" :key="segment.codeBlobType">
              <td class="method-name">{{ segment.codeBlobType }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(segment.usedBytes) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(segment.reservedBytes) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(segment.methodCount) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(segment.adaptorCount) }}</td>
              <td class="text-end">
                <Badge v-if="segment.fullCount > 0" :value="segment.fullCount" variant="danger" size="xs" borderless />
                <span v-else class="text-muted">0</span>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="codeCacheView.visible.length"
              :match-count="codeCacheView.matchCount"
              :total="codeCacheView.total"
              :expanded="codeCacheView.expanded"
              :page-size="codeCacheView.pageSize"
              @toggle="codeCacheView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- How It Works Tab -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding JIT Compilation"
          subtitle="How HotSpot turns hot bytecode into native code — and why warmup looks the way it does"
        >
          <AboutCallout variant="intro">
            <p>
              The JVM starts by <em>interpreting</em> bytecode, then compiles the methods that run
              often into optimized native code. HotSpot uses <strong>tiered compilation</strong>: code
              climbs a ladder of compilers as it gets hotter, each tier trading compile time for
              execution speed. This page shows that activity, the queues feeding it, and the code
              cache that stores the result.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-bar-chart-steps" title="The Compilation Tiers">
            <FeatureGrid>
              <FeatureCard icon="bi-0-circle" variant="neutral" title="Tier 0 — Interpreter">
                Every method starts here. No compilation; the JVM also gathers invocation and
                branch counters to decide what's worth compiling.
              </FeatureCard>
              <FeatureCard icon="bi-1-circle" variant="info" title="Tiers 1–3 — C1 (client)">
                Fast compiles with light optimization. Tier 3 also adds <em>profiling</em> counters so
                C2 can later speculate well. This is what makes early throughput jump during warmup.
              </FeatureCard>
              <FeatureCard icon="bi-4-circle" variant="primary" title="Tier 4 — C2 (server)">
                Slow, aggressive, profile-guided optimization producing the fastest code. Most steady-
                state hot methods end up here. Wrong speculation is undone via deoptimization.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-repeat" variant="warning" title="On-Stack Replacement">
                OSR compiles a method <em>while a long loop is still running</em>, swapping the
                interpreter frame for compiled code mid-execution — why a hot loop speeds up without
                being re-called.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-graph-up" title="Reading the Charts">
            <FeatureGrid>
              <FeatureCard icon="bi-graph-up" variant="primary" title="Activity &amp; Queues">
                Compilation rate over time plus the C1/C2 queue backlog. A large queue during startup
                is normal warmup pressure; a queue that never drains means the compilers can't keep up.
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-split" variant="warning" title="Long Compilations">
                Individual methods that took unusually long to compile — huge methods or inlining
                blow-ups that can stall the queue behind them.
              </FeatureCard>
              <FeatureCard icon="bi-hdd-stack" variant="info" title="Code Cache">
                The fixed-size native region holding all compiled code. It's segmented (non-method,
                profiled, non-profiled) in modern JVMs.
              </FeatureCard>
              <FeatureCard icon="bi-exclamation-octagon" variant="danger" title="Code Cache Full">
                When the cache fills, the JVM <strong>stops compiling</strong> and may flush code —
                throughput falls off a cliff. A non-zero count here is a red flag worth
                <code>-XX:ReservedCodeCacheSize</code>.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="Warmup is real" icon="bi-lightbulb-fill">
            Throughput benchmarks taken before C2 finishes are meaningless. Heavy compilation activity
            that never settles, or repeated recompiles of the same method, points at deoptimization
            churn — check the <em>Deoptimizations</em> page.
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.Compilation</code> — one event per compilation (method, tier/level, OSR flag,
                duration, generated code size). Enabled in the default configuration.
              </li>
              <li>
                <code>jdk.CompilerQueueUtilization</code> — periodic C1/C2 queue depth and add/remove
                rates that drive the queue timeline.
              </li>
              <li>
                <code>jdk.CodeCacheStatistics</code> and <code>jdk.CodeCacheFull</code> — code-cache
                occupancy per segment and the cache-exhaustion event.
              </li>
              <li>
                <code>jdk.CompilerConfiguration</code> — tier thresholds and code-cache size for
                context.
              </li>
            </ul>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>

    <!-- Compilation Terms Modal -->
    <GenericModal
      modal-id="bailoutInfoModal"
      size="lg"
      :show="showModal"
      title="JIT Compilation: Bailouts vs Invalidations"
      icon="bi-info-circle"
      @update:show="showModal = $event"
    >
      <div class="compilation-terms-content">
        <div class="term-section">
          <h6 class="term-heading">
            <i class="bi bi-exclamation-triangle text-warning me-2"></i>
            Bailout:
          </h6>
          <ul class="term-list">
            <li>Occurs when the JIT compiler must abandon optimized execution mid-operation</li>
            <li>The JVM has to fall back to interpreter mode or deoptimize during execution</li>
            <li>Happens when runtime conditions don't match compilation assumptions</li>
            <li>
              Usually triggered by unexpected type changes, uncommon traps, or other on-the-fly
              issues
            </li>
            <li>Shows up in logs as "made not entrant" or "made zombie"</li>
          </ul>
        </div>

        <div class="term-section">
          <h6 class="term-heading">
            <i class="bi bi-x-circle text-danger me-2"></i>
            Invalidation:
          </h6>
          <ul class="term-list">
            <li>When previously compiled code must be completely discarded</li>
            <li>Typically occurs when assumptions made during compilation are no longer valid</li>
            <li>
              Often caused by class loading/unloading, method redefinition, or dependency changes
            </li>
            <li>Affects entire compiled methods rather than specific execution paths</li>
            <li>Results in recompilation on next invocation rather than immediate fallback</li>
          </ul>
        </div>

        <div class="key-difference">
          <div class="alert alert-info">
            <i class="bi bi-lightbulb text-info me-2"></i>
            <strong>Key difference:</strong> bailouts happen during execution (requiring immediate
            action), while invalidations mark code as invalid for future executions (requiring
            recompilation later).
          </div>
        </div>
      </div>
    </GenericModal>

    <!-- nMethods Information Modal -->
    <GenericModal
      modal-id="nMethodsInfoModal"
      size="lg"
      :show="showNMethodsInfoModal"
      title="JIT Compilation: nMethods Memory Usage"
      icon="bi-info-circle"
      @update:show="showNMethodsInfoModal = $event"
    >
      <div class="compilation-terms-content">
        <p>
          In Java JIT compilation, nMethods represent the compiled native code form of Java methods.
          They have two main components:
        </p>

        <div class="term-section">
          <h6 class="term-heading">
            <i class="bi bi-cpu text-primary me-2"></i>
            nMethod Code:
          </h6>
          <ul class="term-list">
            <li>The actual machine code generated by the JIT compiler</li>
            <li>Contains optimized native CPU instructions</li>
            <li>Stored in the code cache memory region</li>
            <li>Directly executed by the CPU (much faster than interpreted bytecode)</li>
            <li>May contain inline caches and optimized instruction sequences</li>
          </ul>
        </div>

        <div class="term-section">
          <h6 class="term-heading">
            <i class="bi bi-layers text-success me-2"></i>
            nMethod Metadata:
          </h6>
          <ul class="term-list">
            <li>Supporting information about the compiled method:</li>
            <li>Method identification information</li>
            <li>Deoptimization data (for falling back to interpreter if assumptions break)</li>
            <li>GC maps (helps garbage collector identify references)</li>
            <li>Exception tables</li>
            <li>Debug information (bytecode-to-native code mapping)</li>
            <li>Dependencies on class hierarchy and method implementations</li>
            <li>Relocation information for references to other code</li>
          </ul>
        </div>

        <div class="key-difference">
          <div class="alert alert-info">
            <i class="bi bi-lightbulb text-info me-2"></i>
            <strong>The metadata enables critical JVM features</strong> like on-stack replacement,
            deoptimization, proper garbage collection, and exception handling while working with
            compiled code.
          </div>
        </div>
      </div>
    </GenericModal>

    <!-- Compilations Information Modal -->
    <GenericModal
      modal-id="compilationsInfoModal"
      size="lg"
      :show="showCompilationsInfoModal"
      title="JIT Compilation: Standard vs OSR Compilation"
      icon="bi-info-circle"
      @update:show="showCompilationsInfoModal = $event"
    >
      <div class="compilation-terms-content">
        <p>In Java JIT compilation, there are two primary compilation methods:</p>

        <div class="term-section">
          <h6 class="term-heading">
            <i class="bi bi-arrow-right-circle text-primary me-2"></i>
            Standard Compilation:
          </h6>
          <ul class="term-list">
            <li>Triggered when a method's invocation counter reaches a threshold</li>
            <li>Compiles the entire method at once</li>
            <li>Entry point is at the beginning of the method</li>
            <li>Optimized code is used for future invocations</li>
            <li>More comprehensive optimizations possible (whole method context)</li>
            <li>Metadata includes standard entry point information</li>
            <li>Generated when methods are called frequently</li>
          </ul>
        </div>

        <div class="term-section">
          <h6 class="term-heading">
            <i class="bi bi-arrow-repeat text-success me-2"></i>
            OSR (On-Stack Replacement):
          </h6>
          <ul class="term-list">
            <li>Triggered when a loop's back-edge counter reaches a threshold</li>
            <li>Compiles methods that are already running (especially long loops)</li>
            <li>Entry point is at a safepoint within the method (not the beginning)</li>
            <li>Allows optimization of currently executing code without waiting for completion</li>
            <li>Special metadata includes:</li>
            <ul class="term-list" style="margin-left: 1rem">
              <li>Deoptimization information for each OSR entry point</li>
              <li>State mapping between interpreter frames and compiled frames</li>
              <li>Loop-specific optimizations</li>
            </ul>
          </ul>
        </div>

        <div class="key-difference">
          <div class="alert alert-info">
            <i class="bi bi-lightbulb text-info me-2"></i>
            <strong>Both compilation types</strong> produce nMethods with machine code and metadata,
            but their triggers, entry points, and optimization approaches differ significantly. The
            HotSpot JVM uses both approaches to ensure optimal performance in different scenarios.
          </div>
        </div>
      </div>
    </GenericModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import type { TabBarItem } from '@/components/TabBar.vue';
import GenericModal from '@/components/GenericModal.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import FormattingService from '@/services/FormattingService.ts';
import JITCompilationData from '@/services/api/model/JITCompilationData.ts';
import ProfileCompilationClient from '@/services/api/ProfileCompilationClient.ts';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import Serie from '@/services/timeseries/model/Serie.ts';
import JITLongCompilation from '@/services/api/model/JITLongCompilation.ts';
import type { CodeCacheData, CodeCacheSegment } from '@/services/api/model/CodeCacheModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import { computed } from 'vue';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const loading = ref(true);
const error = ref(false);
const statisticsData = ref<JITCompilationData>();
const compilationsData = ref<JITLongCompilation[]>([]);

// Time series chart state
const chartLoading = ref(true);
const timeseriesData = ref<Serie>();

// Compiler queue + code cache state
const queueTimeline = ref<TimeseriesData>();
const codeCacheData = ref<CodeCacheData>();

const activeTab = ref('activity');

const compilationsView = useTableView<JITLongCompilation>(compilationsData, {
  searchableText: row => row.method
});

const codeCacheView = useTableView<CodeCacheSegment>(() => codeCacheData.value?.segments ?? [], {
  searchableText: row => row.codeBlobType
});

const c1QueueSeries = computed<number[][]>(() => queueTimeline.value?.series?.[0]?.data ?? []);
const c2QueueSeries = computed<number[][]>(() => queueTimeline.value?.series?.[1]?.data ?? []);
const hasQueueData = computed(
  () =>
    c1QueueSeries.value.some(point => point[1] > 0) ||
    c2QueueSeries.value.some(point => point[1] > 0)
);

const tabs = computed<TabBarItem[]>(() => [
  { id: 'activity', label: 'Activity', icon: 'graph-up' },
  {
    id: 'compilations',
    label: 'Long Compilations',
    icon: 'list-ul',
    badge: compilationsData.value.length || undefined
  },
  {
    id: 'code-cache',
    label: 'Code Cache',
    icon: 'hdd-stack',
    badge: codeCacheData.value?.segments.length || undefined
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

// Modal state
const showModal = ref(false);
const showNMethodsInfoModal = ref(false);
const showCompilationsInfoModal = ref(false);

// Computed metrics for StatsTable
const metricsData = computed(() => {
  if (!statisticsData.value) return [];

  return [
    {
      icon: 'lightning-charge-fill',
      title: 'Compilations',
      value: statisticsData.value.compileCount,
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Standard',
          value: statisticsData.value.standardCompileCount,
          color: '#4285F4'
        },
        {
          label: 'OSR',
          value: statisticsData.value.osrCompileCount,
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'exclamation-triangle-fill',
      title: 'Failed Compilations',
      value: statisticsData.value.bailoutCount + statisticsData.value.invalidatedCount,
      variant: 'danger' as const,
      breakdown: [
        {
          label: 'Bailouts',
          value: statisticsData.value.bailoutCount,
          color: '#EA4335'
        },
        {
          label: 'Invalidations',
          value: statisticsData.value.invalidatedCount,
          color: '#EA4335'
        }
      ]
    },
    {
      icon: 'memory',
      title: 'Memory Usage (nMethods)',
      value: FormattingService.formatBytes(statisticsData.value.nmethodsSize),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Code',
          value: FormattingService.formatBytes(statisticsData.value.nmethodCodeSize),
          color: '#34A853'
        },
        {
          label: 'Metadata',
          value: FormattingService.formatBytes(
            statisticsData.value.nmethodsSize - statisticsData.value.nmethodCodeSize
          ),
          color: '#34A853'
        }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Peak Compilation Time',
      value: FormattingService.formatDuration2Units(statisticsData.value.peakTimeSpent),
      variant: 'warning' as const,
      breakdown: [
        {
          label: 'Total Time',
          value: FormattingService.formatDuration2Units(statisticsData.value.totalTimeSpent),
          color: '#FBBC05'
        }
      ]
    }
  ];
});

// Load JIT compilation data on component mount
onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;

    // Create the client instance
    const compilationClient = new ProfileCompilationClient(profileId);

    // Fetch all data sets in parallel
    const [
      statisticsDataResult,
      timeseriesDataResult,
      compilationsDataResult,
      queueTimelineResult,
      codeCacheResult
    ] = await Promise.all([
      compilationClient.getStatistics(),
      compilationClient.getTimeseries(),
      compilationClient.getCompilations(),
      compilationClient.getQueueTimeline(),
      compilationClient.getCodeCache()
    ]);

    // Update the component state with real data
    statisticsData.value = statisticsDataResult;
    timeseriesData.value = timeseriesDataResult;
    compilationsData.value = compilationsDataResult;
    queueTimeline.value = queueTimelineResult;
    codeCacheData.value = codeCacheResult;

    // Data loaded successfully
    loading.value = false;
    chartLoading.value = false;
  } catch (e) {
    console.error('Failed to load JIT compilation data:', e);
    error.value = true;
    loading.value = false;
    chartLoading.value = false;
  }
});

// Show/hide compilation terms modal
const showTooltipModal = () => {
  showModal.value = true;
};

// Show/hide nMethods information modal
const showNMethodsModal = () => {
  showNMethodsInfoModal.value = true;
};

// Show/hide compilations information modal
const showCompilationsModal = () => {
  showCompilationsInfoModal.value = true;
};

// Method name and path helpers
const getClassMethodName = (method: string): string => {
  if (!method) return '';

  // Extract the method name with parameters
  const lastDotIndex = method.lastIndexOf('#');
  if (lastDotIndex === -1) return method;

  // Get the part after the last dot (method name with params)
  const methodNameWithParams = method.substring(lastDotIndex + 1);

  // Get the class path (everything before the method)
  const packagePath = method.substring(0, lastDotIndex);

  // Get only the class name (last segment before the method)
  const lastClassDotIndex = packagePath.lastIndexOf('.');
  const className =
    lastClassDotIndex !== -1 ? packagePath.substring(lastClassDotIndex + 1) : packagePath;

  return `${className}.${methodNameWithParams}`;
};

const getPackage = (method: string): string => {
  if (!method) return '';

  // Extract the package path (everything up to the last two segments)
  const segments = method.split('.');
  if (segments.length <= 1) return method;

  // Return everything except the last two segments (class and method)
  return segments.slice(0, segments.length - 1).join('.');
};
</script>

<style scoped>
.jit-compilation-container {
  width: 100%;
  color: var(--color-text);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans',
    'Helvetica Neue', sans-serif;
}

.chart-container {
  width: 100%;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.id-col {
  width: 80px;
}

/* Long Compilation Table Styles */
.method-cell {
  display: flex;
  flex-direction: column;
}

.method-name {
  font-weight: 500;
}

.method-path {
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 250px;
}

.mb-4 {
  margin-bottom: 1.5rem;
}

/* Info icon styling */
.compilation-info-icon {
  margin-left: 0.5rem;
  font-size: 0.875rem;
  cursor: help;
  opacity: 0.7;
  transition: opacity 0.2s ease;
}

.compilation-info-icon:hover {
  opacity: 1;
}

/* Modal content styling */
.compilation-terms-content {
  font-size: 0.95rem;
  line-height: 1.5;
}

.term-section {
  margin-bottom: 1.5rem;
}

.term-heading {
  color: var(--color-dark);
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid var(--color-border);
  display: flex;
  align-items: center;
}

.term-list {
  margin: 0;
  padding-left: 1.5rem;
  list-style-type: disc;
}

.term-list li {
  margin-bottom: 0.5rem;
  color: var(--color-text);
  line-height: 1.4;
}

.key-difference {
  margin-top: 1.5rem;
}

.key-difference .alert {
  margin-bottom: 0;
  font-size: 0.95rem;
}

/* Responsive Adjustments */
@media (max-width: 768px) {
  .chart-container {
    height: 430px;
  }
}
</style>
