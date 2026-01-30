<template>
  <LoadingState v-if="loading" message="Loading collection analysis..." />

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
      icon="collection"
      message="The heap dump needs to be initialized before you can view collection analysis. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <!-- Analysis Not Yet Run -->
  <div v-else-if="!analysisExists && !analysisRunning">
    <PageHeader
        title="Collection Analysis"
        description="Find over-allocated and empty collections"
        icon="bi-collection"
    />

    <div class="alert alert-warning d-flex align-items-center">
      <i class="bi bi-exclamation-triangle me-3 fs-4"></i>
      <div class="flex-grow-1">
        <h6 class="mb-1">Collection Analysis Not Available</h6>
        <p class="mb-2 small">The collection analysis was not found. This can happen if the heap dump was initialized before this feature was added. You can run the analysis now.</p>
        <button class="btn btn-primary btn-sm" @click="runAnalysis">
          <i class="bi bi-play-fill me-1"></i>
          Run Collection Analysis
        </button>
      </div>
    </div>
  </div>

  <!-- Analysis Running -->
  <div v-else-if="analysisRunning">
    <PageHeader
        title="Collection Analysis"
        description="Find over-allocated and empty collections"
        icon="bi-collection"
    />

    <div class="alert alert-info d-flex align-items-center">
      <div class="spinner-border spinner-border-sm me-3" role="status">
        <span class="visually-hidden">Running...</span>
      </div>
      <div>
        <h6 class="mb-1">Analyzing Collection Instances...</h6>
        <p class="mb-0 small">This may take a few moments depending on the heap dump size.</p>
      </div>
    </div>
  </div>

  <!-- Analysis Results -->
  <div v-else>
    <PageHeader
        title="Collection Analysis"
        description="Find over-allocated and empty collections"
        icon="bi-collection"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Tabbed Analysis Section -->
    <ChartSectionWithTabs
        icon="collection"
        :tabs="analysisTabs"
        :full-width="true"
        id-prefix="collection-"
        @tab-change="onTabChange"
    >
      <!-- Overview Tab -->
      <template #overview>
        <div class="row">
          <div class="col-md-6">
            <div class="card h-100">
              <div class="card-body">
                <h6 class="card-title">Fill Distribution</h6>
                <div ref="fillChartRef" style="height: 250px;"></div>
                <table class="table table-sm legend-table mb-0" v-if="report">
                  <tbody>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #EA4335;"></span></td>
                      <td>Empty (0%)</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.overallFillDistribution.empty) }}</td>
                    </tr>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #FBBC05;"></span></td>
                      <td>Low (1-25%)</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.overallFillDistribution.low) }}</td>
                    </tr>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #4285F4;"></span></td>
                      <td>Medium (26-75%)</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.overallFillDistribution.medium) }}</td>
                    </tr>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #34A853;"></span></td>
                      <td>High (76-99%)</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.overallFillDistribution.high) }}</td>
                    </tr>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #185ABC;"></span></td>
                      <td>Full (100%)</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.overallFillDistribution.full) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
          <div class="col-md-6">
            <div class="card h-100">
              <div class="card-body">
                <h6 class="card-title">Summary</h6>
                <div class="summary-table" v-if="report">
                  <table class="table table-sm mb-0">
                    <tbody>
                      <tr>
                        <td class="text-muted">Total Collections</td>
                        <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.totalCollections) }}</td>
                      </tr>
                      <tr>
                        <td class="text-muted">Empty Collections</td>
                        <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.totalEmptyCount) }}</td>
                      </tr>
                      <tr>
                        <td class="text-muted">Empty Ratio</td>
                        <td class="text-end font-monospace">{{ emptyRatio }}%</td>
                      </tr>
                      <tr>
                        <td class="text-muted">Wasted Memory</td>
                        <td class="text-end font-monospace">{{ FormattingService.formatBytes(report.totalWastedBytes) }}</td>
                      </tr>
                      <tr>
                        <td class="text-muted">Under-utilized (Empty + Low)</td>
                        <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.overallFillDistribution.empty + report.overallFillDistribution.low) }}</td>
                      </tr>
                      <tr>
                        <td class="text-muted">Well-utilized (High + Full)</td>
                        <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.overallFillDistribution.high + report.overallFillDistribution.full) }}</td>
                      </tr>
                      <tr>
                        <td class="text-muted">Collection Types</td>
                        <td class="text-end font-monospace">{{ report.byType.length }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- By Type Tab -->
      <template #by-type>
        <div v-if="report && report.byType.length > 0">
          <div class="filter-controls mb-3">
            <div class="row align-items-center">
              <div class="col-auto ms-auto">
                <small class="text-muted">Showing {{ report.byType.length }} collection types</small>
              </div>
            </div>
          </div>
          <div class="table-card">
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                <tr>
                  <th style="width: 50px;">#</th>
                  <SortableTableHeader
                      column="collectionType"
                      label="Collection Type"
                      :sort-column="typeSortColumn"
                      :sort-direction="typeSortDirection"
                      @sort="toggleTypeSort"
                  />
                  <SortableTableHeader
                      column="totalCount"
                      label="Count"
                      :sort-column="typeSortColumn"
                      :sort-direction="typeSortDirection"
                      align="end"
                      width="100px"
                      @sort="toggleTypeSort"
                  />
                  <SortableTableHeader
                      column="emptyCount"
                      label="Empty"
                      :sort-column="typeSortColumn"
                      :sort-direction="typeSortDirection"
                      align="end"
                      width="100px"
                      @sort="toggleTypeSort"
                  />
                  <SortableTableHeader
                      column="avgFillRatio"
                      label="Avg Fill %"
                      :sort-column="typeSortColumn"
                      :sort-direction="typeSortDirection"
                      align="end"
                      width="110px"
                      @sort="toggleTypeSort"
                  />
                  <SortableTableHeader
                      column="totalWastedBytes"
                      label="Wasted"
                      :sort-column="typeSortColumn"
                      :sort-direction="typeSortDirection"
                      align="end"
                      width="120px"
                      @sort="toggleTypeSort"
                  />
                  <th style="width: 180px;">% of Max</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="(entry, index) in sortedByType" :key="index">
                  <td class="text-muted">{{ index + 1 }}</td>
                  <td>
                    <div class="class-info">
                      <code class="class-name">{{ simpleClassName(entry.collectionType) }}</code>
                      <span class="package-name">{{ packageName(entry.collectionType) }}</span>
                    </div>
                  </td>
                  <td class="text-end font-monospace">{{ FormattingService.formatNumber(entry.totalCount) }}</td>
                  <td class="text-end font-monospace">{{ FormattingService.formatNumber(entry.emptyCount) }}</td>
                  <td class="text-end font-monospace">{{ (entry.avgFillRatio * 100).toFixed(1) }}%</td>
                  <td class="text-end font-monospace text-warning">{{ FormattingService.formatBytes(entry.totalWastedBytes) }}</td>
                  <td>
                    <div class="d-flex align-items-center gap-2">
                      <div class="progress flex-grow-1" style="height: 6px;">
                        <div
                            class="progress-bar bg-warning"
                            :style="{ width: getTypePercentage(entry) + '%' }"
                        ></div>
                      </div>
                      <small class="text-muted" style="min-width: 45px;">{{ getTypePercentage(entry).toFixed(1) }}%</small>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-collection fs-1 mb-3 d-block"></i>
          <p>No collection type data available.</p>
        </div>
      </template>

      <!-- How It Works Tab -->
      <template #how-it-works>
        <div class="about-container">
          <!-- Header Section -->
          <div class="about-header">
            <div class="about-header-icon">
              <i class="bi bi-question-circle"></i>
            </div>
            <div>
              <h5 class="mb-1">How Collection Analysis Works</h5>
              <p class="text-muted mb-0">Understanding collection memory allocation and fill ratios</p>
            </div>
          </div>

          <!-- Intro -->
          <div class="about-intro">
            <p>Java collections like <code>HashMap</code>, <code>ArrayList</code>, and <code>HashSet</code> pre-allocate internal arrays
            to store elements. When a collection holds fewer elements than its capacity, the unused slots represent wasted memory.
            This analysis inspects the heap to find over-allocated and empty collections.</p>
          </div>

          <!-- Key Concepts -->
          <h6 class="section-title">
            <i class="bi bi-book me-2"></i>
            Key Concepts
          </h6>

          <div class="feature-grid">
            <div class="feature-card">
              <div class="feature-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
                <i class="bi bi-rulers"></i>
              </div>
              <div class="feature-content">
                <h6>Initial Capacity</h6>
                <p>Collections allocate an internal array when created. <code>ArrayList</code> defaults to 10 elements, <code>HashMap</code> defaults to 16 buckets. If the actual usage is much smaller, memory is wasted.</p>
              </div>
            </div>

            <div class="feature-card">
              <div class="feature-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
                <i class="bi bi-speedometer2"></i>
              </div>
              <div class="feature-content">
                <h6>Load Factor</h6>
                <p><code>HashMap</code> uses a load factor (default 0.75) to decide when to resize. This means a HashMap is typically only 75% full at most before it doubles in size.</p>
              </div>
            </div>

            <div class="feature-card">
              <div class="feature-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
                <i class="bi bi-arrow-up-right-circle"></i>
              </div>
              <div class="feature-content">
                <h6>Growth Strategy</h6>
                <p>When a collection runs out of space, it allocates a new, larger array (often 1.5x or 2x the previous size) and copies elements over. After growth, fill ratio drops significantly.</p>
              </div>
            </div>

            <div class="feature-card">
              <div class="feature-icon" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);">
                <i class="bi bi-pie-chart"></i>
              </div>
              <div class="feature-content">
                <h6>Fill Ratio</h6>
                <p>The fill ratio is <code>size / capacity</code>. A ratio of 0% means the collection is empty but still holds allocated memory. 100% means every slot is used.</p>
              </div>
            </div>
          </div>

          <!-- Fill Distribution Categories -->
          <h6 class="section-title">
            <i class="bi bi-bar-chart me-2"></i>
            Fill Distribution Categories
          </h6>

          <div class="flag-cards">
            <div class="flag-card">
              <div class="flag-header">
                <code class="flag-code">Empty (0%)</code>
                <span class="flag-badge">Highest Waste</span>
              </div>
              <div class="flag-body">
                <p>Collections that were created but never populated, or were cleared and not garbage collected. These hold allocated arrays with zero elements and are prime candidates for optimization.</p>
              </div>
            </div>

            <div class="flag-card">
              <div class="flag-header">
                <code class="flag-code">Low (1-25%)</code>
                <span class="flag-badge">Significant Waste</span>
              </div>
              <div class="flag-body">
                <p>Collections using less than a quarter of their capacity. Often caused by over-estimated initial capacity or collections that once held more data but were partially cleared.</p>
              </div>
            </div>

            <div class="flag-card">
              <div class="flag-header">
                <code class="flag-code">Medium (26-75%) / High (76-99%) / Full (100%)</code>
                <span class="flag-badge">Acceptable</span>
              </div>
              <div class="flag-body">
                <p>Collections with reasonable utilization. Medium-fill collections are common due to growth strategies and load factors. High and full collections are efficiently using their allocated memory.</p>
              </div>
            </div>
          </div>

          <!-- Optimization Tips -->
          <h6 class="section-title">
            <i class="bi bi-lightning-charge me-2"></i>
            Optimization Tips
          </h6>

          <div class="benefits-list">
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span>Use <code>new ArrayList&lt;&gt;(expectedSize)</code> to set initial capacity when the size is known ahead of time</span>
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span>Use <code>new HashMap&lt;&gt;(expectedSize, 1.0f)</code> to avoid over-allocation when exact size is known</span>
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span>Replace empty singleton collections with <code>Collections.emptyList()</code> or <code>List.of()</code> to avoid allocation entirely</span>
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span>Call <code>trimToSize()</code> on ArrayList after final population to release unused capacity</span>
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span>Consider lazy initialization: only create collections when the first element is added</span>
            </div>
          </div>

          <!-- Note -->
          <div class="about-note">
            <div class="note-icon">
              <i class="bi bi-lightbulb-fill"></i>
            </div>
            <div class="note-content">
              <strong>Why are there so many empty collections?</strong>
              <p class="mb-0">Empty collections are common in real applications. Many frameworks and libraries eagerly initialize collections in constructors or field declarations that may never be populated. This is often the largest source of wasted collection memory and can be addressed with lazy initialization patterns.</p>
            </div>
          </div>
        </div>
      </template>
    </ChartSectionWithTabs>

  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ApexCharts from 'apexcharts';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import CollectionAnalysisReport, { CollectionStats } from '@/services/api/model/CollectionAnalysisReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const analysisExists = ref(false);
const analysisRunning = ref(false);
const report = ref<CollectionAnalysisReport | null>(null);
const activeTab = ref('overview');

// Sort state for by-type table
const typeSortColumn = ref('totalWastedBytes');
const typeSortDirection = ref<'asc' | 'desc'>('desc');

const fillChartRef = ref<HTMLElement | null>(null);
let fillChart: ApexCharts | null = null;

let client: HeapDumpClient;

const analysisTabs = [
  { id: 'overview', label: 'Overview', icon: 'pie-chart' },
  { id: 'by-type', label: 'By Type', icon: 'list-ul' },
  { id: 'how-it-works', label: 'How It Works', icon: 'info-circle' }
];

const emptyRatio = computed(() => {
  if (!report.value || report.value.totalCollections === 0) return '0.0';
  return ((report.value.totalEmptyCount / report.value.totalCollections) * 100).toFixed(1);
});

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'collection',
      title: 'Total Collections',
      value: FormattingService.formatNumber(report.value.totalCollections),
      variant: 'highlight' as const
    },
    {
      icon: 'x-circle',
      title: 'Empty Collections',
      value: FormattingService.formatNumber(report.value.totalEmptyCount),
      variant: 'warning' as const
    },
    {
      icon: 'hdd',
      title: 'Wasted Memory',
      value: FormattingService.formatBytes(report.value.totalWastedBytes),
      variant: 'danger' as const
    }
  ];
});

const maxTypeWasted = computed(() => {
  if (!report.value || report.value.byType.length === 0) return 0;
  return Math.max(...report.value.byType.map(e => e.totalWastedBytes));
});

// Sorted by-type entries
const sortedByType = computed(() => {
  if (!report.value) return [];
  const entries = [...report.value.byType];
  const direction = typeSortDirection.value === 'asc' ? 1 : -1;

  switch (typeSortColumn.value) {
    case 'collectionType':
      entries.sort((a, b) => direction * a.collectionType.localeCompare(b.collectionType));
      break;
    case 'totalCount':
      entries.sort((a, b) => direction * (a.totalCount - b.totalCount));
      break;
    case 'emptyCount':
      entries.sort((a, b) => direction * (a.emptyCount - b.emptyCount));
      break;
    case 'avgFillRatio':
      entries.sort((a, b) => direction * (a.avgFillRatio - b.avgFillRatio));
      break;
    case 'totalWastedBytes':
      entries.sort((a, b) => direction * (a.totalWastedBytes - b.totalWastedBytes));
      break;
  }
  return entries;
});

const toggleTypeSort = (column: string) => {
  if (typeSortColumn.value === column) {
    typeSortDirection.value = typeSortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    typeSortColumn.value = column;
    typeSortDirection.value = column === 'collectionType' ? 'asc' : 'desc';
  }
};

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
};

const packageName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(0, lastDot) : '';
};

const getTypePercentage = (entry: CollectionStats): number => {
  if (maxTypeWasted.value === 0) return 0;
  return (entry.totalWastedBytes / maxTypeWasted.value) * 100;
};

const onTabChange = (tabIndex: number, tab: { id: string; label: string; icon?: string }) => {
  activeTab.value = tab.id;
  if (tab.id === 'overview') {
    nextTick(() => {
      renderCharts();
    });
  }
};

const renderCharts = () => {
  if (!report.value) return;

  if (fillChartRef.value) {
    if (fillChart) {
      fillChart.destroy();
    }

    const dist = report.value.overallFillDistribution;
    const fillOptions = {
      chart: {
        type: 'donut',
        height: 250
      },
      series: [dist.empty, dist.low, dist.medium, dist.high, dist.full],
      labels: ['Empty (0%)', 'Low (1-25%)', 'Medium (26-75%)', 'High (76-99%)', 'Full (100%)'],
      colors: ['#EA4335', '#FBBC05', '#4285F4', '#34A853', '#185ABC'],
      legend: {
        show: false
      },
      dataLabels: {
        enabled: true,
        formatter: (val: number) => val.toFixed(1) + '%'
      },
      tooltip: {
        y: {
          formatter: (val: number) => FormattingService.formatNumber(val) + ' collections'
        }
      },
      plotOptions: {
        pie: {
          donut: {
            labels: {
              show: true,
              value: {
                formatter: (val: string) => FormattingService.formatNumber(Number(val))
              },
              total: {
                show: true,
                label: 'Total',
                formatter: () => FormattingService.formatNumber(report.value!.totalCollections)
              }
            }
          }
        }
      }
    };

    fillChart = new ApexCharts(fillChartRef.value, fillOptions);
    fillChart.render();
  }
};

const runAnalysis = async () => {
  try {
    analysisRunning.value = true;
    await client.runCollectionAnalysis();
    await loadAnalysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to run collection analysis';
  } finally {
    analysisRunning.value = false;
  }
};

const loadAnalysis = async () => {
  analysisExists.value = await client.collectionAnalysisExists();
  if (analysisExists.value) {
    report.value = await client.getCollectionAnalysis();
    nextTick(() => {
      setTimeout(() => {
        renderCharts();
      }, 100);
    });
  }
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

    await loadAnalysis();

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load collection analysis';
    console.error('Error loading collection analysis:', err);
  } finally {
    loading.value = false;
  }
};

watch(report, () => {
  if (report.value && activeTab.value === 'overview') {
    nextTick(() => {
      setTimeout(() => {
        renderCharts();
      }, 100);
    });
  }
});

onMounted(() => {
  scrollToTop();
  loadData();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

.class-info {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.class-name {
  font-size: 0.8rem;
  font-weight: 600;
  background-color: transparent;
  color: #495057;
  white-space: nowrap;
}

.package-name {
  font-size: 0.8rem;
  color: #868e96;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.legend-table {
  margin-top: 0.5rem;
}

.legend-table td {
  padding: 0.25rem 0.5rem;
  border: none;
}

.legend-table td:first-child {
  width: 20px;
  padding-right: 0;
}

.legend-table td:last-child {
  text-align: right;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
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

.progress {
  background-color: #e9ecef;
}

.progress-bar {
  transition: width 0.3s ease;
}

.font-monospace {
  font-size: 0.8rem;
}

.card {
  border: 1px solid #dee2e6;
}

.card-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #495057;
  margin-bottom: 1rem;
}

.summary-table .table td {
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #f0f0f0;
}

.summary-table .table tr:last-child td {
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

.about-intro code {
  background-color: #e9ecef;
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  font-size: 0.85em;
  color: #d63384;
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

.flag-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.flag-card {
  background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%);
  border: 1px solid #e9ecef;
  border-radius: 8px;
  overflow: hidden;
}

.flag-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  background: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.flag-code {
  font-size: 0.85rem;
  color: #0d6efd;
  background: white;
  padding: 0.35rem 0.65rem;
  border-radius: 4px;
  border: 1px solid #dee2e6;
}

.flag-badge {
  font-size: 0.7rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: #6c757d;
  background: #e9ecef;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
}

.flag-body {
  padding: 1rem;
  font-size: 0.85rem;
  line-height: 1.6;
  color: #495057;
}

.flag-body code {
  background-color: #f1f3f4;
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.9em;
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

.benefit-item code {
  background-color: #f1f3f4;
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.85em;
  color: #d63384;
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

/* Darker warning colors */
.text-warning {
  color: #b8860b !important;
}

.bg-warning {
  background-color: #daa520 !important;
}
</style>
