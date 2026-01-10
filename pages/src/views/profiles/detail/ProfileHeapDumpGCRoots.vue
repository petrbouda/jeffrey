<template>
  <LoadingState v-if="loading" message="Loading GC roots..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a heap dump and add it to the recording folder.</p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
      v-else-if="!cacheReady"
      icon="diagram-3"
      message="The heap dump needs to be initialized before you can view GC roots. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="GC Roots"
        description="Garbage collection root objects that prevent memory from being collected"
        icon="bi-diagram-3"
    />

    <div v-if="gcRootData">
      <!-- Summary Metrics -->
      <StatsTable :metrics="summaryMetrics" class="mb-4" />

      <!-- Distribution Chart and Table Grid -->
      <div class="content-grid">
        <!-- Pie Chart -->
        <PieChart
            v-if="chartData.length > 0"
            title="Root Type Distribution"
            icon="pie-chart"
            :data="chartData"
            :total="gcRootData.totalRoots"
            :color-mapping="getChartColor"
            :value-formatter="formatRootCount"
        />

        <!-- Data Table -->
        <div class="table-section">
          <div class="section-header">
            <h4>
              <i class="bi bi-table me-2"></i>
              Root Type Breakdown
            </h4>
          </div>
          <div class="table-card">
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                <tr>
                  <th>Root Type</th>
                  <th class="text-end" style="width: 120px;">Count</th>
                  <th style="width: 250px;">Distribution</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="(count, type) in sortedRootsByType" :key="type">
                  <td>
                    <div class="d-flex align-items-center">
                      <span class="type-indicator" :style="{ backgroundColor: getRootTypeColor(type as string) }"></span>
                      {{ formatRootType(type as string) }}
                    </div>
                  </td>
                  <td class="text-end font-monospace">{{ FormattingService.formatNumber(count) }}</td>
                  <td>
                    <div class="d-flex align-items-center gap-2">
                      <div class="progress flex-grow-1" style="height: 8px;">
                        <div
                            class="progress-bar"
                            :style="{ width: getRootPercentage(count) + '%', backgroundColor: getRootTypeColor(type as string) }"
                        ></div>
                      </div>
                      <small class="text-muted percentage-label">{{ getRootPercentage(count).toFixed(1) }}%</small>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
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
import PieChart from '@/components/PieChart.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import GCRootSummary from '@/services/api/model/GCRootSummary';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const gcRootData = ref<GCRootSummary | null>(null);

let client: HeapDumpClient;

const rootTypeColors = [
  '#4285F4', '#EA4335', '#FBBC05', '#34A853', '#9C27B0',
  '#FF5722', '#00BCD4', '#795548', '#607D8B', '#E91E63'
];

// Computed metrics for StatsTable
const summaryMetrics = computed(() => {
  if (!gcRootData.value) return [];
  return [
    {
      icon: 'diagram-3',
      title: 'Total GC Roots',
      value: FormattingService.formatNumber(gcRootData.value.totalRoots),
      variant: 'highlight' as const
    },
    {
      icon: 'tags',
      title: 'Root Types',
      value: Object.keys(gcRootData.value.rootsByType).length.toString(),
      variant: 'info' as const
    }
  ];
});

// Chart data for PieChart
const chartData = computed(() => {
  if (!gcRootData.value) return [];
  return Object.entries(gcRootData.value.rootsByType)
      .sort((a, b) => b[1] - a[1])
      .map(([type, count]) => ({
        label: formatRootType(type),
        value: count
      }));
});

const sortedRootsByType = computed(() => {
  if (!gcRootData.value) return {};

  const entries = Object.entries(gcRootData.value.rootsByType);
  entries.sort((a, b) => b[1] - a[1]);
  return Object.fromEntries(entries);
});

const formatRootType = (type: string): string => {
  return type
      .replace(/_/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
};

const getRootPercentage = (count: number): number => {
  if (!gcRootData.value || gcRootData.value.totalRoots === 0) return 0;
  return (count / gcRootData.value.totalRoots) * 100;
};

const getRootTypeColor = (type: string): string => {
  if (!gcRootData.value) return rootTypeColors[0];
  const types = Object.keys(gcRootData.value.rootsByType);
  const index = types.indexOf(type);
  return rootTypeColors[index % rootTypeColors.length];
};

const getChartColor = (label: string, index: number): string => {
  return rootTypeColors[index % rootTypeColors.length];
};

const formatRootCount = (value: number): string => {
  return FormattingService.formatNumber(value) + ' roots';
};

const scrollToTop = () => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
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

    cacheReady.value = await client.isCacheReady();

    if (!cacheReady.value) {
      loading.value = false;
      return;
    }

    gcRootData.value = await client.getGCRoots();

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load GC roots';
    console.error('Error loading GC roots:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  scrollToTop();
  loadData();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
  align-items: stretch;
}

.content-grid :deep(.dashboard-section) {
  margin-bottom: 0;
}

.content-grid :deep(.charts-grid) {
  height: 100%;
}

.content-grid :deep(.chart-card) {
  height: 100%;
}

@media (max-width: 1200px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

.table-section {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.section-header {
  padding: 1rem 1.5rem;
  background: white;
  border: 1px solid #dee2e6;
  border-bottom: 1px solid #dee2e6;
}

.section-header h4 {
  margin: 0;
  color: #2c3e50;
  font-size: 1rem;
  font-weight: 600;
}

.table-card {
  background: white;
  border: 1px solid #dee2e6;
  border-top: none;
  overflow: auto;
  flex: 1;
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
  font-size: 0.85rem;
  padding: 0.75rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.table tbody tr:hover {
  background-color: rgba(66, 133, 244, 0.04);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.type-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 0.5rem;
  flex-shrink: 0;
}

.progress {
  background-color: #e9ecef;
}

.progress-bar {
  transition: width 0.3s ease;
}

.percentage-label {
  min-width: 50px;
  text-align: right;
}

.font-monospace {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
}
</style>
