<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<template>
  <MetricCardList
    :items="tags"
    :item-key="stat => stat.tag"
    :count="stat => stat.count"
    count-label="spans"
    :sort-options="sortOptions"
    initial-sort="totalNanos"
    @item-click="stat => $emit('tagClick', stat.tag)"
  >
    <template #name="{ item }">
      <MetricName
        :segments="parseGroupedName(item.tag, '(no tag)')"
        :title="item.tag || '(no tag)'"
      />
    </template>

    <template #metrics="{ item }">
      <Badge
        key-label="Total"
        :value="FormattingService.formatDuration2Units(item.totalNanos)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="Avg"
        :value="FormattingService.formatDuration2Units(item.avgNanos)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="P95"
        :value="FormattingService.formatDuration2Units(item.p95Nanos)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="Max"
        :value="FormattingService.formatDuration2Units(item.maxNanos)"
        variant="secondary"
        size="s"
        borderless
      />
    </template>
  </MetricCardList>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import MetricCardList from '@shared/components/MetricCardList.vue';
import MetricName from '@/components/common/MetricName.vue';
import { parseGroupedName } from '@/services/metricName';
import type { MetricSortOption } from '@shared/components/MetricCardList.vue';
import type { SpanTagStat } from '@/services/api/model/span/SpanModels';

defineProps<{
  tags: SpanTagStat[];
}>();

defineEmits<{
  tagClick: [tag: string];
}>();

const sortOptions: MetricSortOption[] = [
  { key: 'totalNanos', label: 'Total', compare: (a, b) => b.totalNanos - a.totalNanos },
  { key: 'p95Nanos', label: 'P95', compare: (a, b) => b.p95Nanos - a.p95Nanos },
  { key: 'maxNanos', label: 'Max', compare: (a, b) => b.maxNanos - a.maxNanos },
  { key: 'count', label: 'Count', compare: (a, b) => b.count - a.count }
];
</script>
