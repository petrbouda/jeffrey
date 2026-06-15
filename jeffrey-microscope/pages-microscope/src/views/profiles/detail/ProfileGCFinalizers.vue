<template>
  <LoadingState v-if="loading" message="Loading finalizer statistics..." />

  <ErrorState v-else-if="error" message="Failed to load finalizer statistics" />

  <div v-else>
    <PageHeader
      title="Finalizers"
      description="Per-class finalization statistics from jdk.FinalizerStatistics"
      icon="bi-hourglass-split"
    />

    <EmptyState
      v-if="!hasData"
      icon="bi-hourglass-split"
      title="No finalizer statistics"
      description="This recording contains no jdk.FinalizerStatistics events — no classes override finalize()."
    />

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <ChartDescription
        shows="Classes that override finalize(), ranked by peak pending finalizable objects"
        use-case="A class with a high or growing pending count signals a finalizer leak or slow finalizer — finalization is deprecated and a known stall / retained-memory source"
      />

      <DataTable>
        <template #toolbar>
          <TableToolbar v-model="classesView.query" search-placeholder="Filter classes...">
            <span class="toolbar-info">Finalizable classes</span>
            <template #filters>
              <Badge key-label="Total" :value="classesView.matchCount" variant="secondary" size="s" borderless />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th>Class</th>
            <th>Code Source</th>
            <th class="text-end">Peak Pending Objects</th>
            <th class="text-end">Finalizers Run</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(c, i) in classesView.visible" :key="i">
            <td>{{ c.className }}</td>
            <td class="code-source">{{ c.codeSource || '—' }}</td>
            <td class="text-end">{{ FormattingService.formatNumber(c.peakObjects) }}</td>
            <td class="text-end">{{ FormattingService.formatNumber(c.finalizersRun) }}</td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="classesView.visible.length"
            :match-count="classesView.matchCount"
            :total="classesView.total"
            :expanded="classesView.expanded"
            :page-size="classesView.pageSize"
            @toggle="classesView.toggle"
          />
        </template>
      </DataTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import Badge from '@/components/Badge.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import FormattingService from '@/services/FormattingService';
import { useTableView } from '@/composables/useTableView';
import type { FinalizersData } from '@/services/api/model/GCTablesModels';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<FinalizersData>();

const classesView = useTableView(() => data.value?.classes ?? [], {
  searchableText: c => c.className
});

const hasData = computed(() => (data.value?.header.classCount ?? 0) > 0);

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'hourglass-split',
      title: 'Finalizable Classes',
      value: FormattingService.formatNumber(h.classCount),
      variant: 'highlight' as const
    },
    {
      icon: 'collection',
      title: 'Pending Objects',
      value: FormattingService.formatNumber(h.totalPendingObjects),
      variant: h.totalPendingObjects > 0 ? ('warning' as const) : ('success' as const)
    },
    {
      icon: 'check2-circle',
      title: 'Finalizers Run',
      value: FormattingService.formatNumber(h.totalFinalizersRun),
      variant: 'info' as const
    }
  ];
});

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const client = new ProfileGCClient(route.params.profileId as string);
    data.value = await client.getFinalizers();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading finalizers:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(loadData);
</script>

<style scoped>
.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.code-source {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  word-break: break-all;
}
</style>
