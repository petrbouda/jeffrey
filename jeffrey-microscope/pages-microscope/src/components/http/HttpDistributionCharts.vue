<template>
  <DualPanel
    :title="title"
    :icon="icon"
    :embedded="embedded"
    left-title="Status Codes"
    right-title="HTTP Methods"
  >
    <template #left>
      <DonutWithLegend
        :data="statusCodeChartData"
        :tooltip-formatter="(val: number) => val + ' requests'"
      />
    </template>
    <template #right>
      <DonutWithLegend
        :data="httpMethodChartData"
        :tooltip-formatter="(val: number) => val + ' requests'"
      />
    </template>
  </DualPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import DualPanel from '@/components/DualPanel.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import type { DonutChartData } from '@/components/DonutWithLegend.vue';
import FormattingService from '@/services/FormattingService';

interface StatusCode {
  code: number;
  count: number;
}

interface HttpMethod {
  method: string;
  count: number;
}

interface Props {
  statusCodes: StatusCode[];
  methods: HttpMethod[];
  totalRequests: number;
  title?: string;
  icon?: string;
  embedded?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: undefined,
  icon: undefined,
  embedded: false
});

const statusCodeColor = (code: number): string => {
  if (code >= 200 && code < 300) return '#5cb85c';
  if (code >= 300 && code < 400) return '#5a9fd4';
  if (code >= 400 && code < 500) return '#f0ad4e';
  if (code >= 500) return '#d9534f';
  return '#6c757d';
};

const httpMethodColor = (method: string): string => {
  switch (method.toUpperCase()) {
    case 'GET':
      return '#5a9fd4';
    case 'POST':
      return '#5cb85c';
    case 'PUT':
      return '#f0ad4e';
    case 'DELETE':
      return '#d9534f';
    case 'PATCH':
      return '#6c757d';
    case 'OPTIONS':
      return '#9b59b6';
    default:
      return '#95a5a6';
  }
};

const statusCodeChartData = computed<DonutChartData>(() => ({
  series: props.statusCodes.map(s => s.count),
  labels: props.statusCodes.map(s => s.code.toString()),
  colors: props.statusCodes.map(s => statusCodeColor(s.code)),
  totalValue: FormattingService.formatNumber(props.totalRequests),
  legendItems: props.statusCodes.map(s => ({
    color: statusCodeColor(s.code),
    label: s.code.toString(),
    value: FormattingService.formatNumber(s.count)
  }))
}));

const httpMethodChartData = computed<DonutChartData>(() => ({
  series: props.methods.map(m => m.count),
  labels: props.methods.map(m => m.method),
  colors: props.methods.map(m => httpMethodColor(m.method)),
  totalValue: FormattingService.formatNumber(props.totalRequests),
  legendItems: props.methods.map(m => ({
    color: httpMethodColor(m.method),
    label: m.method,
    value: FormattingService.formatNumber(m.count)
  }))
}));
</script>
