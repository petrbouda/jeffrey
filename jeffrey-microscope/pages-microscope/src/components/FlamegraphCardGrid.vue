<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<template>
  <div class="flamegraph-grid">
    <FlamegraphCard
      v-for="panel in visiblePanels"
      :key="panel.section + '-' + panel.event.code"
      :title="panel.title"
      :color="panel.color"
      :icon="panel.icon"
      :show-type="panel.showType"
      :thread-mode-opt="panel.threadMode.applicable"
      :thread-mode-selected="panel.threadMode.defaultOn"
      :weight-opt="panel.weight.applicable"
      :weight-selected="panel.weight.defaultOn"
      :weight-desc="panel.weight.label"
      :weight-formatter="weightFormatter(panel)"
      :exclude-non-java-samples-opt="panel.excludeNonJava.applicable"
      :exclude-non-java-samples-selected="panel.excludeNonJava.defaultOn"
      :exclude-idle-samples-opt="panel.excludeIdle.applicable"
      :exclude-idle-samples-selected="panel.excludeIdle.defaultOn"
      :only-unsafe-allocation-samples-opt="panel.onlyUnsafe.applicable"
      :only-unsafe-allocation-samples-selected="panel.onlyUnsafe.defaultOn"
      :graph-mode="graphMode"
      :event="panel.event"
      :enabled="panel.event.primary.samples > 0"
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
import FlamegraphPanel from '@/services/api/model/FlamegraphPanel';

interface Props {
  graphMode: string;
  // The backend-produced, ordered card grid. Every panel is fully self-describing; the frontend does no
  // event-type inference — it only applies route-based show/hide off the panel's classification flags.
  panels: FlamegraphPanel[];
  routeName?: string;
  buttonText?: string;
  emitView?: boolean;
  // Suppress panels by role for views that don't want them (default: shown). Inverted (hide*) on purpose:
  // Vue casts an absent boolean prop to false, so "default false = not hidden = shown" works. The span
  // view passes these true so it stays focused on Execution / Wall-Clock / Allocation.
  hideMethod?: boolean;
  hideNative?: boolean;
  hideBlocking?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  routeName: 'flamegraph',
  buttonText: 'View Flamegraph',
  emitView: false
});

const emit = defineEmits<{
  view: [payload: FlamegraphCardViewPayload];
}>();

const isPrimary = computed(() => props.graphMode === GraphType.PRIMARY);
// Native/blocking cards belong to the primary flamegraph route only (not subsecond); method tracing is
// suppressed on views that pass hideMethod. All keyed on the panel's classification, never its code.
const isFlamegraphRoute = computed(() => isPrimary.value && props.routeName === 'flamegraph');

function isVisible(panel: FlamegraphPanel): boolean {
  if (panel.classification.method && props.hideMethod) {
    return false;
  }
  if (panel.classification.nativeMemory && (props.hideNative || !isFlamegraphRoute.value)) {
    return false;
  }
  if (panel.classification.blocking && (props.hideBlocking || !isFlamegraphRoute.value)) {
    return false;
  }
  return true;
}

const visiblePanels = computed(() => props.panels.filter(isVisible));

function weightFormatter(panel: FlamegraphPanel): (value: number) => string {
  if (panel.weight.kind === 'BYTES') {
    return FormattingService.formatBytes;
  }
  return FormattingService.formatDuration2Units;
}
</script>
