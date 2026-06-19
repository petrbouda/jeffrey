<template>
  <MetricCardList
    :items="services"
    :item-key="(service) => service.service"
    :count="(service) => service.callCount"
    count-label="calls"
    :sort-options="sortOptions"
    initial-sort="maxResponseTime"
    @item-click="(service) => $emit('serviceClick', service.service)"
  >
    <template #name="{ item }">
      <MetricName :segments="parseQualifiedName(item.service)" :title="item.service" />
    </template>

    <template #metrics="{ item }">
      <Badge
        key-label="Max"
        :value="FormattingService.formatDuration2Units(item.maxResponseTime)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="P99"
        :value="FormattingService.formatDuration2Units(item.p99ResponseTime)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="P95"
        :value="FormattingService.formatDuration2Units(item.p95ResponseTime)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        v-if="item.avgRequestSize >= 0"
        key-label="Avg Req"
        :value="FormattingService.formatBytes(item.avgRequestSize)"
        variant="secondary"
        size="s"
        borderless
      />
      <Badge
        v-if="item.avgResponseSize >= 0"
        key-label="Avg Resp"
        :value="FormattingService.formatBytes(item.avgResponseSize)"
        variant="secondary"
        size="s"
        borderless
      />
    </template>

    <template #right="{ item }">
      <StatusBadge
        v-if="item.successRate < 1"
        :value="FormattingService.formatSuccessRate(item.successRate || 0)"
        label="success"
        :variant="item.successRate < 0.95 ? 'danger' : 'warn'"
      />
    </template>
  </MetricCardList>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import MetricCardList from '@/components/common/MetricCardList.vue';
import MetricName from '@/components/common/MetricName.vue';
import { parseQualifiedName } from '@/services/metricName';
import type { MetricSortOption } from '@/components/common/MetricCardList.vue';
import type { GrpcServiceInfo } from '@/services/api/ProfileGrpcClient';

interface Props {
  services: GrpcServiceInfo[];
  selectedService?: string | null;
}

withDefaults(defineProps<Props>(), {
  selectedService: null
});

defineEmits<{
  serviceClick: [service: string];
}>();

const sortOptions: MetricSortOption[] = [
  {
    key: 'maxResponseTime',
    label: 'MAX',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.maxResponseTime - a.maxResponseTime
  },
  {
    key: 'p95ResponseTime',
    label: 'P95',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.p95ResponseTime - a.p95ResponseTime
  },
  {
    key: 'callCount',
    label: 'Calls',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.callCount - a.callCount
  },
  {
    key: 'successRate',
    label: 'Success',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => a.successRate - b.successRate
  }
];

</script>
