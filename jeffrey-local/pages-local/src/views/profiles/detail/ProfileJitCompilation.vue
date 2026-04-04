<template>
  <div class="jit-compilation-container">
    <!-- Loading State -->
    <div v-if="loading" class="text-center my-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading JIT compilation data...</span>
      </div>
      <p class="mt-3">Loading JIT compilation data...</p>
    </div>

    <div v-else-if="error" class="text-center my-5">
      <div class="alert alert-danger">
        <i class="bi bi-exclamation-triangle-fill me-2"></i>
        Failed to load JIT compilation data
      </div>
    </div>

    <div v-else>
      <!-- Header Section with Stats Overview -->
      <PageHeader
        title="JIT Compilation"
        description="Real-time insights into Java Just-In-Time compilation performance"
        icon="bi-lightning-charge-fill"
      />

      <!-- Main Dashboard Grid -->
      <div class="dashboard-grid">
        <!-- Stats Table -->
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

        <!-- Row 3: Time Series Graph -->
        <div class="chart-card mb-4">
          <div class="chart-card-header">
            <h5>JIT Compilation Activity by CPU Samples</h5>
          </div>
          <div class="chart-container">
            <TimeSeriesChart
              :primaryData="timeseriesData?.data"
              :primaryTitle="timeseriesData?.name"
              :visibleMinutes="60"
            />
          </div>
        </div>

        <!-- Row 4: Long Compilation Table -->
        <div class="data-table-card">
          <div class="chart-card-header">
            <h5>Long Compilations</h5>
            <div class="chart-controls">
              <div class="d-flex align-items-center">
                <Badge
                  key-label="Threshold"
                  :value="`${statisticsData?.compileMethodThreshold}ms`"
                  variant="primary"
                  size="s"
                />
              </div>
            </div>
          </div>
          <EmptyState
            v-if="compilationsData.length === 0"
            icon="bi-lightning"
            title="No long compilations recorded"
          />
          <DataTable v-else>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Method</th>
                  <th>Level</th>
                  <th>Time</th>
                  <th>Code Size</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="compilation in compilationsData"
                  :key="compilation.compileId"
                  :class="{ 'table-danger': !compilation.succeded }"
                  :title="compilation.method"
                >
                  <td>{{ compilation.compileId }}</td>
                  <td>
                    <div class="method-cell">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="method-name">{{
                          getClassMethodName(compilation.method)
                        }}</span>
                        <Badge :value="compilation.compiler" variant="primary" size="xs" />
                        <Badge v-if="compilation.isOsr" value="OSR" variant="info" size="xs" />
                      </div>
                      <span class="method-path text-muted small">{{
                        getPackage(compilation.method)
                      }}</span>
                    </div>
                  </td>
                  <td>{{ compilation.compileLevel }}</td>
                  <td>{{ FormattingService.formatDuration2Units(compilation.duration) }}</td>
                  <td>{{ FormattingService.formatBytes(compilation.codeSize) }}</td>
                  <td>
                    <Badge v-if="compilation.succeded" value="Success" variant="success" size="s" />
                    <Badge v-else value="Failed" variant="danger" size="s" />
                  </td>
                </tr>
              </tbody>
          </DataTable>
        </div>
      </div>
    </div>

    <!-- Compilation Terms Modal -->
    <GenericModal
      modal-id="bailoutInfoModal"
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
import GenericModal from '@/components/GenericModal.vue';
import FormattingService from '@/services/FormattingService.ts';
import JITCompilationData from '@/services/api/model/JITCompilationData.ts';
import ProfileCompilationClient from '@/services/api/ProfileCompilationClient.ts';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import DataTable from '@/components/table/DataTable.vue';
import Serie from '@/services/timeseries/model/Serie.ts';
import JITLongCompilation from '@/services/api/model/JITLongCompilation.ts';
import { computed } from 'vue';

const route = useRoute();

const loading = ref(true);
const error = ref(false);
const statisticsData = ref<JITCompilationData>();
const compilationsData = ref<JITLongCompilation[]>([]);

// Time series chart state
const chartLoading = ref(true);
const timeseriesData = ref<Serie>();

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
    const [statisticsDataResult, timeseriesDataResult, compilationsDataResult] = await Promise.all([
      compilationClient.getStatistics(),
      compilationClient.getTimeseries(),
      compilationClient.getCompilations()
    ]);

    // Update the component state with real data
    statisticsData.value = statisticsDataResult;
    timeseriesData.value = timeseriesDataResult;
    compilationsData.value = compilationsDataResult;

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

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
}

/* Data Table Card */
.data-table-card,
.chart-card {
  background: var(--color-white);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.chart-container {
  padding: 1rem;
  height: 550px;
}

.chart-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--color-border-row);
}

.chart-card-header h5 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--color-text);
}

.chart-controls {
  display: flex;
  align-items: center;
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
