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
      v-for="(event, index) in executionSampleEvents"
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
      :enabled="true"
      :route-name="routeName"
      :button-text="buttonText"
    />

    <!-- Method Trace Events -->
    <FlamegraphCard
      v-for="(event, index) in methodTraceEvents"
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
      :enabled="true"
      :route-name="routeName"
      :button-text="buttonText"
    />

    <!-- Wall-Clock Events -->
    <FlamegraphCard
      v-for="(event, index) in wallClockEvents"
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
      :enabled="true"
      :route-name="routeName"
      :button-text="buttonText"
    />

    <!-- Object Allocation Events -->
    <FlamegraphCard
      v-for="(event, index) in objectAllocationEvents"
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
      :enabled="true"
      :route-name="routeName"
      :button-text="buttonText"
    />

    <!-- Native Allocation Events (Primary only) -->
    <FlamegraphCard
      v-if="showNativeEvents"
      v-for="(event, index) in nativeAllocationEvents"
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
      :enabled="true"
      :route-name="routeName"
      :button-text="buttonText"
    />

    <!-- Native Leak Events (Primary only) -->
    <FlamegraphCard
      v-if="showNativeEvents"
      v-for="(event, index) in nativeLeakEvents"
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
      :enabled="true"
      :route-name="routeName"
      :button-text="buttonText"
    />

    <!-- Blocking Events (Primary flamegraph only) -->
    <FlamegraphCard
      v-if="showBlockingEvents"
      v-for="(event, index) in blockingEvents"
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
      :enabled="true"
      :route-name="routeName"
      :button-text="buttonText"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FlamegraphCard from '@/components/FlamegraphCard.vue';
import FormattingService from '@/services/FormattingService';
import GraphType from '@/services/flamegraphs/GraphType';
import EventSummary from '@/services/flamegraphs/model/EventSummary';

interface Props {
  graphMode: string;
  executionSampleEvents: EventSummary[];
  methodTraceEvents: EventSummary[];
  objectAllocationEvents: EventSummary[];
  wallClockEvents: EventSummary[];
  blockingEvents?: EventSummary[];
  nativeAllocationEvents?: EventSummary[];
  nativeLeakEvents?: EventSummary[];
  routeName?: string;
  buttonText?: string;
}

const props = withDefaults(defineProps<Props>(), {
  blockingEvents: () => [],
  nativeAllocationEvents: () => [],
  nativeLeakEvents: () => [],
  routeName: 'flamegraph',
  buttonText: 'View Flamegraph'
});

const isPrimary = computed(() => props.graphMode === GraphType.PRIMARY);

// Show native events only for primary flamegraph mode (not subsecond)
const showNativeEvents = computed(() =>
  isPrimary.value && props.routeName === 'flamegraph'
);

// Show blocking events only for primary flamegraph mode (not subsecond)
const showBlockingEvents = computed(() =>
  isPrimary.value && props.routeName === 'flamegraph'
);
</script>
