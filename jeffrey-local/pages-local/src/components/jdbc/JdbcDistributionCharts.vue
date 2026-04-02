<template>
  <DualPanel
    :title="title"
    :icon="icon"
    :embedded="embedded"
    left-title="Operation Types"
    :right-title="secondChartTitle"
  >
    <template #left>
      <DonutWithLegend
        :data="operationChartData"
        :tooltip-formatter="(val: number) => formatNumber(val) + ' invocations'"
      />
    </template>
    <template #right>
      <DonutWithLegend
        :data="secondDonutData"
        :tooltip-formatter="(val: number) => formatNumber(val) + ' invocations'"
      />
    </template>
  </DualPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import DualPanel from '@/components/DualPanel.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import type { DonutChartData } from '@/components/DonutWithLegend.vue';
import JdbcUtils from '@/services/api/model/JdbcUtils.ts';
import FormattingService from '@/services/FormattingService';

const defaultColors = [
  '#4285F4',
  '#EA4335',
  '#FBBC05',
  '#34A853',
  '#9C27B0',
  '#FF5722',
  '#00BCD4',
  '#795548'
];

interface Props {
  operations: { label: string; value: number }[];
  secondChartTitle: string;
  secondChartData: { label: string; value: number }[];
  total: number;
  title?: string;
  icon?: string;
  embedded?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: undefined,
  icon: undefined,
  embedded: false
});

const formatNumber = (num: number): string => {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
  return num.toString();
};

const operationChartData = computed<DonutChartData>(() => {
  const items = props.operations.map(op => ({
    label: JdbcUtils.cleanOperationName(op.label),
    value: op.value
  }));
  return {
    series: items.map(i => i.value),
    labels: items.map(i => i.label),
    colors: items.map((_, idx) => defaultColors[idx % defaultColors.length]),
    totalValue: FormattingService.formatNumber(props.total),
    legendItems: items.map((i, idx) => ({
      color: defaultColors[idx % defaultColors.length],
      label: i.label,
      value: FormattingService.formatNumber(i.value)
    }))
  };
});

const secondDonutData = computed<DonutChartData>(() => {
  const items = props.secondChartData;
  return {
    series: items.map(i => i.value),
    labels: items.map(i => i.label),
    colors: items.map((_, idx) => defaultColors[idx % defaultColors.length]),
    totalValue: FormattingService.formatNumber(props.total),
    legendItems: items.map((i, idx) => ({
      color: defaultColors[idx % defaultColors.length],
      label: i.label,
      value: FormattingService.formatNumber(i.value)
    }))
  };
});
</script>
