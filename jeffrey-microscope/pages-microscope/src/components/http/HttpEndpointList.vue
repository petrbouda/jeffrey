<template>
  <MetricCardList
    :items="endpoints"
    :item-key="(endpoint) => endpoint.uri"
    :count="(endpoint) => endpoint.requestCount"
    count-label="requests"
    :sort-options="sortOptions"
    initial-sort="maxResponseTime"
    @item-click="(endpoint) => $emit('endpointClick', endpoint.uri)"
  >
    <template #name="{ item }">
      <MetricName :segments="parseUriName(item.uri)" :title="item.uri" />
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
        v-if="item.totalBytesReceived >= 0"
        key-label="Recv"
        :value="FormattingService.formatBytes(item.totalBytesReceived)"
        variant="secondary"
        size="s"
        borderless
      />
      <Badge
        v-if="item.totalBytesSent >= 0"
        key-label="Sent"
        :value="FormattingService.formatBytes(item.totalBytesSent)"
        variant="secondary"
        size="s"
        borderless
      />
    </template>

    <template #right="{ item }">
      <StatusBadge v-if="item.count4xx > 0" :value="item.count4xx" label="4xx" variant="warn" />
      <StatusBadge v-if="item.count5xx > 0" :value="item.count5xx" label="5xx" variant="danger" />
    </template>
  </MetricCardList>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService.ts';
import Badge from '@shared/components/Badge.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import MetricCardList from '@/components/common/MetricCardList.vue';
import MetricName from '@/components/common/MetricName.vue';
import { parseUriName } from '@/services/metricName';
import type { MetricSortOption } from '@/components/common/MetricCardList.vue';

interface Endpoint {
  uri: string;
  requestCount: number;
  maxResponseTime: number;
  p99ResponseTime: number;
  p95ResponseTime: number;
  totalBytesReceived: number;
  totalBytesSent: number;
  count4xx: number;
  count5xx: number;
}

interface Props {
  endpoints: Endpoint[];
  selectedEndpoint?: string | null;
}

withDefaults(defineProps<Props>(), {
  selectedEndpoint: null
});

defineEmits<{
  endpointClick: [uri: string];
}>();

const sortOptions: MetricSortOption[] = [
  { key: 'maxResponseTime', label: 'MAX', compare: (a, b) => b.maxResponseTime - a.maxResponseTime },
  { key: 'p95ResponseTime', label: 'P95', compare: (a, b) => b.p95ResponseTime - a.p95ResponseTime },
  { key: 'count4xx', label: '4xx', compare: (a, b) => b.count4xx - a.count4xx },
  { key: 'count5xx', label: '5xx', compare: (a, b) => b.count5xx - a.count5xx },
  { key: 'requestCount', label: 'Requests', compare: (a, b) => b.requestCount - a.requestCount }
];

</script>
