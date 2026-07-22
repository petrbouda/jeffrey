<template>
  <LoadingState v-if="loading" message="Comparing heap dumps..." />

  <div v-else-if="!baselineId" class="no-heap-dump">
    <PageHeader
      title="Heap Diff"
      description="Compare this profile's heap dump against a baseline profile"
      icon="bi-layers-half"
    />
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-layers-half me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Baseline Profile Selected</h6>
        <p class="mb-0 small">
          Select a secondary profile (the baseline heap dump) using the secondary-profile selector
          in the sidebar, then return to this page.
        </p>
      </div>
    </div>
  </div>

  <ErrorState v-else-if="error" :message="error" />

  <!-- Diff Results -->
  <div v-else-if="report">
    <PageHeader
      title="Heap Diff"
      :description="`Current heap vs baseline '${baselineName}' — what grew, appeared or shrank`"
      icon="bi-layers-half"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Category tabs -->
    <TabBar v-model="activeCategory" :tabs="categoryTabs" class="mb-3" />

    <DataTable v-if="categoryRows.length > 0">
      <template #toolbar>
        <TableToolbar v-model="diffView.query" search-placeholder="Filter classes...">
          <span class="toolbar-info">{{ diffView.matchCount }} classes</span>
        </TableToolbar>
      </template>
      <thead>
        <tr>
          <th style="width: 40px">#</th>
          <th>Class</th>
          <th style="width: 100px">Change</th>
          <th class="text-end" style="width: 130px">Baseline Count</th>
          <th class="text-end" style="width: 130px">Current Count</th>
          <th class="text-end" style="width: 110px">Δ Count</th>
          <th class="text-end" style="width: 130px">Δ Bytes</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(entry, index) in diffView.visible" :key="entry.className">
          <td class="text-muted">{{ index + 1 }}</td>
          <td>
            <ClassNameDisplay :class-name="entry.className" />
          </td>
          <td>
            <Badge
              :value="categoryOf(entry).label"
              :variant="categoryOf(entry).variant"
              size="s"
            />
          </td>
          <td class="text-end font-monospace">
            {{ FormattingService.formatNumber(entry.baselineCount) }}
          </td>
          <td class="text-end font-monospace">
            {{ FormattingService.formatNumber(entry.primaryCount) }}
          </td>
          <td class="text-end font-monospace" :class="deltaClass(entry.countDelta)">
            {{ signedNumber(entry.countDelta) }}
          </td>
          <td class="text-end font-monospace" :class="deltaClass(entry.bytesDelta)">
            {{ signedBytes(entry.bytesDelta) }}
          </td>
        </tr>
      </tbody>
      <template #footer>
        <TableShowMore
          :shown="diffView.visible.length"
          :match-count="diffView.matchCount"
          :total="diffView.total"
          :expanded="diffView.expanded"
          :page-size="diffView.pageSize"
          @toggle="diffView.toggle"
        />
      </template>
    </DataTable>
    <div v-else class="text-center text-muted py-5">
      <i class="bi bi-layers-half fs-1 mb-3 d-block"></i>
      <p>No class-level differences in this category.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import TabBar from '@shared/components/TabBar.vue';
import Badge from '@shared/components/Badge.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import { useTableView } from '@/composables/useTableView';
import HeapDumpDiffClient from '@/services/api/HeapDumpDiffClient';
import type HeapDumpDiffReport from '@/services/api/model/HeapDumpDiffReport';
import type { ClassDiffEntry } from '@/services/api/model/HeapDumpDiffReport';
import SecondaryProfileService from '@/services/SecondaryProfileService';
import FormattingService from '@shared/services/FormattingService';

type DiffCategory = 'all' | 'grown' | 'new' | 'shrunk' | 'removed';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const report = ref<HeapDumpDiffReport | null>(null);
const baselineId = ref<string | null>(null);
const baselineName = ref<string>('');

const categoryTabs = [
  { id: 'all', label: 'All Changes', icon: 'layers-half' },
  { id: 'grown', label: 'Grown', icon: 'arrow-up-right' },
  { id: 'new', label: 'New', icon: 'plus-circle' },
  { id: 'shrunk', label: 'Shrunk', icon: 'arrow-down-right' },
  { id: 'removed', label: 'Removed', icon: 'dash-circle' }
];
const activeCategory = ref<string>('all');

const categoryOf = (
  entry: ClassDiffEntry
): { id: DiffCategory; label: string; variant: 'danger' | 'success' | 'warning' | 'info' } => {
  if (entry.baselineCount === 0 && entry.primaryCount > 0) {
    return { id: 'new', label: 'New', variant: 'danger' };
  }
  if (entry.primaryCount === 0 && entry.baselineCount > 0) {
    return { id: 'removed', label: 'Removed', variant: 'success' };
  }
  if (entry.bytesDelta > 0 || (entry.bytesDelta === 0 && entry.countDelta > 0)) {
    return { id: 'grown', label: 'Grown', variant: 'warning' };
  }
  return { id: 'shrunk', label: 'Shrunk', variant: 'info' };
};

const categoryRows = computed<ClassDiffEntry[]>(() => {
  if (!report.value) {
    return [];
  }
  if (activeCategory.value === 'all') {
    return report.value.entries;
  }
  return report.value.entries.filter(e => categoryOf(e).id === activeCategory.value);
});

const diffView = useTableView<ClassDiffEntry>(() => categoryRows.value, {
  searchableText: row => row.className
});

const summaryMetrics = computed(() => {
  if (!report.value) {
    return [];
  }
  const grown = report.value.entries.filter(e => categoryOf(e).id === 'grown').length;
  const added = report.value.entries.filter(e => categoryOf(e).id === 'new').length;
  return [
    {
      icon: 'hdd-fill',
      title: 'Heap Size Change',
      value: signedBytes(report.value.shallowBytesDelta),
      variant: (report.value.shallowBytesDelta > 0 ? 'danger' : 'success') as 'danger' | 'success',
      breakdown: [
        {
          label: 'Baseline',
          value: FormattingService.formatBytes(report.value.baselineSummary.totalBytes)
        },
        {
          label: 'Current',
          value: FormattingService.formatBytes(report.value.primarySummary.totalBytes)
        }
      ]
    },
    {
      icon: 'collection',
      title: 'Instance Change',
      value: signedNumber(report.value.instanceCountDelta),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Baseline',
          value: FormattingService.formatNumber(report.value.baselineSummary.totalInstances)
        },
        {
          label: 'Current',
          value: FormattingService.formatNumber(report.value.primarySummary.totalInstances)
        }
      ]
    },
    {
      icon: 'arrow-up-right',
      title: 'Classes Grown',
      value: FormattingService.formatNumber(grown),
      variant: 'warning' as const
    },
    {
      icon: 'plus-circle',
      title: 'New Classes',
      value: FormattingService.formatNumber(added),
      variant: 'highlight' as const
    }
  ];
});

const signedNumber = (value: number): string => {
  const formatted = FormattingService.formatNumber(Math.abs(value));
  if (value > 0) {
    return '+' + formatted;
  }
  if (value < 0) {
    return '-' + formatted;
  }
  return formatted;
};

const signedBytes = (value: number): string => {
  const formatted = FormattingService.formatBytes(Math.abs(value));
  if (value > 0) {
    return '+' + formatted;
  }
  if (value < 0) {
    return '-' + formatted;
  }
  return formatted;
};

const deltaClass = (value: number): string => {
  if (value > 0) {
    return 'delta-positive';
  }
  if (value < 0) {
    return 'delta-negative';
  }
  return '';
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    report.value = null;

    baselineId.value = SecondaryProfileService.id();
    baselineName.value = SecondaryProfileService.name() ?? '';
    if (!baselineId.value) {
      loading.value = false;
      return;
    }

    const client = new HeapDumpDiffClient(profileId, baselineId.value);
    report.value = await client.getHistogramDiff();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to compare heap dumps';
  } finally {
    loading.value = false;
  }
};

const handleBaselineChanged = () => {
  loadData();
};

onMounted(() => {
  loadData();
  window.addEventListener(SecondaryProfileService.PROFILE_CHANGED, handleBaselineChanged);
});

onUnmounted(() => {
  window.removeEventListener(SecondaryProfileService.PROFILE_CHANGED, handleBaselineChanged);
});
</script>

<style scoped>
.no-heap-dump {
  padding: 0 0 2rem 0;
}

.toolbar-info {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.font-monospace {
  font-size: 0.8rem;
}

.delta-positive {
  color: var(--color-danger);
}

.delta-negative {
  color: var(--color-success);
}
</style>
