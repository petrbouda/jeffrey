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

      <!-- Tabbed Content Section -->
      <ChartSectionWithTabs
          icon="diagram-3"
          :tabs="analysisTabs"
          :full-width="true"
          id-prefix="gcroots-"
      >
        <!-- Overview Tab -->
        <template #overview>
          <div class="row">
            <!-- Pie Chart -->
            <div class="col-md-6">
              <DonutChartCard
                  v-if="gcRootData"
                  title="Root Type Distribution"
                  :series="chartSeries"
                  :labels="chartLabels"
                  :colors="chartColors"
                  :legend-items="chartLegendItems"
                  :total-value="FormattingService.formatNumber(gcRootData.totalRoots)"
                  :tooltip-formatter="(val: number) => FormattingService.formatNumber(val) + ' roots'"
              />
            </div>

            <!-- Breakdown Table -->
            <div class="col-md-6">
              <BasePanel title="Root Type Breakdown">
                <table class="table table-sm mb-0">
                  <tbody>
                  <tr v-for="item in sortedRootsArray" :key="item.type">
                    <td style="width: 16px; padding-right: 0;">
                      <span class="legend-dot" :style="{ backgroundColor: getRootTypeColor(item.type) }"></span>
                    </td>
                    <td>{{ formatRootType(item.type) }}</td>
                    <td class="text-end font-monospace">{{ FormattingService.formatNumber(item.count) }}</td>
                    <td class="text-end text-muted" style="width: 60px;">{{ getRootPercentage(item.count).toFixed(1) }}%</td>
                  </tr>
                  </tbody>
                </table>
              </BasePanel>
            </div>
          </div>
        </template>

        <!-- About Tab -->
        <template #about>
          <div class="about-container">
            <!-- Header Section -->
            <div class="about-header">
              <div class="about-header-icon">
                <i class="bi bi-question-circle"></i>
              </div>
              <div>
                <h5 class="mb-1">Understanding GC Roots</h5>
                <p class="text-muted mb-0">What prevents objects from being garbage collected</p>
              </div>
            </div>

            <!-- Intro -->
            <div class="about-intro">
              <p>GC roots are the starting points for garbage collection. The garbage collector traces object references
                starting from these roots to determine which objects are reachable and should be kept alive.
                Objects that cannot be traced from any GC root are considered unreachable and eligible for collection.</p>
            </div>

            <!-- Root Types Section -->
            <h6 class="section-title">
              <i class="bi bi-diagram-3 me-2"></i>
              GC Root Types
            </h6>

            <div class="feature-grid">
              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
                  <i class="bi bi-globe"></i>
                </div>
                <div class="feature-content">
                  <h6>JNI Global</h6>
                  <p>Objects referenced by JNI global references. These are native code references that persist across JNI calls and must be explicitly deleted.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
                  <i class="bi bi-geo-alt"></i>
                </div>
                <div class="feature-content">
                  <h6>JNI Local</h6>
                  <p>Objects referenced by JNI local references within a native method's stack frame. Automatically cleaned up when the native method returns.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
                  <i class="bi bi-layers"></i>
                </div>
                <div class="feature-content">
                  <h6>Java Frame</h6>
                  <p>Objects referenced from Java stack frames, including local variables and method parameters in active method calls.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);">
                  <i class="bi bi-cpu"></i>
                </div>
                <div class="feature-content">
                  <h6>Native Stack</h6>
                  <p>Objects referenced from native code execution stacks. These are JNI references held on the native (C/C++) call stack.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);">
                  <i class="bi bi-pin-angle"></i>
                </div>
                <div class="feature-content">
                  <h6>Sticky Class</h6>
                  <p>System classes that are never unloaded, such as <code>java.lang.*</code>, primitive types, and other bootstrap classes loaded by the JVM.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #30cfd0 0%, #330867 100%);">
                  <i class="bi bi-lock"></i>
                </div>
                <div class="feature-content">
                  <h6>Thread Block</h6>
                  <p>Objects held as references within thread synchronization blocks. These are objects referenced by threads waiting on monitors.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #ff0844 0%, #ffb199 100%);">
                  <i class="bi bi-shield-lock"></i>
                </div>
                <div class="feature-content">
                  <h6>Monitor Used</h6>
                  <p>Objects actively being used as monitor locks (<code>synchronized</code>). These objects are held by threads that have acquired their intrinsic lock.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%);">
                  <i class="bi bi-person-badge"></i>
                </div>
                <div class="feature-content">
                  <h6>Thread Object</h6>
                  <p>Thread objects themselves. Every active Java thread is a GC root, keeping itself and all objects it references alive.</p>
                </div>
              </div>
            </div>

            <!-- Why It Matters -->
            <h6 class="section-title">
              <i class="bi bi-lightning-charge me-2"></i>
              Why It Matters
            </h6>

            <div class="benefits-list">
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Identify memory leaks by finding unexpected retention paths from GC roots</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Understand which objects cannot be collected and why they are kept alive</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Analyze thread state and synchronization issues through monitor roots</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Debug native code memory issues through JNI global and local references</span>
              </div>
            </div>

            <!-- Note -->
            <div class="about-note">
              <div class="note-icon">
                <i class="bi bi-lightbulb-fill"></i>
              </div>
              <div class="note-content">
                <strong>Investigating Memory Leaks?</strong>
                <p class="mb-0">Look for objects with unexpected GC roots. Common culprits include static fields (via Sticky Class),
                  thread locals (via Thread Object), and JNI global references that weren't properly cleaned up.
                  Use the dominator tree and shortest path to GC root analysis to trace retention paths.</p>
              </div>
            </div>
          </div>
        </template>
      </ChartSectionWithTabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import DonutChartCard from '@/components/DonutChartCard.vue';
import type { LegendItem } from '@/components/DonutChartCard.vue';
import BasePanel from '@/components/BasePanel.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import GCRootSummary from '@/services/api/model/GCRootSummary';
import FormattingService from '@/services/FormattingService';

const route = useRoute();

const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const gcRootData = ref<GCRootSummary | null>(null);
const sortColumn = ref('count');
const sortDirection = ref<'asc' | 'desc'>('desc');

let client: HeapDumpClient;

const rootTypeColors = [
  '#4285F4', '#EA4335', '#FBBC05', '#34A853', '#9C27B0',
  '#FF5722', '#00BCD4', '#795548', '#607D8B', '#E91E63'
];

const analysisTabs = [
  { id: 'overview', label: 'Overview', icon: 'pie-chart' },
  { id: 'about', label: 'How It Works', icon: 'info-circle' }
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

const chartEntries = computed(() => {
  if (!gcRootData.value) return [];
  return Object.entries(gcRootData.value.rootsByType).sort((a, b) => b[1] - a[1]);
});

const chartSeries = computed(() => chartEntries.value.map(([, count]) => count));
const chartLabels = computed(() => chartEntries.value.map(([type]) => formatRootType(type)));
const chartColors = computed(() => chartEntries.value.map(([type]) => getRootTypeColor(type)));

const chartLegendItems = computed<LegendItem[]>(() => {
  return chartEntries.value.map(([type, count]) => ({
    color: getRootTypeColor(type),
    label: formatRootType(type),
    value: FormattingService.formatNumber(count)
  }));
});

const sortedRootsArray = computed(() => {
  if (!gcRootData.value) return [];

  const entries = Object.entries(gcRootData.value.rootsByType).map(([type, count]) => ({
    type,
    count
  }));

  const direction = sortDirection.value === 'asc' ? 1 : -1;
  if (sortColumn.value === 'count') {
    entries.sort((a, b) => direction * (a.count - b.count));
  } else {
    entries.sort((a, b) => direction * a.type.localeCompare(b.type));
  }

  return entries;
});

const toggleSort = (column: string) => {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortColumn.value = column;
    sortDirection.value = column === 'count' ? 'desc' : 'asc';
  }
};

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

const scrollToTop = () => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;

    client = new HeapDumpClient(profileId);

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

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.table td {
  font-size: 0.85rem;
  padding: 0.5rem 0.5rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.table tbody tr:last-child td {
  border-bottom: none;
}

/* About Tab Styles */
.about-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 1.5rem;
}

.about-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #e9ecef;
}

.about-header-icon {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.5rem;
  flex-shrink: 0;
}

.about-header h5 {
  font-weight: 600;
  color: #343a40;
}

.about-intro {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 1rem 1.25rem;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
  line-height: 1.6;
  color: #495057;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 1rem;
  display: flex;
  align-items: center;
}

.section-title i {
  color: #6c757d;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

@media (max-width: 768px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }
}

.feature-card {
  display: flex;
  gap: 0.875rem;
  padding: 1rem;
  background: white;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.feature-card:hover {
  border-color: #dee2e6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.1rem;
  flex-shrink: 0;
}

.feature-content h6 {
  font-size: 0.875rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.25rem;
}

.feature-content p {
  font-size: 0.8rem;
  color: #6c757d;
  margin-bottom: 0;
  line-height: 1.5;
}

.feature-content code {
  background-color: #f1f3f4;
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.85em;
  color: #d63384;
}

.benefits-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.benefit-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  font-size: 0.85rem;
  color: #495057;
  padding: 0.5rem 0;
}

.benefit-item i {
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.about-note {
  display: flex;
  gap: 1rem;
  background: linear-gradient(135deg, #fff8e1 0%, #fffde7 100%);
  border: 1px solid #ffe082;
  border-radius: 8px;
  padding: 1rem;
}

.note-icon {
  color: #f9a825;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.note-content {
  font-size: 0.85rem;
  line-height: 1.6;
  color: #5d4037;
}

.note-content strong {
  color: #4e342e;
}

.note-content code {
  background-color: rgba(255, 224, 130, 0.5);
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.9em;
  color: #bf360c;
}
</style>
