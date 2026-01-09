<template>
  <LoadingState v-if="loading" message="Loading class histogram..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a heap dump and add it to the recording folder.</p>
      </div>
    </div>
  </div>

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="Class Histogram"
        description="Memory usage breakdown by class"
        icon="bi-bar-chart-steps"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Filter Controls -->
    <div class="filter-controls mb-3">
      <div class="row align-items-center">
        <div class="col-auto">
          <label class="form-label mb-0 me-2">Top:</label>
          <select v-model="histogramTopN" class="form-select form-select-sm d-inline-block select-narrow" @change="loadHistogram">
            <option :value="50">50</option>
            <option :value="100">100</option>
            <option :value="200">200</option>
            <option :value="500">500</option>
          </select>
        </div>
        <div class="col-auto">
          <label class="form-label mb-0 me-2">Sort by:</label>
          <select v-model="histogramSortBy" class="form-select form-select-sm d-inline-block select-wide" @change="loadHistogram">
            <option value="SIZE">Size</option>
            <option value="COUNT">Instance Count</option>
            <option value="CLASS_NAME">Class Name</option>
          </select>
        </div>
        <div class="col-auto ms-auto">
          <small class="text-muted">Showing {{ histogramData.length }} classes</small>
        </div>
      </div>
    </div>

    <!-- Data Table -->
    <div class="table-card">
      <div class="table-responsive">
        <table class="table table-sm table-hover mb-0">
          <thead>
          <tr>
            <th style="width: 50px;">#</th>
            <th>Class Name</th>
            <th class="text-end" style="width: 120px;">Instances</th>
            <th class="text-end" style="width: 120px;">Total Size</th>
            <th style="width: 200px;">% of Max</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(entry, index) in histogramData" :key="entry.className">
            <td class="text-muted">{{ index + 1 }}</td>
            <td>
              <code class="class-name">{{ entry.className }}</code>
            </td>
            <td class="text-end font-monospace">{{ FormattingService.formatNumber(entry.instanceCount) }}</td>
            <td class="text-end font-monospace">{{ FormattingService.formatBytes(entry.totalSize) }}</td>
            <td>
              <div class="d-flex align-items-center gap-2">
                <div class="progress flex-grow-1" style="height: 6px;">
                  <div
                      class="progress-bar"
                      :style="{ width: getDistributionPercentage(entry) + '%', backgroundColor: '#4285F4' }"
                  ></div>
                </div>
                <small class="text-muted" style="min-width: 45px;">{{ getDistributionPercentage(entry).toFixed(1) }}%</small>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import ClassHistogramEntry from '@/services/api/model/ClassHistogramEntry';
import HeapSummary from '@/services/api/model/HeapSummary';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const histogramData = ref<ClassHistogramEntry[]>([]);
const summary = ref<HeapSummary | null>(null);
const histogramTopN = ref(50);
const histogramSortBy = ref('SIZE');

let client: HeapDumpClient;

// Computed metrics for StatsTable
const summaryMetrics = computed(() => {
  if (!summary.value) return [];
  return [
    {
      icon: 'layers',
      title: 'Total Classes',
      value: FormattingService.formatNumber(summary.value.classCount),
      variant: 'highlight' as const
    },
    {
      icon: 'collection',
      title: 'Total Instances',
      value: FormattingService.formatNumber(summary.value.totalInstances),
      variant: 'info' as const
    },
    {
      icon: 'hdd',
      title: 'Total Heap Size',
      value: FormattingService.formatBytes(summary.value.totalBytes),
      variant: 'info' as const
    },
    {
      icon: 'diagram-3',
      title: 'GC Roots',
      value: FormattingService.formatNumber(summary.value.gcRootCount),
      variant: 'success' as const
    }
  ];
});

// Computed max value for percentage calculation based on sort field
const maxValue = computed(() => {
  if (histogramData.value.length === 0) return 0;

  if (histogramSortBy.value === 'COUNT') {
    return Math.max(...histogramData.value.map(entry => entry.instanceCount));
  }
  // For SIZE and CLASS_NAME, use totalSize
  return Math.max(...histogramData.value.map(entry => entry.totalSize));
});

// Calculate distribution percentage based on sort field
const getDistributionPercentage = (entry: ClassHistogramEntry): number => {
  if (maxValue.value === 0) return 0;

  if (histogramSortBy.value === 'COUNT') {
    return (entry.instanceCount / maxValue.value) * 100;
  }
  // For SIZE and CLASS_NAME, use totalSize
  return (entry.totalSize / maxValue.value) * 100;
};

const loadHistogram = async () => {
  try {
    histogramData.value = await client.getHistogram(histogramTopN.value, histogramSortBy.value);
  } catch (err) {
    console.error('Error loading histogram:', err);
  }
};


const loadData = async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    loading.value = true;
    error.value = null;

    client = new HeapDumpClient(workspaceId.value, projectId.value, profileId);

    heapExists.value = await client.exists();

    if (!heapExists.value) {
      loading.value = false;
      return;
    }

    // Load summary and histogram in parallel
    const [summaryData, histogramResult] = await Promise.all([
      client.getSummary(),
      client.getHistogram(histogramTopN.value, histogramSortBy.value)
    ]);

    summary.value = summaryData;
    histogramData.value = histogramResult;

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load class histogram';
    console.error('Error loading class histogram:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

.class-name {
  font-size: 0.8rem;
  word-break: break-all;
  background-color: transparent;
  color: #495057;
}

.table-card {
  background: white;
  border: 1px solid #dee2e6;
  overflow: hidden;
}

.table thead th {
  background-color: #fafbfc;
  font-weight: 600;
  color: #495057;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 0.75rem;
  border-bottom: 1px solid #e9ecef;
}

.table td {
  font-size: 0.8rem;
  padding: 0.6rem 0.75rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.table tbody tr:hover {
  background-color: rgba(66, 133, 244, 0.04);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.filter-controls {
  background-color: #f8f9fa;
  padding: 0.75rem 1rem;
  border: 1px solid #dee2e6;
}

.select-narrow {
  width: 80px;
}

.select-wide {
  width: 150px;
}

.progress {
  background-color: #e9ecef;
}

.progress-bar {
  transition: width 0.3s ease;
}

.font-monospace {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 0.8rem;
}
</style>
