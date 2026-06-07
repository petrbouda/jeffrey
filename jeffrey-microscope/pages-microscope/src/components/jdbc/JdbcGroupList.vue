<template>
  <MetricCardList
    :items="groups"
    :item-key="(group) => group.group"
    :count="(group) => group.count"
    count-label="executions"
    :sort-options="sortOptions"
    initial-sort="maxExecutionTime"
    @item-click="(group) => $emit('groupClick', group.group)"
  >
    <template #name="{ item }">{{ item.group }}</template>

    <template #metrics="{ item }">
      <Badge
        key-label="Max"
        :value="FormattingService.formatDuration2Units(item.maxExecutionTime)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="P99"
        :value="FormattingService.formatDuration2Units(item.p99ExecutionTime)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="P95"
        :value="FormattingService.formatDuration2Units(item.p95ExecutionTime)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="Rows"
        :value="FormattingService.formatNumber(item.totalRowsProcessed)"
        variant="secondary"
        size="s"
        borderless
      />
    </template>

    <template #right="{ item }">
      <StatusBadge v-if="item.errorCount > 0" :value="item.errorCount" label="errors" variant="danger" />
    </template>
  </MetricCardList>
</template>

<script setup lang="ts">
import JdbcGroup from '@/services/api/model/JdbcGroup.ts';
import FormattingService from '@/services/FormattingService.ts';
import Badge from '@/components/Badge.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import MetricCardList from '@/components/common/MetricCardList.vue';
import type { MetricSortOption } from '@/components/common/MetricCardList.vue';

interface Props {
  groups: JdbcGroup[];
  selectedGroup?: string | null;
}

withDefaults(defineProps<Props>(), {
  selectedGroup: null
});

defineEmits<{
  groupClick: [group: string];
}>();

const sortOptions: MetricSortOption[] = [
  { key: 'maxExecutionTime', label: 'MAX', compare: (a, b) => b.maxExecutionTime - a.maxExecutionTime },
  { key: 'p99ExecutionTime', label: 'P99', compare: (a, b) => b.p99ExecutionTime - a.p99ExecutionTime },
  { key: 'p95ExecutionTime', label: 'P95', compare: (a, b) => b.p95ExecutionTime - a.p95ExecutionTime },
  { key: 'errorCount', label: 'Errors', compare: (a, b) => b.errorCount - a.errorCount },
  { key: 'count', label: 'Executions', compare: (a, b) => b.count - a.count }
];
</script>
