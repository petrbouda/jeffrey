<template>
  <LoadingState v-if="loading" message="Loading string analysis..." />

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
      icon="fonts"
      message="The heap dump needs to be initialized before you can view string analysis. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <!-- Analysis Not Yet Run -->
  <div v-else-if="!analysisExists && !analysisRunning">
    <PageHeader
        title="String Analysis"
        description="Analysis of string deduplication status and opportunities"
        icon="bi-fonts"
    />

    <div class="alert alert-warning d-flex align-items-center">
      <i class="bi bi-exclamation-triangle me-3 fs-4"></i>
      <div class="flex-grow-1">
        <h6 class="mb-1">String Analysis Not Available</h6>
        <p class="mb-2 small">The string analysis was not found. This can happen if the heap dump was initialized before this feature was added. You can run the analysis now.</p>
        <button class="btn btn-primary btn-sm" @click="runAnalysis">
          <i class="bi bi-play-fill me-1"></i>
          Run String Analysis
        </button>
      </div>
    </div>
  </div>

  <!-- Analysis Running -->
  <div v-else-if="analysisRunning">
    <PageHeader
        title="String Analysis"
        description="Analysis of string deduplication status and opportunities"
        icon="bi-fonts"
    />

    <div class="alert alert-info d-flex align-items-center">
      <div class="spinner-border spinner-border-sm me-3" role="status">
        <span class="visually-hidden">Running...</span>
      </div>
      <div>
        <h6 class="mb-1">Analyzing String Instances...</h6>
        <p class="mb-0 small">This may take a few moments depending on the heap dump size.</p>
      </div>
    </div>
  </div>

  <!-- Analysis Results -->
  <div v-else>
    <PageHeader
        title="String Analysis"
        description="Analysis of string deduplication status and opportunities"
        icon="bi-fonts"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Tabbed Analysis Section -->
    <ChartSectionWithTabs
        icon="fonts"
        :tabs="analysisTabs"
        :full-width="true"
        id-prefix="string-"
        @tab-change="onTabChange"
    >
      <!-- Overview Tab -->
      <template #overview>
        <div class="row">
          <div class="col-md-6">
            <div class="card h-100">
              <div class="card-body">
                <h6 class="card-title">Memory Status</h6>
                <div ref="memoryChartRef" style="height: 200px;"></div>
                <table class="table table-sm legend-table mb-0" v-if="report">
                  <tbody>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #FBBC05;"></span></td>
                      <td>Potential Savings</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatBytes(report.potentialSavings) }}</td>
                    </tr>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #4285F4;"></span></td>
                      <td>Unique + Shared</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatBytes(report.totalStringShallowSize - report.potentialSavings) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
          <div class="col-md-6">
            <div class="card h-100">
              <div class="card-body">
                <h6 class="card-title">String Array Sharing</h6>
                <div ref="arrayChartRef" style="height: 200px;"></div>
                <table class="table table-sm legend-table mb-0" v-if="report">
                  <tbody>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #34A853;"></span></td>
                      <td>Shared Arrays</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.sharedArrays) }}</td>
                    </tr>
                    <tr>
                      <td><span class="legend-dot" style="background-color: #4285F4;"></span></td>
                      <td>Unique Arrays</td>
                      <td class="text-end font-monospace">{{ FormattingService.formatNumber(report.uniqueArrays - report.sharedArrays) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- Deduplicated Tab -->
      <template #deduplicated>
        <div v-if="report && report.alreadyDeduplicated.length > 0">
          <div class="filter-controls mb-3">
            <div class="row align-items-center">
              <div class="col-auto ms-auto">
                <small class="text-muted">Showing {{ report.alreadyDeduplicated.length }} deduplicated strings</small>
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
                      column="content"
                      label="Content"
                      :sort-column="dedupSortColumn"
                      :sort-direction="dedupSortDirection"
                      @sort="toggleDedupSort"
                  />
                  <SortableTableHeader
                      column="count"
                      label="Count"
                      :sort-column="dedupSortColumn"
                      :sort-direction="dedupSortDirection"
                      align="end"
                      width="100px"
                      @sort="toggleDedupSort"
                  />
                  <SortableTableHeader
                      column="arraySize"
                      label="Array Size"
                      :sort-column="dedupSortColumn"
                      :sort-direction="dedupSortDirection"
                      align="end"
                      width="120px"
                      @sort="toggleDedupSort"
                  />
                  <SortableTableHeader
                      column="savings"
                      label="Saved"
                      :sort-column="dedupSortColumn"
                      :sort-direction="dedupSortDirection"
                      align="end"
                      width="120px"
                      @sort="toggleDedupSort"
                  />
                  <th style="width: 180px;">% of Max</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="(entry, index) in sortedDeduplicated" :key="index">
                  <td class="text-muted">{{ index + 1 }}</td>
                  <td>
                    <code class="string-content">{{ entry.content }}</code>
                  </td>
                  <td class="text-end font-monospace">{{ FormattingService.formatNumber(entry.count) }}</td>
                  <td class="text-end font-monospace">{{ FormattingService.formatBytes(entry.arraySize) }}</td>
                  <td class="text-end font-monospace text-success">{{ FormattingService.formatBytes(entry.savings) }}</td>
                  <td>
                    <div class="d-flex align-items-center gap-2">
                      <div class="progress flex-grow-1" style="height: 6px;">
                        <div
                            class="progress-bar bg-success"
                            :style="{ width: getDeduplicatedPercentage(entry) + '%' }"
                        ></div>
                      </div>
                      <small class="text-muted" style="min-width: 45px;">{{ getDeduplicatedPercentage(entry).toFixed(1) }}%</small>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-check-circle fs-1 mb-3 d-block"></i>
          <p>No deduplicated strings found in this heap dump.</p>
          <small>String deduplication is not active or no strings are being shared.</small>
        </div>
      </template>

      <!-- Opportunities Tab -->
      <template #opportunities>
        <div v-if="report && report.opportunities.length > 0">
          <div class="filter-controls mb-3">
            <div class="row align-items-center">
              <div class="col-auto ms-auto">
                <small class="text-muted">Showing {{ report.opportunities.length }} deduplication opportunities</small>
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
                      column="content"
                      label="Content"
                      :sort-column="oppSortColumn"
                      :sort-direction="oppSortDirection"
                      @sort="toggleOppSort"
                  />
                  <SortableTableHeader
                      column="count"
                      label="Count"
                      :sort-column="oppSortColumn"
                      :sort-direction="oppSortDirection"
                      align="end"
                      width="100px"
                      @sort="toggleOppSort"
                  />
                  <SortableTableHeader
                      column="arraySize"
                      label="Array Size"
                      :sort-column="oppSortColumn"
                      :sort-direction="oppSortDirection"
                      align="end"
                      width="120px"
                      @sort="toggleOppSort"
                  />
                  <SortableTableHeader
                      column="savings"
                      label="Potential"
                      :sort-column="oppSortColumn"
                      :sort-direction="oppSortDirection"
                      align="end"
                      width="120px"
                      @sort="toggleOppSort"
                  />
                  <th style="width: 180px;">% of Max</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="(entry, index) in sortedOpportunities" :key="index">
                  <td class="text-muted">{{ index + 1 }}</td>
                  <td>
                    <code class="string-content">{{ entry.content }}</code>
                  </td>
                  <td class="text-end font-monospace">{{ FormattingService.formatNumber(entry.count) }}</td>
                  <td class="text-end font-monospace">{{ FormattingService.formatBytes(entry.arraySize) }}</td>
                  <td class="text-end font-monospace text-warning">{{ FormattingService.formatBytes(entry.savings) }}</td>
                  <td>
                    <div class="d-flex align-items-center gap-2">
                      <div class="progress flex-grow-1" style="height: 6px;">
                        <div
                            class="progress-bar bg-warning"
                            :style="{ width: getOpportunityPercentage(entry) + '%' }"
                        ></div>
                      </div>
                      <small class="text-muted" style="min-width: 45px;">{{ getOpportunityPercentage(entry).toFixed(1) }}%</small>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-emoji-smile fs-1 mb-3 d-block"></i>
          <p>No deduplication opportunities found!</p>
          <small>All duplicate strings are already deduplicated or there are no duplicates.</small>
        </div>
      </template>

      <!-- JVM Configuration Tab -->
      <template #jvm-config>
        <div v-if="report?.jvmFlags && report.jvmFlags.length > 0">
          <div class="table-card">
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                  <tr>
                    <th>Flag</th>
                    <th>Value</th>
                    <th>Origin</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="flag in report.jvmFlags" :key="flag.name">
                    <td><code class="flag-name">{{ flag.name }}</code></td>
                    <td>
                      <span :class="getFlagValueClass(flag)">{{ flag.value }}</span>
                    </td>
                    <td class="text-muted">{{ flag.origin }}</td>
                    <td>{{ flag.description }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-gear fs-1 mb-3 d-block"></i>
          <p>No JVM flags information available.</p>
          <small>JVM flag data was not found in the JFR recording.</small>
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
              <h5 class="mb-1">How String Deduplication Works</h5>
              <p class="text-muted mb-0">Understanding when and how Java strings share memory</p>
            </div>
          </div>

          <!-- Intro -->
          <div class="about-intro">
            <p>In Java, each <code>String</code> object contains a reference to a <code>byte[]</code> array that holds the actual character data.
            When multiple String objects have identical content, they can share the same <code>byte[]</code> array to save memory.</p>
          </div>

          <!-- Ways Strings Can Be Shared -->
          <h6 class="section-title">
            <i class="bi bi-share me-2"></i>
            Ways Strings Can Be Shared
          </h6>

          <div class="feature-grid">
            <div class="feature-card">
              <div class="feature-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
                <i class="bi bi-file-earmark-code"></i>
              </div>
              <div class="feature-content">
                <h6>String Literals</h6>
                <p>All string literals (<code>"hello"</code>) are automatically interned in the constant pool. Same literals share the same object.</p>
              </div>
            </div>

            <div class="feature-card">
              <div class="feature-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
                <i class="bi bi-box-arrow-in-down"></i>
              </div>
              <div class="feature-content">
                <h6>String.intern()</h6>
                <p>Frameworks like Jackson, Gson, and Hibernate explicitly intern field names and entity names.</p>
              </div>
            </div>

            <div class="feature-card">
              <div class="feature-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
                <i class="bi bi-cpu"></i>
              </div>
              <div class="feature-content">
                <h6>JVM Internals</h6>
                <p>Class names, method names, and field names are stored in shared symbol tables by the JVM.</p>
              </div>
            </div>

            <div class="feature-card">
              <div class="feature-icon" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);">
                <i class="bi bi-collection"></i>
              </div>
              <div class="feature-content">
                <h6>Framework Interning</h6>
                <p>Libraries like Guava's Interner, Apache Commons, and Spring implement custom deduplication.</p>
              </div>
            </div>
          </div>

          <!-- JVM Flag Section -->
          <h6 class="section-title">
            <i class="bi bi-toggles me-2"></i>
            JVM String Deduplication
          </h6>

          <div class="flag-cards">
            <div class="flag-card">
              <div class="flag-header">
                <code class="flag-code">-XX:+UseStringDeduplication</code>
                <span class="flag-badge">GC Feature</span>
              </div>
              <div class="flag-body">
                <p>When enabled, the garbage collector automatically finds String objects with identical content and makes them share the same <code>byte[]</code> array during GC cycles.</p>
                <div class="gc-support">
                  <span class="gc-label">Supported:</span>
                  <span class="gc-tag">G1 (JDK 8u20+)</span>
                  <span class="gc-tag">ZGC (JDK 18+)</span>
                  <span class="gc-tag">Shenandoah (JDK 18+)</span>
                  <span class="gc-tag">Serial (JDK 18+)</span>
                  <span class="gc-tag">Parallel (JDK 18+)</span>
                </div>
              </div>
            </div>

            <div class="flag-card">
              <div class="flag-header">
                <code class="flag-code">-XX:StringDeduplicationAgeThreshold=3</code>
                <span class="flag-badge">Default: 3</span>
              </div>
              <div class="flag-body">
                <p>Specifies the number of garbage collection cycles a String must survive before becoming a candidate for deduplication.</p>
                <ul class="flag-details">
                  <li><strong>Lower value (1-2):</strong> More aggressive deduplication, processes strings sooner but may deduplicate short-lived strings unnecessarily</li>
                  <li><strong>Higher value (4+):</strong> Only long-lived strings are deduplicated, reducing overhead but potentially missing savings</li>
                </ul>
              </div>
            </div>
          </div>

          <!-- Benefits -->
          <h6 class="section-title">
            <i class="bi bi-lightning-charge me-2"></i>
            What It Helps With
          </h6>

          <div class="benefits-list">
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span>User data with duplicates (city names, status values, categories)</span>
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span>Runtime-generated strings that aren't explicitly interned</span>
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span>Temporary strings that survive long enough for GC to process</span>
            </div>
          </div>

          <!-- Note -->
          <div class="about-note">
            <div class="note-icon">
              <i class="bi bi-lightbulb-fill"></i>
            </div>
            <div class="note-content">
              <strong>Why is sharing high without the flag?</strong>
              <p class="mb-0">High sharing without <code>UseStringDeduplication</code> is normal. It comes from string literals, framework interning, and JVM metadata. The "Potential Savings" metric shows additional memory that could be saved by enabling the flag.</p>
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
import StringAnalysisReport, { JvmStringFlag } from '@/services/api/model/StringAnalysisReport';
import StringDeduplicationEntry from '@/services/api/model/StringDeduplicationEntry';
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
const report = ref<StringAnalysisReport | null>(null);
const activeTab = ref('overview');

// Sort state for deduplicated table
const dedupSortColumn = ref('savings');
const dedupSortDirection = ref<'asc' | 'desc'>('desc');

// Sort state for opportunities table
const oppSortColumn = ref('savings');
const oppSortDirection = ref<'asc' | 'desc'>('desc');

const memoryChartRef = ref<HTMLElement | null>(null);
const arrayChartRef = ref<HTMLElement | null>(null);
let memoryChart: ApexCharts | null = null;
let arrayChart: ApexCharts | null = null;

let client: HeapDumpClient;

const analysisTabs = [
  { id: 'overview', label: 'Overview', icon: 'pie-chart' },
  { id: 'deduplicated', label: 'Deduplicated', icon: 'check-circle' },
  { id: 'opportunities', label: 'Opportunities', icon: 'exclamation-triangle' },
  { id: 'jvm-config', label: 'JVM Configuration', icon: 'gear' },
  { id: 'about', label: 'How It Works', icon: 'info-circle' }
];

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'fonts',
      title: 'Total Strings',
      value: FormattingService.formatNumber(report.value.totalStrings),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Unique Arrays', value: FormattingService.formatNumber(report.value.uniqueArrays), color: '#4285F4' },
        { label: 'Shared Arrays', value: FormattingService.formatNumber(report.value.sharedArrays), color: '#34A853' }
      ]
    },
    {
      icon: 'check-circle',
      title: 'Memory Saved by Sharing',
      value: FormattingService.formatBytes(report.value.memorySavedByDedup),
      variant: 'success' as const
    },
    {
      icon: 'exclamation-triangle',
      title: 'Potential Savings',
      value: FormattingService.formatBytes(report.value.potentialSavings),
      variant: 'warning' as const
    },
    {
      icon: 'hdd',
      title: 'Total String Size',
      value: FormattingService.formatBytes(report.value.totalStringShallowSize),
      variant: 'info' as const
    }
  ];
});

const maxDeduplicatedSavings = computed(() => {
  if (!report.value || report.value.alreadyDeduplicated.length === 0) return 0;
  return Math.max(...report.value.alreadyDeduplicated.map(e => e.savings));
});

const maxOpportunitySavings = computed(() => {
  if (!report.value || report.value.opportunities.length === 0) return 0;
  return Math.max(...report.value.opportunities.map(e => e.savings));
});

// Sorted deduplicated entries
const sortedDeduplicated = computed(() => {
  if (!report.value) return [];
  const entries = [...report.value.alreadyDeduplicated];
  const direction = dedupSortDirection.value === 'asc' ? 1 : -1;

  switch (dedupSortColumn.value) {
    case 'content':
      entries.sort((a, b) => direction * a.content.localeCompare(b.content));
      break;
    case 'count':
      entries.sort((a, b) => direction * (a.count - b.count));
      break;
    case 'arraySize':
      entries.sort((a, b) => direction * (a.arraySize - b.arraySize));
      break;
    case 'savings':
      entries.sort((a, b) => direction * (a.savings - b.savings));
      break;
  }
  return entries;
});

// Sorted opportunities entries
const sortedOpportunities = computed(() => {
  if (!report.value) return [];
  const entries = [...report.value.opportunities];
  const direction = oppSortDirection.value === 'asc' ? 1 : -1;

  switch (oppSortColumn.value) {
    case 'content':
      entries.sort((a, b) => direction * a.content.localeCompare(b.content));
      break;
    case 'count':
      entries.sort((a, b) => direction * (a.count - b.count));
      break;
    case 'arraySize':
      entries.sort((a, b) => direction * (a.arraySize - b.arraySize));
      break;
    case 'savings':
      entries.sort((a, b) => direction * (a.savings - b.savings));
      break;
  }
  return entries;
});

const toggleDedupSort = (column: string) => {
  if (dedupSortColumn.value === column) {
    dedupSortDirection.value = dedupSortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    dedupSortColumn.value = column;
    dedupSortDirection.value = column === 'content' ? 'asc' : 'desc';
  }
};

const toggleOppSort = (column: string) => {
  if (oppSortColumn.value === column) {
    oppSortDirection.value = oppSortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    oppSortColumn.value = column;
    oppSortDirection.value = column === 'content' ? 'asc' : 'desc';
  }
};

const getDeduplicatedPercentage = (entry: StringDeduplicationEntry): number => {
  if (maxDeduplicatedSavings.value === 0) return 0;
  return (entry.savings / maxDeduplicatedSavings.value) * 100;
};

const getOpportunityPercentage = (entry: StringDeduplicationEntry): number => {
  if (maxOpportunitySavings.value === 0) return 0;
  return (entry.savings / maxOpportunitySavings.value) * 100;
};

const getFlagValueClass = (flag: JvmStringFlag): string => {
  if (flag.type === 'Boolean') {
    return flag.value === 'true' ? 'badge bg-success' : 'badge bg-secondary';
  }
  return 'font-monospace';
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

  // Memory Status Pie Chart
  if (memoryChartRef.value) {
    if (memoryChart) {
      memoryChart.destroy();
    }

    const memoryOptions = {
      chart: {
        type: 'donut',
        height: 250
      },
      series: [
        report.value.potentialSavings,
        report.value.totalStringShallowSize - report.value.potentialSavings
      ],
      labels: ['Potential Savings', 'Unique + Shared'],
      colors: ['#FBBC05', '#4285F4'],
      legend: {
        show: false
      },
      dataLabels: {
        enabled: true,
        formatter: (val: number) => val.toFixed(1) + '%'
      },
      tooltip: {
        y: {
          formatter: (val: number) => FormattingService.formatBytes(val)
        }
      },
      plotOptions: {
        pie: {
          donut: {
            labels: {
              show: true,
              value: {
                formatter: (val: string) => FormattingService.formatBytes(Number(val))
              },
              total: {
                show: true,
                label: 'Total',
                formatter: () => FormattingService.formatBytes(report.value!.totalStringShallowSize)
              }
            }
          }
        }
      }
    };

    memoryChart = new ApexCharts(memoryChartRef.value, memoryOptions);
    memoryChart.render();
  }

  // Array Sharing Pie Chart
  if (arrayChartRef.value) {
    if (arrayChart) {
      arrayChart.destroy();
    }

    const arrayOptions = {
      chart: {
        type: 'donut',
        height: 250
      },
      series: [
        report.value.sharedArrays,
        report.value.uniqueArrays - report.value.sharedArrays
      ],
      labels: ['Shared Arrays', 'Unique Arrays'],
      colors: ['#34A853', '#4285F4'],
      legend: {
        show: false
      },
      dataLabels: {
        enabled: true,
        formatter: (val: number) => val.toFixed(1) + '%'
      },
      tooltip: {
        y: {
          formatter: (val: number) => FormattingService.formatNumber(val)
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
                label: 'Total Arrays',
                formatter: () => FormattingService.formatNumber(report.value!.uniqueArrays)
              }
            }
          }
        }
      }
    };

    arrayChart = new ApexCharts(arrayChartRef.value, arrayOptions);
    arrayChart.render();
  }
};

const runAnalysis = async () => {
  try {
    analysisRunning.value = true;
    await client.runStringAnalysis(100);
    await loadAnalysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to run string analysis';
  } finally {
    analysisRunning.value = false;
  }
};

const loadAnalysis = async () => {
  analysisExists.value = await client.stringAnalysisExists();
  if (analysisExists.value) {
    report.value = await client.getStringAnalysis();
    // Wait for Vue to render the template and then for refs to be available
    nextTick(() => {
      // Additional delay to ensure DOM is fully rendered after v-else switches
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

    await loadAnalysis();

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load string analysis';
    console.error('Error loading string analysis:', err);
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

.string-content {
  font-size: 0.8rem;
  word-break: break-all;
  background-color: transparent;
  color: #495057;
  max-width: 400px;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flag-name {
  font-size: 0.8rem;
  background-color: transparent;
  color: #495057;
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

.flag-details {
  margin: 0.75rem 0 0 0;
  padding-left: 1.25rem;
  font-size: 0.8rem;
  color: #6c757d;
}

.flag-details li {
  margin-bottom: 0.35rem;
}

.flag-details li:last-child {
  margin-bottom: 0;
}

.flag-details strong {
  color: #495057;
}

.gc-support {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.gc-label {
  font-size: 0.8rem;
  font-weight: 500;
  color: #6c757d;
}

.gc-tag {
  font-size: 0.75rem;
  background: #e7f1ff;
  color: #0d6efd;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-weight: 500;
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

/* Darker warning colors for opportunities */
.text-warning {
  color: #b8860b !important;
}

.bg-warning {
  background-color: #daa520 !important;
}

/* Larger badges in JVM Configuration table */
.table .badge {
  font-size: 0.8rem;
  padding: 0.35rem 0.6rem;
}
</style>
