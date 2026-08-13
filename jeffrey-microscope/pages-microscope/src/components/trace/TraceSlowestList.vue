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
    :items="traces"
    :item-key="(trace: TraceRow) => trace.traceId"
    :name="(trace: TraceRow) => trace.rootName"
    :duration="(trace: TraceRow) => trace.durationNanos"
    icon="bi bi-diagram-3"
    :accent="accent"
    :tone="tone"
    :total="total"
    :note="note"
    empty-message="No traces for this filter."
    @row-click="(trace: TraceRow) => emit('rowClick', trace)"
  >
    <template #details="{ item }">
      <Badge
        :value="item.rootKind"
        :variant="kindTone(item.rootKind)"
        size="s"
        icon="bi bi-diagram-2"
      />
      <Badge
        v-if="item.errorCount > 0"
        :value="errorLabel(item.errorCount)"
        variant="danger"
        size="s"
        icon="bi bi-exclamation-triangle"
        :uppercase="false"
      />
      <DetailChip icon="bi bi-bounding-box">{{ item.spanCount }} spans</DetailChip>
      <DetailChip icon="bi bi-clock">
        {{ FormattingService.formatTimestamp(item.startEpochMillis) }}
      </DetailChip>
      <!-- The id is the one thing here worth copying out, so it keeps the monospace treatment. -->
      <DetailChip icon="bi bi-hash" mono>{{ item.traceId }}</DetailChip>
    </template>
  </SlowestRowList>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import DetailChip from '@shared/components/DetailChip.vue';
import SlowestRowList from '@shared/components/SlowestRowList.vue';
import type { SlowestRowAccent, SlowestRowTone } from '@shared/types/ui';
import { errorLabel } from '@/services/trace/traceLabels';
import type { SpanKind, TraceRow } from '@/services/api/model/trace/TraceModels';

defineProps<{
  traces: TraceRow[];
  /** Profile-wide trace count, which the capped list cannot be summed into. */
  total?: number;
  note?: string;
}>();

const emit = defineEmits<{
  rowClick: [trace: TraceRow];
}>();

/** Assignable to both Badge's `Variant` and SlowestRowList's `SlowestRowAccent`. */
type KindTone = 'primary' | 'info' | 'secondary';

function kindTone(kind: SpanKind): KindTone {
  if (kind === 'SERVER') {
    return 'primary';
  }
  return kind === 'CLIENT' ? 'info' : 'secondary';
}

// A failure outranks the kind: it is the thing worth spotting from the gutter alone.
function accent(trace: TraceRow): SlowestRowAccent {
  if (trace.errorCount > 0) {
    return 'danger';
  }
  return kindTone(trace.rootKind);
}

function tone(trace: TraceRow): SlowestRowTone {
  return trace.errorCount > 0 ? 'danger' : 'default';
}
</script>
