<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <MetricCardList
    :items="tags"
    :item-key="(stat) => stat.tag"
    :count="(stat) => stat.count"
    count-label="spans"
    :sort-options="sortOptions"
    initial-sort="totalNanos"
    @item-click="(stat) => $emit('tagClick', stat.tag)"
  >
    <template #name="{ item }">{{ item.tag || '(no tag)' }}</template>

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
import FormattingService from '@/services/FormattingService';
import Badge from '@/components/Badge.vue';
import MetricCardList from '@/components/common/MetricCardList.vue';
import type { MetricSortOption } from '@/components/common/MetricCardList.vue';
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
