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
  <SlowestRowList
    :items="spans"
    :item-key="(_span: SlowestSpanRow, index: number) => index"
    :name="(span: SlowestSpanRow) => span.threadName || 'unknown'"
    :duration="(span: SlowestSpanRow) => span.durationNanos"
    icon="bi bi-cpu"
    empty-message="No spans for this filter."
    @row-click="(span: SlowestSpanRow) => emit('rowClick', span)"
  >
    <template #details="{ item }">
      <Badge
        v-if="item.tag"
        :value="item.tag"
        variant="primary"
        size="s"
        icon="bi bi-tag"
        :uppercase="false"
        class="span-tag-badge"
      />
      <DetailChip icon="bi bi-clock">
        {{ FormattingService.formatTimestamp(item.startEpochMillis) }}
      </DetailChip>
    </template>
  </SlowestRowList>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import DetailChip from '@shared/components/DetailChip.vue';
import SlowestRowList from '@shared/components/SlowestRowList.vue';
import type { SlowestSpanRow } from '@/services/api/model/span/SpanModels';

defineProps<{
  spans: SlowestSpanRow[];
}>();

const emit = defineEmits<{
  rowClick: [span: SlowestSpanRow];
}>();
</script>

<style scoped>
.span-tag-badge {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
