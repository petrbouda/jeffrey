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
      <DetailChip icon="bi bi-clock" :title="startedTitle(item.startEpochMillis)">
        {{ startedAt(item.startEpochMillis) }}
      </DetailChip>
    </template>
  </SlowestRowList>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import DetailChip from '@shared/components/DetailChip.vue';
import SlowestRowList from '@shared/components/SlowestRowList.vue';
import { profileStore } from '@/stores/profileStore';
import type { SlowestSpanRow } from '@/services/api/model/span/SpanModels';

defineProps<{
  spans: SlowestSpanRow[];
}>();

const emit = defineEmits<{
  rowClick: [span: SlowestSpanRow];
}>();

/**
 * Where the span sits in the recording, the same reading the waterfall and the timelines use. A span
 * carries only an absolute start, so it is rebased here; without the recording's bounds there is no
 * zero to count from, and the absolute instant is the honest answer.
 */
function startedAt(startEpochMillis: number): string {
  const window = profileStore.recordingWindow.value;
  if (window === null) {
    return FormattingService.formatTimestamp(startEpochMillis);
  }
  const offset = startEpochMillis - window.startEpochMillis;
  return `${FormattingService.formatDurationInMillis2Units(offset)} in`;
}

function startedTitle(startEpochMillis: number): string {
  const window = profileStore.recordingWindow.value;
  if (window === null) {
    return 'The recording did not report when it started';
  }
  const offset = startEpochMillis - window.startEpochMillis;
  return `${FormattingService.formatDurationInMillis2Units(offset)} into the recording`;
}
</script>

<style scoped>
.span-tag-badge {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
