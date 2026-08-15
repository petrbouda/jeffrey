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
    :server-ordered="serverOrdered"
    empty-message="No traces for this filter."
    @row-click="(trace: TraceRow) => emit('rowClick', trace)"
  >
    <template #details="{ item }">
      <!-- Not uppercased: the event type is a type name, and jeffrey.HttpServerExchange is how the
           recording, jfr print and JMC all spell it. -->
      <Badge
        :value="item.rootEventType"
        variant="secondary"
        size="s"
        borderless
        :uppercase="false"
      />
      <!-- Same treatment as the Trace Operations list: borderless and unadorned, so the pair reads
           identically wherever a trace root is described. -->
      <Badge :value="item.rootKind" :variant="spanKindVariant(item.rootKind)" size="s" borderless />
      <Badge
        v-if="item.errorCount > 0"
        :value="errorLabel(item.errorCount)"
        variant="danger"
        size="s"
        icon="bi bi-exclamation-triangle"
        :uppercase="false"
      />
      <DetailChip icon="bi bi-bounding-box">{{ item.spanCount }} spans</DetailChip>
      <DetailChip icon="bi bi-clock" :title="startedTitle(item.startMillisFromBeginning)">
        {{ startedAt(item.startMillisFromBeginning) }}
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
import { errorLabel, spanKindVariant } from '@/services/trace/traceLabels';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';

withDefaults(
  defineProps<{
    traces: TraceRow[];
    /** Profile-wide trace count, which the capped list cannot be summed into. */
    total?: number;
    note?: string;
    /** Draw the rows exactly as given, for a caller whose server ordered and paged them. */
    serverOrdered?: boolean;
  }>(),
  { total: undefined, note: undefined, serverOrdered: false }
);

const emit = defineEmits<{
  rowClick: [trace: TraceRow];
}>();

/**
 * Where the trace sits in the recording, which is the only reading of "when" that lines up with the
 * waterfall, the timeline and the flamegraphs. An absolute instant lines up with nothing.
 */
function startedAt(millisFromBeginning: number): string {
  return `${FormattingService.formatDurationInMillis2Units(millisFromBeginning)} in`;
}

function startedTitle(millisFromBeginning: number): string {
  return `${FormattingService.formatDurationInMillis2Units(millisFromBeginning)} into the recording`;
}

// A failure outranks the kind: it is the thing worth spotting from the gutter alone.
function accent(trace: TraceRow): SlowestRowAccent {
  if (trace.errorCount > 0) {
    return 'danger';
  }
  return spanKindVariant(trace.rootKind);
}

function tone(trace: TraceRow): SlowestRowTone {
  return trace.errorCount > 0 ? 'danger' : 'default';
}
</script>
