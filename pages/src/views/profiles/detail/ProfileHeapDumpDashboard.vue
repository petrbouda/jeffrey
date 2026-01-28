<template>
  <LoadingState v-if="loading" message="Loading heap dump dashboard..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile.</p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
      v-else-if="!cacheReady"
      icon="speedometer2"
      message="The heap dump needs to be initialized before the dashboard is available."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="Heap Dump Dashboard"
        description="Key findings and actionable insights at a glance"
        icon="bi-speedometer2"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <div class="row g-3">
      <!-- Insights Panel -->
      <div class="col-lg-7">
        <div class="dashboard-section">
          <h6 class="section-title">
            <i class="bi bi-lightbulb me-2"></i>
            Insights
          </h6>
          <div class="insights-list">
            <div v-for="insight in insights" :key="insight.title" class="insight-card"
                 :class="'insight-' + insight.severity">
              <div class="insight-icon">
                <i :class="getInsightIcon(insight.severity)"></i>
              </div>
              <div class="insight-content">
                <div class="insight-title">{{ insight.title }}</div>
                <div class="insight-description">{{ insight.description }}</div>
              </div>
              <router-link v-if="insight.actionRoute" :to="insight.actionRoute" class="insight-action">
                View <i class="bi bi-arrow-right"></i>
              </router-link>
            </div>
            <div v-if="insights.length === 0" class="text-center text-muted py-4">
              <i class="bi bi-check-circle fs-3 d-block mb-2 text-success"></i>
              <p class="small mb-0">No significant findings. Run individual analyses for detailed information.</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick Stats Panel -->
      <div class="col-lg-5">
        <!-- Top Memory Consumers -->
        <div class="dashboard-section mb-3">
          <h6 class="section-title">
            <i class="bi bi-bar-chart me-2"></i>
            Top Memory Consumers
          </h6>
          <div v-if="topClasses.length > 0" class="top-classes-list">
            <div v-for="(entry, index) in topClasses" :key="index" class="top-class-item">
              <span class="top-class-rank">{{ index + 1 }}.</span>
              <span class="top-class-name">{{ simpleClassName(entry.className) }}</span>
              <span class="top-class-size font-monospace">{{ FormattingService.formatBytes(entry.totalSize) }}</span>
              <span class="top-class-percent text-muted">({{ getClassPercent(entry) }}%)</span>
            </div>
            <router-link :to="`/profiles/${profileId}/heap-dump/histogram`" class="see-more-link">
              See full histogram <i class="bi bi-arrow-right"></i>
            </router-link>
          </div>
          <div v-else class="text-center text-muted py-3 small">No histogram data available.</div>
        </div>

        <!-- Analysis Status -->
        <div class="dashboard-section">
          <h6 class="section-title">
            <i class="bi bi-clipboard-check me-2"></i>
            Analysis Status
          </h6>
          <div class="analysis-status-list">
            <div class="analysis-status-item">
              <span>String Analysis</span>
              <span :class="stringAnalysisReady ? 'badge bg-success' : 'badge bg-secondary'">
                {{ stringAnalysisReady ? 'Ready' : 'Not Run' }}
              </span>
            </div>
            <div class="analysis-status-item">
              <span>Biggest Objects</span>
              <span :class="biggestObjectsReady ? 'badge bg-success' : 'badge bg-secondary'">
                {{ biggestObjectsReady ? 'Ready' : 'Not Run' }}
              </span>
            </div>
            <div class="analysis-status-item">
              <span>Collection Analysis</span>
              <span :class="collectionAnalysisReady ? 'badge bg-success' : 'badge bg-secondary'">
                {{ collectionAnalysisReady ? 'Ready' : 'Not Run' }}
              </span>
            </div>
            <div class="analysis-status-item">
              <span>Leak Suspects</span>
              <span :class="leakSuspectsReady ? 'badge bg-success' : 'badge bg-secondary'">
                {{ leakSuspectsReady ? 'Ready' : 'Not Run' }}
              </span>
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
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import HeapSummary from '@/services/api/model/HeapSummary';
import ClassHistogramEntry from '@/services/api/model/ClassHistogramEntry';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const summary = ref<HeapSummary | null>(null);
const topClasses = ref<ClassHistogramEntry[]>([]);
const stringAnalysisReady = ref(false);
const biggestObjectsReady = ref(false);
const collectionAnalysisReady = ref(false);
const leakSuspectsReady = ref(false);

interface Insight {
  severity: 'critical' | 'warning' | 'info' | 'success';
  title: string;
  description: string;
  actionRoute?: string;
}

const insights = ref<Insight[]>([]);

let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  if (!summary.value) return [];
  return [
    {
      icon: 'hdd',
      title: 'Heap Size',
      value: FormattingService.formatBytes(summary.value.totalBytes),
      variant: 'highlight' as const
    },
    {
      icon: 'box',
      title: 'Total Objects',
      value: FormattingService.formatNumber(summary.value.totalInstances),
      variant: 'info' as const
    },
    {
      icon: 'layers',
      title: 'Classes',
      value: FormattingService.formatNumber(summary.value.classCount),
      variant: 'info' as const
    },
    {
      icon: 'diagram-3',
      title: 'GC Roots',
      value: FormattingService.formatNumber(summary.value.gcRootCount),
      variant: 'info' as const
    }
  ];
});

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
};

const getClassPercent = (entry: ClassHistogramEntry): string => {
  if (!summary.value || summary.value.totalBytes === 0) return '0.0';
  return ((entry.totalSize / summary.value.totalBytes) * 100).toFixed(1);
};

const getInsightIcon = (severity: string): string => {
  switch (severity) {
    case 'critical': return 'bi bi-exclamation-circle-fill text-danger';
    case 'warning': return 'bi bi-exclamation-triangle-fill text-warning';
    case 'info': return 'bi bi-info-circle-fill text-info';
    case 'success': return 'bi bi-check-circle-fill text-success';
    default: return 'bi bi-info-circle text-muted';
  }
};

const buildInsights = () => {
  const result: Insight[] = [];

  // Top class concentration insight
  if (topClasses.value.length >= 3 && summary.value) {
    const top3Size = topClasses.value.slice(0, 3).reduce((sum, c) => sum + c.totalSize, 0);
    const top3Percent = (top3Size / summary.value.totalBytes) * 100;
    if (top3Percent > 50) {
      result.push({
        severity: 'warning',
        title: `Top 3 classes hold ${top3Percent.toFixed(0)}% of heap`,
        description: topClasses.value.slice(0, 3).map(c => simpleClassName(c.className)).join(', '),
        actionRoute: `/profiles/${profileId}/heap-dump/histogram`
      });
    }
  }

  // Analysis availability hints
  if (!biggestObjectsReady.value) {
    result.push({
      severity: 'info',
      title: 'Biggest Objects analysis available',
      description: 'Find individual objects consuming the most memory.',
      actionRoute: `/profiles/${profileId}/heap-dump/biggest-objects`
    });
  }

  if (!collectionAnalysisReady.value) {
    result.push({
      severity: 'info',
      title: 'Collection Analysis available',
      description: 'Check for over-allocated and empty collections wasting memory.',
      actionRoute: `/profiles/${profileId}/heap-dump/collection-analysis`
    });
  }

  if (!leakSuspectsReady.value) {
    result.push({
      severity: 'info',
      title: 'Leak Suspects analysis available',
      description: 'Run heuristic analysis to identify potential memory leaks.',
      actionRoute: `/profiles/${profileId}/heap-dump/leak-suspects`
    });
  }

  insights.value = result;
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    client = new HeapDumpClient(profileId);

    heapExists.value = await client.exists();
    if (!heapExists.value) { loading.value = false; return; }

    cacheReady.value = await client.isCacheReady();
    if (!cacheReady.value) { loading.value = false; return; }

    // Load summary and top classes in parallel
    const [summaryResult, histogramResult] = await Promise.all([
      client.getSummary(),
      client.getHistogram(5, 'SIZE')
    ]);
    summary.value = summaryResult;
    topClasses.value = histogramResult;

    // Check analysis statuses in parallel
    const [stringExists, biggestExists, collectionExists, leakExists] = await Promise.all([
      client.stringAnalysisExists(),
      client.biggestObjectsExists(),
      client.collectionAnalysisExists(),
      client.leakSuspectsExists()
    ]);
    stringAnalysisReady.value = stringExists;
    biggestObjectsReady.value = biggestExists;
    collectionAnalysisReady.value = collectionExists;
    leakSuspectsReady.value = leakExists;

    buildInsights();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load dashboard';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) workspaceContent.scrollTop = 0;
  loadData();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

.dashboard-section {
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 1rem 1.25rem;
  background: white;
}

.section-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #495057;
  margin-bottom: 0.75rem;
  display: flex;
  align-items: center;
}

.section-title i {
  color: #6c757d;
}

/* Insights */
.insights-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.insight-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border-radius: 6px;
  border: 1px solid #e9ecef;
}

.insight-critical { background: #fef2f2; border-color: #fecaca; }
.insight-warning { background: #fffbeb; border-color: #fde68a; }
.insight-info { background: #eff6ff; border-color: #bfdbfe; }
.insight-success { background: #f0fdf4; border-color: #bbf7d0; }

.insight-icon {
  flex-shrink: 0;
  font-size: 1.1rem;
}

.insight-content {
  flex: 1;
  min-width: 0;
}

.insight-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #343a40;
}

.insight-description {
  font-size: 0.75rem;
  color: #6c757d;
  margin-top: 0.1rem;
}

.insight-action {
  flex-shrink: 0;
  font-size: 0.75rem;
  font-weight: 500;
  color: #6f42c1;
  text-decoration: none;
  white-space: nowrap;
}

.insight-action:hover {
  text-decoration: underline;
}

/* Top Classes */
.top-classes-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.top-class-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  padding: 0.3rem 0;
}

.top-class-rank {
  color: #6c757d;
  font-weight: 500;
  min-width: 1.5rem;
}

.top-class-name {
  flex: 1;
  font-weight: 500;
  color: #343a40;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-class-size {
  flex-shrink: 0;
  font-size: 0.75rem;
}

.top-class-percent {
  flex-shrink: 0;
  font-size: 0.7rem;
  min-width: 40px;
  text-align: right;
}

.see-more-link {
  display: block;
  text-align: center;
  font-size: 0.75rem;
  color: #6f42c1;
  text-decoration: none;
  padding-top: 0.5rem;
  border-top: 1px solid #e9ecef;
  margin-top: 0.5rem;
}

.see-more-link:hover {
  text-decoration: underline;
}

/* Analysis Status */
.analysis-status-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.analysis-status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.8rem;
  padding: 0.25rem 0;
}

.analysis-status-item .badge {
  font-size: 0.65rem;
}

.font-monospace {
  font-size: 0.8rem;
}
</style>
