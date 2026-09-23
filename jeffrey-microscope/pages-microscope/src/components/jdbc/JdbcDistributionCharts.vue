<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <DistributionChartsPanel
    :title="title"
    :icon="icon"
    :embedded="embedded"
    left-title="Operation Types"
    :right-title="secondChartTitle"
    :left-data="operationChartData"
    :right-data="secondDonutData"
    :tooltip-format="(val: number) => formatNumber(val) + ' invocations'"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import DistributionChartsPanel from '@shared/components/DistributionChartsPanel.vue';
import { buildDonutData } from '@shared/services/DonutData';
import ChartColors from '@shared/services/ChartColors';
import JdbcUtils from '@/services/api/model/JdbcUtils.ts';

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
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M';
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K';
  }
  return num.toString();
};

const operationChartData = computed(() => {
  const items = props.operations.map(op => ({
    label: JdbcUtils.cleanOperationName(op.label),
    value: op.value
  }));
  const palette = ChartColors.chartPalette(Math.max(items.length, 1));
  return buildDonutData(
    items.map((item, idx) => ({ ...item, color: palette[idx % palette.length] })),
    props.total
  );
});

const secondDonutData = computed(() => {
  const palette = ChartColors.chartPalette(Math.max(props.secondChartData.length, 1));
  return buildDonutData(
    props.secondChartData.map((item, idx) => ({
      label: item.label,
      value: item.value,
      color: palette[idx % palette.length]
    })),
    props.total
  );
});
</script>
