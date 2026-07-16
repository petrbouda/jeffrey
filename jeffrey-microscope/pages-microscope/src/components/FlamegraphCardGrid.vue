<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<template>
  <div class="flamegraph-grid">
    <!-- Execution Sample Events -->
    <FlamegraphCard
      v-for="(event, index) in displayExecutionSampleEvents"
      :key="'exec-' + index"
      title="Execution Samples"
      color="blue"
      icon="bi-sprint"
      :thread-mode-opt="isPrimary"
      :thread-mode-selected="false"
      :weight-desc="null"
      :weight-opt="false"
      :weight-selected="false"
      :weight-formatter="FormattingService.formatDuration2Units"
      :exclude-non-java-samples-opt="false"
      :exclude-non-java-samples-selected="false"
      :exclude-idle-samples-opt="false"
      :exclude-idle-samples-selected="false"
      :only-unsafe-allocation-samples-opt="false"
      :only-unsafe-allocation-samples-selected="false"
      :graph-mode="graphMode"
      :event="event"
      :enabled="event.primary.samples > 0"
      :route-name="routeName"
      :button-text="buttonText"
      :emit-view="emitView"
      @view="emit('view', $event)"
    />

    <!-- CPU-Time Sample Events -->
    <FlamegraphCard
      v-for="(event, index) in displayCpuTimeSampleEvents"
      :key="'cpu-' + index"
      title="CPU-Time Samples"
      color="blue"
      icon="bi-cpu"
      :thread-mode-opt="isPrimary"
      :thread-mode-selected="false"
      weight-desc="CPU Time"
      :weight-opt="true"
      :weight-selected="false"
      :weight-formatter="FormattingService.formatDuration2Units"
      :exclude-non-java-samples-opt="false"
      :exclude-non-java-samples-selected="false"
      :exclude-idle-samples-opt="false"
      :exclude-idle-samples-selected="false"
      :only-unsafe-allocation-samples-opt="false"
      :only-unsafe-allocation-samples-selected="false"
      :graph-mode="graphMode"
      :event="event"
      :enabled="event.primary.samples > 0"
      :route-name="routeName"
      :button-text="buttonText"
      :emit-view="emitView"
      @view="emit('view', $event)"
    />

    <!-- Method Trace Events -->
    <FlamegraphCard
      v-if="showMethodEvents"
      v-for="(event, index) in displayMethodTraceEvents"
      :key="'method-' + index"
      title="Method Traces"
      color="blue"
      icon="bi-sprint"
      :thread-mode-opt="isPrimary"
      :thread-mode-selected="false"
      weight-desc="Total Time"
      :weight-opt="isPrimary"
      :weight-selected="isPrimary"
      :weight-formatter="FormattingService.formatDuration2Units"
      :exclude-non-java-samples-opt="false"
      :exclude-non-java-samples-selected="false"
      :exclude-idle-samples-opt="false"
      :exclude-idle-samples-selected="false"
      :only-unsafe-allocation-samples-opt="false"
      :only-unsafe-allocation-samples-selected="false"
      :graph-mode="graphMode"
      :event="event"
      :enabled="event.primary.samples > 0"
      :route-name="routeName"
      :button-text="buttonText"
      :emit-view="emitView"
      @view="emit('view', $event)"
    />

    <!-- Wall-Clock Events -->
    <FlamegraphCard
      v-for="(event, index) in displayWallClockEvents"
      :key="'wall-' + index"
      title="Wall-Clock Samples"
      color="purple"
      icon="bi-alarm"
      :thread-mode-opt="isPrimary"
      :thread-mode-selected="true"
      :weight-desc="null"
      :weight-opt="false"
      :weight-selected="false"
      :weight-formatter="FormattingService.formatDuration2Units"
      :exclude-non-java-samples-opt="true"
      :exclude-non-java-samples-selected="true"
      :exclude-idle-samples-opt="true"
      :exclude-idle-samples-selected="true"
      :only-unsafe-allocation-samples-opt="false"
      :only-unsafe-allocation-samples-selected="false"
      :graph-mode="graphMode"
      :event="event"
      :enabled="event.primary.samples > 0"
      :route-name="routeName"
      :button-text="buttonText"
      :emit-view="emitView"
      @view="emit('view', $event)"
    />

    <!-- Object Allocation Events -->
    <FlamegraphCard
      v-for="(event, index) in displayObjectAllocationEvents"
      :key="'alloc-' + index"
      title="Allocation Samples"
      color="green"
      icon="bi-memory"
      :thread-mode-opt="isPrimary"
      :thread-mode-selected="false"
      weight-desc="Total Allocation"
      :weight-opt="true"
      :weight-selected="true"
      :weight-formatter="FormattingService.formatBytes"
      :exclude-non-java-samples-opt="false"
      :exclude-non-java-samples-selected="false"
      :exclude-idle-samples-opt="false"
      :exclude-idle-samples-selected="false"
      :only-unsafe-allocation-samples-opt="false"
      :only-unsafe-allocation-samples-selected="false"
      :graph-mode="graphMode"
      :event="event"
      :enabled="event.primary.samples > 0"
      :route-name="routeName"
      :button-text="buttonText"
      :emit-view="emitView"
      @view="emit('view', $event)"
    />

    <!-- Native Allocation Events (Primary only) -->
    <FlamegraphCard
      v-if="showNativeEvents"
      v-for="(event, index) in displayNativeAllocationEvents"
      :key="'native-alloc-' + index"
      title="Native Allocation Samples"
      color="pink"
      icon="bi-memory"
      :thread-mode-opt="true"
      :thread-mode-selected="false"
      weight-desc="Total Allocation"
      :weight-opt="true"
      :weight-selected="true"
      :weight-formatter="FormattingService.formatBytes"
      :exclude-non-java-samples-opt="false"
      :exclude-non-java-samples-selected="false"
      :exclude-idle-samples-opt="false"
      :exclude-idle-samples-selected="false"
      :only-unsafe-allocation-samples-opt="true"
      :only-unsafe-allocation-samples-selected="true"
      :graph-mode="graphMode"
      :event="event"
      :enabled="event.primary.samples > 0"
      :route-name="routeName"
      :button-text="buttonText"
      :emit-view="emitView"
      @view="emit('view', $event)"
    />

    <!-- Native Leak Events (Primary only) -->
    <FlamegraphCard
      v-if="showNativeEvents"
      v-for="(event, index) in displayNativeLeakEvents"
      :key="'native-leak-' + index"
      title="Native Allocation Leaks"
      color="pink"
      icon="bi-memory"
      :thread-mode-opt="true"
      :thread-mode-selected="false"
      weight-desc="Total Allocation"
      :weight-opt="true"
      :weight-selected="true"
      :weight-formatter="FormattingService.formatBytes"
      :exclude-non-java-samples-opt="false"
      :exclude-non-java-samples-selected="false"
      :exclude-idle-samples-opt="false"
      :exclude-idle-samples-selected="false"
      :only-unsafe-allocation-samples-opt="true"
      :only-unsafe-allocation-samples-selected="true"
      :graph-mode="graphMode"
      :event="event"
      :enabled="event.primary.samples > 0"
      :route-name="routeName"
      :button-text="buttonText"
      :emit-view="emitView"
      @view="emit('view', $event)"
    />

    <!-- Blocking Events (Primary flamegraph only) -->
    <FlamegraphCard
      v-if="showBlockingEvents"
      v-for="(event, index) in displayBlockingEvents"
      :key="'blocking-' + index"
      :title="event.label"
      color="red"
      icon="bi-lock"
      :thread-mode-opt="true"
      :thread-mode-selected="false"
      :weight-opt="true"
      :weight-selected="true"
      weight-desc="Blocked Time"
      :weight-formatter="FormattingService.formatDuration2Units"
      :exclude-non-java-samples-opt="false"
      :exclude-non-java-samples-selected="false"
      :exclude-idle-samples-opt="false"
      :exclude-idle-samples-selected="false"
      :only-unsafe-allocation-samples-opt="false"
      :only-unsafe-allocation-samples-selected="false"
      :graph-mode="graphMode"
      :event="event"
      :enabled="event.primary.samples > 0"
      :route-name="routeName"
      :button-text="buttonText"
      :emit-view="emitView"
      @view="emit('view', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FlamegraphCard from '@/components/FlamegraphCard.vue';
import type { FlamegraphCardViewPayload } from '@/components/FlamegraphCard.vue';
import FormattingService from '@shared/services/FormattingService';
import GraphType from '@/services/flamegraphs/GraphType';
import EventSummary from '@/services/api/model/EventSummary';
import EventSummaryDetail from '@/services/api/model/EventSummaryDetail';

interface Props {
  graphMode: string;
  executionSampleEvents: EventSummary[];
  cpuTimeSampleEvents?: EventSummary[];
  methodTraceEvents: EventSummary[];
  objectAllocationEvents: EventSummary[];
  wallClockEvents: EventSummary[];
  blockingEvents?: EventSummary[];
  nativeAllocationEvents?: EventSummary[];
  nativeLeakEvents?: EventSummary[];
  routeName?: string;
  buttonText?: string;
  emitView?: boolean;
  // Suppress categories for views that don't want them (default: shown). Inverted (hide*) on purpose:
  // Vue casts an absent boolean prop to false, so "default false = not hidden = shown" works correctly.
  // The span view passes these true so it stays focused on Execution / Wall-Clock / Allocation.
  hideMethod?: boolean;
  hideNative?: boolean;
  hideBlocking?: boolean;
  // Suppress the greyed "No data" placeholder cards for empty categories. JFR profiles keep them so
  // the full set of standard categories stays visible; non-JFR profiles (e.g. pprof, which only ever
  // carries a subset like Execution or Allocation) pass this true so empty JFR categories don't clutter
  // the grid with irrelevant placeholders.
  suppressEmptyPlaceholders?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  cpuTimeSampleEvents: () => [],
  blockingEvents: () => [],
  nativeAllocationEvents: () => [],
  nativeLeakEvents: () => [],
  routeName: 'flamegraph',
  buttonText: 'View Flamegraph',
  emitView: false
});

const emit = defineEmits<{
  view: [payload: FlamegraphCardViewPayload];
}>();

const isPrimary = computed(() => props.graphMode === GraphType.PRIMARY);

// Native/blocking shown for primary flamegraph mode (not subsecond), unless explicitly overridden.
const isFlamegraphRoute = computed(() => isPrimary.value && props.routeName === 'flamegraph');
const showMethodEvents = computed(() => !props.hideMethod);
const showNativeEvents = computed(() => !props.hideNative && isFlamegraphRoute.value);
const showBlockingEvents = computed(() => !props.hideBlocking && isFlamegraphRoute.value);

/**
 * Keep a card visible (disabled) when a category has no samples: if the real list is empty, render a
 * single zero-sample placeholder so the grid shows a greyed "No data" card instead of hiding it.
 */
function withPlaceholder(events: EventSummary[], code: string, label: string): EventSummary[] {
  if (events.length > 0) {
    return events;
  }
  if (props.suppressEmptyPlaceholders) {
    return [];
  }
  return [
    new EventSummary(
      code,
      label,
      new EventSummaryDetail(code, label, '', '', 0, 0, false, null),
      null
    )
  ];
}

const displayExecutionSampleEvents = computed(() =>
  withPlaceholder(props.executionSampleEvents, 'jdk.ExecutionSample', 'Execution')
);
const displayCpuTimeSampleEvents = computed(() =>
  withPlaceholder(props.cpuTimeSampleEvents, 'jdk.CPUTimeSample', 'CPU-Time')
);
const displayMethodTraceEvents = computed(() =>
  withPlaceholder(props.methodTraceEvents, 'jdk.MethodTrace', 'Method Trace')
);
const displayWallClockEvents = computed(() =>
  withPlaceholder(props.wallClockEvents, 'profiler.WallClockSample', 'Wall-Clock')
);
const displayObjectAllocationEvents = computed(() =>
  withPlaceholder(props.objectAllocationEvents, 'jdk.ObjectAllocationInNewTLAB', 'Allocation')
);
const displayNativeAllocationEvents = computed(() =>
  withPlaceholder(props.nativeAllocationEvents, 'profiler.Malloc', 'Native Allocation')
);
const displayNativeLeakEvents = computed(() =>
  withPlaceholder(props.nativeLeakEvents, 'jeffrey.NativeLeak', 'Native Allocation Leaks')
);
const displayBlockingEvents = computed(() =>
  withPlaceholder(props.blockingEvents, 'jdk.JavaMonitorEnter', 'Locks & Blocking')
);
</script>
