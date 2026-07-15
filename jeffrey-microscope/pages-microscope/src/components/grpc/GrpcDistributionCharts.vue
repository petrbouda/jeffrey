<template>
  <DistributionChartsPanel
    :title="title"
    :icon="icon"
    :embedded="embedded"
    left-title="Status Codes"
    right-title="Services"
    :left-data="statusCodeChartData"
    :right-data="serviceChartData"
    tooltip-suffix="calls"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import DistributionChartsPanel from '@shared/components/DistributionChartsPanel.vue';
import { buildDonutData } from '@shared/services/DonutData';
import ChartColors from '@shared/services/ChartColors';
import type { GrpcStatusStats, GrpcServiceInfo } from '@/services/api/ProfileGrpcClient';

const CANCELLED_STATUSES = new Set([
  'CANCELLED',
  'INVALID_ARGUMENT',
  'NOT_FOUND',
  'ALREADY_EXISTS',
  'PERMISSION_DENIED',
  'FAILED_PRECONDITION',
  'OUT_OF_RANGE',
  'UNAUTHENTICATED'
]);

const FAILURE_STATUSES = new Set([
  'UNKNOWN',
  'DEADLINE_EXCEEDED',
  'RESOURCE_EXHAUSTED',
  'ABORTED',
  'UNIMPLEMENTED',
  'INTERNAL',
  'UNAVAILABLE',
  'DATA_LOSS'
]);

interface Props {
  statusCodes: GrpcStatusStats[];
  services: GrpcServiceInfo[];
  totalCalls: number;
  title?: string;
  icon?: string;
  embedded?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: undefined,
  icon: undefined,
  embedded: false
});

const statusCodeColor = (label: string): string => {
  if (label === 'OK') {
    return ChartColors.chartColor('color-success');
  }
  if (CANCELLED_STATUSES.has(label)) {
    return ChartColors.chartColor('color-warning');
  }
  if (FAILURE_STATUSES.has(label)) {
    return ChartColors.chartColor('color-danger');
  }
  return ChartColors.chartColor('color-text-muted');
};

const shortServiceName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
};

const statusCodeChartData = computed(() =>
  buildDonutData(
    props.statusCodes.map(s => ({
      label: s.status,
      value: s.count,
      color: statusCodeColor(s.status)
    })),
    props.totalCalls
  )
);

const serviceChartData = computed(() => {
  const sorted = [...props.services].sort((a, b) => b.callCount - a.callCount);
  const palette = ChartColors.chartPalette(Math.max(sorted.length, 1));
  return buildDonutData(
    sorted.map((s, idx) => ({
      label: shortServiceName(s.service),
      value: s.callCount,
      color: palette[idx % palette.length]
    })),
    props.totalCalls
  );
});
</script>
