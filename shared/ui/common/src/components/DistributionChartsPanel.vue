<template>
  <DualPanel
    :title="title"
    :icon="icon"
    :embedded="embedded"
    :left-title="leftTitle"
    :right-title="rightTitle"
  >
    <template #left>
      <DonutWithLegend :data="leftData" :tooltip-formatter="tooltipFormatter" />
    </template>
    <template #right>
      <DonutWithLegend :data="rightData" :tooltip-formatter="tooltipFormatter" />
    </template>
  </DualPanel>
</template>

<script setup lang="ts">
import DualPanel from './DualPanel.vue';
import DonutWithLegend from './DonutWithLegend.vue';
import type { DonutChartData } from './DonutWithLegend.vue';

/**
 * Two donut distributions side by side (DualPanel + DonutWithLegend pair).
 * Shared by the HTTP/gRPC/JDBC distribution charts so the panel markup and
 * tooltip wiring live in one place; callers supply prepared DonutChartData.
 */
interface Props {
  leftTitle: string;
  rightTitle: string;
  leftData: DonutChartData;
  rightData: DonutChartData;
  tooltipSuffix?: string;
  tooltipFormat?: (value: number) => string;
  title?: string;
  icon?: string;
  embedded?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  tooltipSuffix: undefined,
  tooltipFormat: undefined,
  title: undefined,
  icon: undefined,
  embedded: false
});

const tooltipFormatter = (value: number): string => {
  if (props.tooltipFormat) {
    return props.tooltipFormat(value);
  }
  if (props.tooltipSuffix) {
    return `${value} ${props.tooltipSuffix}`;
  }
  return `${value}`;
};
</script>
