<template>
  <LoadingState v-if="loading" message="Loading collection analysis..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">
          No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a
          heap dump and add it to the recording folder.
        </p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
    v-else-if="!cacheReady"
    icon="collection"
    message="The heap dump needs to be initialized before you can view collection analysis. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <HeapDumpNotInitialized
    v-else-if="!report"
    icon="collection"
    message="The collection analysis is not available for this heap dump. Re-initialize the heap dump from the Heap Dump Overview to populate it."
  />

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
    <TabBar v-model="activeTab" :tabs="analysisTabs" class="mb-3" />

    <!-- Overview Tab -->
    <div v-show="activeTab === 'overview'">
      <DualPanel v-if="report" left-title="Fill Distribution" right-title="Summary">
        <template #left>
          <DonutWithLegend
            :data="fillChartData"
            :tooltip-formatter="
              (val: number) => FormattingService.formatNumber(val) + ' collections'
            "
          />
        </template>
        <template #right>
          <SummaryTable :items="summaryItems" />
        </template>
      </DualPanel>
    </div>

    <!-- By Type Tab -->
    <div v-show="activeTab === 'by-type'">
      <div v-if="report && report.byType.length > 0">
        <DataTable>
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">Showing {{ report.byType.length }} collection types</span>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th style="width: 40px">#</th>
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
              <th style="width: 180px">% of Max</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(entry, index) in sortedByType" :key="index">
              <td class="text-muted">{{ index + 1 }}</td>
              <td>
                <ClassNameDisplay :class-name="entry.collectionType" />
              </td>
              <td class="text-end font-monospace">
                {{ FormattingService.formatNumber(entry.totalCount) }}
              </td>
              <td class="text-end font-monospace">
                {{ FormattingService.formatNumber(entry.emptyCount) }}
              </td>
              <td class="text-end font-monospace">{{ (entry.avgFillRatio * 100).toFixed(1) }}%</td>
              <td class="text-end font-monospace text-warning">
                {{ FormattingService.formatBytes(entry.totalWastedBytes) }}
              </td>
              <td>
                <div class="d-flex align-items-center gap-2">
                  <div class="progress flex-grow-1" style="height: 6px">
                    <div
                      class="progress-bar bg-warning"
                      :style="{ width: getTypePercentage(entry) + '%' }"
                    ></div>
                  </div>
                  <small class="text-muted" style="min-width: 45px"
                    >{{ getTypePercentage(entry).toFixed(1) }}%</small
                  >
                </div>
              </td>
            </tr>
          </tbody>
        </DataTable>
      </div>
      <div v-else class="text-center text-muted py-5">
        <i class="bi bi-collection fs-1 mb-3 d-block"></i>
        <p>No collection type data available.</p>
      </div>
    </div>

    <!-- Waste by Class Tab -->
    <div v-show="activeTab === 'waste-by-class'">
      <div v-if="report && report.wasteByClass && report.wasteByClass.length > 0">
        <DataTable>
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info"
                >Showing {{ report.wasteByClass.length }} owner classes</span
              >
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th style="width: 40px">#</th>
              <th style="width: 50%">Owner Class</th>
              <th class="text-end" style="width: 120px">Collections</th>
              <th class="text-end" style="width: 100px">Empty</th>
              <th class="text-end" style="width: 120px">Wasted</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(entry, index) in sortedWasteByClass" :key="index">
              <td class="text-muted">{{ index + 1 }}</td>
              <td>
                <div class="class-info">
                  <ClassNameDisplay :class-name="entry.ownerClassName" />
                  <div
                    class="detail-line"
                    v-if="Object.keys(entry.collectionTypeCounts).length > 0"
                  >
                    <template
                      v-for="(typeName, typeIndex) in Object.keys(entry.collectionTypeCounts).sort(
                        (a, b) => entry.collectionTypeCounts[b] - entry.collectionTypeCounts[a]
                      )"
                      :key="typeName"
                    >
                      <span v-if="typeIndex > 0" class="detail-sep">&middot;</span>
                      <span class="field-tag">{{ simpleClassName(typeName) }}</span>
                      <span class="text-muted">{{
                        FormattingService.formatNumber(entry.collectionTypeCounts[typeName])
                      }}</span>
                    </template>
                  </div>
                </div>
              </td>
              <td class="text-end font-monospace">
                {{ FormattingService.formatNumber(entry.collectionCount) }}
              </td>
              <td class="text-end font-monospace">
                {{ FormattingService.formatNumber(entry.emptyCount) }}
              </td>
              <td class="text-end font-monospace text-warning">
                {{ FormattingService.formatBytes(entry.wastedBytes) }}
              </td>
            </tr>
          </tbody>
        </DataTable>
      </div>
      <div v-else class="text-center text-muted py-5">
        <i class="bi bi-building fs-1 mb-3 d-block"></i>
        <p>No waste-by-class data available.</p>
      </div>
    </div>

    <!-- How It Works Tab -->
    <div v-show="activeTab === 'how-it-works'">
      <AboutPanel
        icon="bi-question-circle"
        title="How Collection Analysis Works"
        subtitle="Understanding collection memory allocation and fill ratios"
      >
        <AboutCallout variant="intro">
          <p>
            Java collections like <code>HashMap</code>, <code>ArrayList</code>, and
            <code>HashSet</code> pre-allocate internal arrays to store elements. When a collection
            holds fewer elements than its capacity, the unused slots represent wasted memory. This
            analysis inspects the heap to find over-allocated and empty collections.
          </p>
        </AboutCallout>

        <AboutSection icon="bi-book" title="Key Concepts">
          <FeatureGrid>
            <FeatureCard icon="bi-rulers" variant="purple" title="Initial Capacity">
              Collections allocate an internal array when created. <code>ArrayList</code> defaults to
              10 elements, <code>HashMap</code> defaults to 16 buckets. If the actual usage is much
              smaller, memory is wasted.
            </FeatureCard>
            <FeatureCard icon="bi-speedometer2" variant="danger" title="Load Factor">
              <code>HashMap</code> uses a load factor (default 0.75) to decide when to resize. This
              means a HashMap is typically only 75% full at most before it doubles in size.
            </FeatureCard>
            <FeatureCard icon="bi-arrow-up-right-circle" variant="info" title="Growth Strategy">
              When a collection runs out of space, it allocates a new, larger array (often 1.5x or 2x
              the previous size) and copies elements over. After growth, fill ratio drops
              significantly.
            </FeatureCard>
            <FeatureCard icon="bi-pie-chart" variant="success" title="Fill Ratio">
              The fill ratio is <code>size / capacity</code>. A ratio of 0% means the collection is
              empty but still holds allocated memory. 100% means every slot is used.
            </FeatureCard>
          </FeatureGrid>
        </AboutSection>

        <AboutSection icon="bi-bar-chart" title="Fill Distribution Categories">
          <div class="flag-cards">
          <div class="flag-card">
            <div class="flag-header">
              <code class="flag-code">Empty (0%)</code>
              <span class="flag-badge">Highest Waste</span>
            </div>
            <div class="flag-body">
              <p>
                Collections that were created but never populated, or were cleared and not garbage
                collected. These hold allocated arrays with zero elements and are prime candidates
                for optimization.
              </p>
            </div>
          </div>

          <div class="flag-card">
            <div class="flag-header">
              <code class="flag-code">Low (1-25%)</code>
              <span class="flag-badge">Significant Waste</span>
            </div>
            <div class="flag-body">
              <p>
                Collections using less than a quarter of their capacity. Often caused by
                over-estimated initial capacity or collections that once held more data but were
                partially cleared.
              </p>
            </div>
          </div>

          <div class="flag-card">
            <div class="flag-header">
              <code class="flag-code">Medium (26-75%) / High (76-99%) / Full (100%)</code>
              <span class="flag-badge">Acceptable</span>
            </div>
            <div class="flag-body">
              <p>
                Collections with reasonable utilization. Medium-fill collections are common due to
                growth strategies and load factors. High and full collections are efficiently using
                their allocated memory.
              </p>
            </div>
          </div>
        </div>
        </AboutSection>

        <AboutSection icon="bi-lightning-charge" title="Optimization Tips">
          <div class="benefits-list">
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span
                >Use <code>new ArrayList&lt;&gt;(expectedSize)</code> to set initial capacity when the
                size is known ahead of time</span
              >
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span
                >Use <code>new HashMap&lt;&gt;(expectedSize, 1.0f)</code> to avoid over-allocation
                when exact size is known</span
              >
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span
                >Replace empty singleton collections with <code>Collections.emptyList()</code> or
                <code>List.of()</code> to avoid allocation entirely</span
              >
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span
                >Call <code>trimToSize()</code> on ArrayList after final population to release unused
                capacity</span
              >
            </div>
            <div class="benefit-item">
              <i class="bi bi-check-circle-fill text-success"></i>
              <span
                >Consider lazy initialization: only create collections when the first element is
                added</span
              >
            </div>
          </div>
        </AboutSection>

        <AboutCallout variant="note" title="Why are there so many empty collections?" icon="bi-lightbulb-fill">
          <p>
            Empty collections are common in real applications. Many frameworks and libraries
            eagerly initialize collections in constructors or field declarations that may never be
            populated. This is often the largest source of wasted collection memory and can be
            addressed with lazy initialization patterns.
          </p>
        </AboutCallout>
      </AboutPanel>
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
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import TabBar from '@/components/TabBar.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import DualPanel from '@/components/DualPanel.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import type { DonutChartData } from '@/components/DonutWithLegend.vue';
import SummaryTable from '@/components/SummaryTable.vue';
import type { SummaryItem } from '@/components/SummaryTable.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import CollectionAnalysisReport, {
  CollectionStats
} from '@/services/api/model/CollectionAnalysisReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();

const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const report = ref<CollectionAnalysisReport | null>(null);

// Sort state for by-type table
const typeSortColumn = ref('totalWastedBytes');
const typeSortDirection = ref<'asc' | 'desc'>('desc');

let client: HeapDumpClient;

const analysisTabs = [
  { id: 'overview', label: 'Overview', icon: 'pie-chart' },
  { id: 'by-type', label: 'By Type', icon: 'list-ul' },
  { id: 'waste-by-class', label: 'Waste by Class', icon: 'building' },
  { id: 'how-it-works', label: 'How It Works', icon: 'info-circle' }
];
const activeTab = ref(analysisTabs[0].id);

const fillChartLabels = [
  'Empty (0%)',
  'Low (1-25%)',
  'Medium (26-75%)',
  'High (76-99%)',
  'Full (100%)'
];
const fillChartColors = ['#EA4335', '#FBBC05', '#4285F4', '#34A853', '#185ABC'];

const fillChartData = computed<DonutChartData>(() => {
  if (!report.value) return { series: [], labels: [], colors: [], legendItems: [], totalValue: '' };
  const dist = report.value.overallFillDistribution;
  const series = [dist.empty, dist.low, dist.medium, dist.high, dist.full];
  return {
    series,
    labels: fillChartLabels,
    colors: fillChartColors,
    totalValue: FormattingService.formatNumber(report.value.totalCollections),
    legendItems: fillChartLabels.map((label, i) => ({
      color: fillChartColors[i],
      label,
      value: FormattingService.formatNumber(series[i])
    }))
  };
});

const summaryItems = computed<SummaryItem[]>(() => {
  if (!report.value) return [];
  const emptyRatio =
    report.value.totalCollections === 0
      ? '0.0'
      : ((report.value.totalEmptyCount / report.value.totalCollections) * 100).toFixed(1);
  return [
    {
      label: 'Total Collections',
      value: FormattingService.formatNumber(report.value.totalCollections)
    },
    {
      label: 'Empty Collections',
      value: FormattingService.formatNumber(report.value.totalEmptyCount)
    },
    { label: 'Empty Ratio', value: emptyRatio + '%' },
    { label: 'Wasted Memory', value: FormattingService.formatBytes(report.value.totalWastedBytes) },
    {
      label: 'Under-utilized (Empty + Low)',
      value: FormattingService.formatNumber(
        report.value.overallFillDistribution.empty + report.value.overallFillDistribution.low
      )
    },
    {
      label: 'Well-utilized (High + Full)',
      value: FormattingService.formatNumber(
        report.value.overallFillDistribution.high + report.value.overallFillDistribution.full
      )
    },
    { label: 'Collection Types', value: String(report.value.byType.length) }
  ];
});

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'collection',
      title: 'Total Collections',
      value: FormattingService.formatNumber(report.value.totalCollections),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Empty',
          value: FormattingService.formatNumber(report.value.totalEmptyCount),
          color: '#FBBC05'
        }
      ]
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

// Sort state for waste-by-class table
const wasteSortColumn = ref('wastedBytes');
const wasteSortDirection = ref<'asc' | 'desc'>('desc');

const sortedWasteByClass = computed(() => {
  if (!report.value || !report.value.wasteByClass) return [];
  const entries = [...report.value.wasteByClass];
  const direction = wasteSortDirection.value === 'asc' ? 1 : -1;

  switch (wasteSortColumn.value) {
    case 'ownerClassName':
      entries.sort((a, b) => direction * a.ownerClassName.localeCompare(b.ownerClassName));
      break;
    case 'collectionCount':
      entries.sort((a, b) => direction * (a.collectionCount - b.collectionCount));
      break;
    case 'emptyCount':
      entries.sort((a, b) => direction * (a.emptyCount - b.emptyCount));
      break;
    case 'wastedBytes':
      entries.sort((a, b) => direction * (a.wastedBytes - b.wastedBytes));
      break;
  }
  return entries;
});

const toggleWasteSort = (column: string) => {
  if (wasteSortColumn.value === column) {
    wasteSortDirection.value = wasteSortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    wasteSortColumn.value = column;
    wasteSortDirection.value = column === 'ownerClassName' ? 'asc' : 'desc';
  }
};

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
};

const getTypePercentage = (entry: CollectionStats): number => {
  if (maxTypeWasted.value === 0) return 0;
  return (entry.totalWastedBytes / maxTypeWasted.value) * 100;
};

const loadAnalysis = async () => {
  report.value = await client.getCollectionAnalysis();
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
  flex-direction: column;
  gap: 0.15rem;
}

.detail-line {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  font-size: 0.75rem;
  margin-top: 1px;
}

.detail-sep {
  color: var(--color-text-light);
  user-select: none;
}

.field-tag {
  color: var(--color-purple);
  font-style: italic;
}

.toolbar-info {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.progress {
  background-color: var(--color-border);
}

.progress-bar {
  transition: width 0.3s ease;
}

.font-monospace {
  font-size: 0.8rem;
}

.flag-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.flag-card {
  background: linear-gradient(135deg, var(--color-light) 0%, var(--color-white) 100%);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.flag-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.flag-code {
  font-size: 0.85rem;
  color: var(--color-accent-blue);
  background: white;
  padding: 0.35rem 0.65rem;
  border-radius: 4px;
  border: 1px solid var(--color-border);
}

.flag-badge {
  font-size: 0.7rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: var(--color-text-muted);
  background: var(--color-border);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
}

.flag-body {
  padding: 1rem;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--color-text);
}

.flag-body code {
  background-color: var(--color-code-bg);
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.9em;
  color: var(--color-code-text);
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
  color: var(--color-text);
  padding: 0.5rem 0;
}

.benefit-item i {
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.benefit-item code {
  background-color: var(--color-code-bg);
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.85em;
  color: var(--color-code-text);
}

.collection-type-badge {
  font-size: 0.75rem;
  color: var(--color-text);
}

/* Darker warning colors */
.text-warning {
  color: var(--color-goldenrod) !important;
}

.bg-warning {
  background-color: var(--color-goldenrod-dark) !important;
}
</style>
