<template>
  <DistributionChartsPanel
    :title="title"
    :icon="icon"
    :embedded="embedded"
    left-title="Status Codes"
    right-title="HTTP Methods"
    :left-data="statusCodeChartData"
    :right-data="httpMethodChartData"
    tooltip-suffix="requests"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import DistributionChartsPanel from '@shared/components/DistributionChartsPanel.vue';
import { buildDonutData } from '@shared/services/DonutData';
import ChartColors from '@shared/services/ChartColors';

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
  if (code >= 200 && code < 300) {
    return ChartColors.chartColor('color-success');
  }
  if (code >= 300 && code < 400) {
    return ChartColors.chartColor('primary');
  }
  if (code >= 400 && code < 500) {
    return ChartColors.chartColor('color-warning');
  }
  if (code >= 500) {
    return ChartColors.chartColor('color-danger');
  }
  return ChartColors.chartColor('color-text-muted');
};

const httpMethodColor = (method: string): string => {
  switch (method.toUpperCase()) {
    case 'GET':
      return ChartColors.chartColor('primary');
    case 'POST':
      return ChartColors.chartColor('color-success');
    case 'PUT':
      return ChartColors.chartColor('color-warning');
    case 'DELETE':
      return ChartColors.chartColor('color-danger');
    case 'PATCH':
      return ChartColors.chartColor('color-text-muted');
    case 'OPTIONS':
      return ChartColors.chartColor('color-purple');
    default:
      return ChartColors.chartColor('color-text-muted');
  }
};

const statusCodeChartData = computed(() =>
  buildDonutData(
    props.statusCodes.map(s => ({
      label: s.code.toString(),
      value: s.count,
      color: statusCodeColor(s.code)
    })),
    props.totalRequests
  )
);

const httpMethodChartData = computed(() =>
  buildDonutData(
    props.methods.map(m => ({
      label: m.method,
      value: m.count,
      color: httpMethodColor(m.method)
    })),
    props.totalRequests
  )
);
</script>
