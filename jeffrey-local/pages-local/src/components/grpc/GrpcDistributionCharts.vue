<template>
  <DualPanel
      :title="title"
      :icon="icon"
      :embedded="embedded"
      left-title="Status Codes"
      right-title="Services"
  >
    <template #left>
      <DonutWithLegend
          :data="statusCodeChartData"
          :tooltip-formatter="(val: number) => val + ' calls'"
      />
    </template>
    <template #right>
      <DonutWithLegend
          :data="serviceChartData"
          :tooltip-formatter="(val: number) => val + ' calls'"
      />
    </template>
  </DualPanel>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import DualPanel from '@/components/DualPanel.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import type {DonutChartData} from '@/components/DonutWithLegend.vue';
import type {GrpcStatusStats, GrpcServiceInfo} from '@/services/api/ProfileGrpcClient';
import FormattingService from '@/services/FormattingService';

const defaultColors = ['#4285F4', '#EA4335', '#FBBC05', '#34A853', '#9C27B0', '#FF5722', '#00BCD4', '#795548'];

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
  switch (label) {
    case 'OK': return '#5cb85c';
    case 'CANCELLED': return '#f0ad4e';
    case 'INVALID_ARGUMENT': case 'NOT_FOUND': case 'ALREADY_EXISTS':
    case 'PERMISSION_DENIED': case 'FAILED_PRECONDITION': case 'OUT_OF_RANGE':
    case 'UNAUTHENTICATED': return '#f0ad4e';
    case 'UNKNOWN': case 'DEADLINE_EXCEEDED': case 'RESOURCE_EXHAUSTED':
    case 'ABORTED': case 'UNIMPLEMENTED': case 'INTERNAL':
    case 'UNAVAILABLE': case 'DATA_LOSS': return '#d9534f';
    default: return '#6c757d';
  }
};

const shortServiceName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
};

const statusCodeChartData = computed<DonutChartData>(() => ({
  series: props.statusCodes.map(s => s.count),
  labels: props.statusCodes.map(s => s.status),
  colors: props.statusCodes.map(s => statusCodeColor(s.status)),
  totalValue: FormattingService.formatNumber(props.totalCalls),
  legendItems: props.statusCodes.map(s => ({
    color: statusCodeColor(s.status),
    label: s.status,
    value: FormattingService.formatNumber(s.count)
  }))
}));

const serviceChartData = computed<DonutChartData>(() => {
  const sorted = [...props.services].sort((a, b) => b.callCount - a.callCount);
  return {
    series: sorted.map(s => s.callCount),
    labels: sorted.map(s => shortServiceName(s.service)),
    colors: sorted.map((_, idx) => defaultColors[idx % defaultColors.length]),
    totalValue: FormattingService.formatNumber(props.totalCalls),
    legendItems: sorted.map((s, idx) => ({
      color: defaultColors[idx % defaultColors.length],
      label: shortServiceName(s.service),
      value: FormattingService.formatNumber(s.callCount)
    }))
  };
});
</script>
