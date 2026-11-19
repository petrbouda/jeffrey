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

<!--
  A compact single-choice switcher: two or three joined buttons where exactly one is picked. Use it
  where the choice reshapes what is already on screen — which metric a chart ranks by, how a search
  is scoped — and the options are worth reading side by side rather than hidden in a dropdown.

  Reach for TabBar instead when the choice navigates between panels of content, and for
  MetricTileSwitch when each option carries a headline figure of its own.

  Selection is owned by the parent through v-model, the same contract as TabBar.
-->
<template>
  <div class="btn-group btn-group-sm segmented-switch" role="group" :aria-label="groupLabel">
    <button
      v-for="option in options"
      :key="option.id"
      type="button"
      class="btn segmented-option"
      :class="modelValue === option.id ? 'btn-primary' : 'btn-outline-primary'"
      :aria-pressed="modelValue === option.id"
      :title="option.title"
      :disabled="option.disabled"
      @click="select(option)"
    >
      <i v-if="option.icon" :class="['bi', `bi-${option.icon}`, 'segmented-icon']"></i>
      {{ option.label }}
    </button>
  </div>
</template>

<script setup lang="ts" generic="T extends string">
/** A single selectable option. `id` is what v-model carries. */
export interface SegmentedOption<T extends string> {
  /** Value written to v-model when this option is picked. */
  id: T;
  /** Visible text label. */
  label: string;
  /** Bootstrap icon name without the `bi-` prefix, e.g. `diagram-3`. */
  icon?: string;
  /** Tooltip spelling out what the option means. */
  title?: string;
  /** Greyed-out and not clickable. */
  disabled?: boolean;
}

defineProps<{
  options: SegmentedOption<T>[];
  /** Accessible name for the group, e.g. `Condition scope`. */
  groupLabel: string;
}>();

const modelValue = defineModel<T>({ required: true });

const select = (option: SegmentedOption<T>): void => {
  if (option.disabled) {
    return;
  }
  modelValue.value = option.id;
};
</script>

<style scoped>
/* Bootstrap's btn-group already carries the joining, radii and focus rings. Labels here are often
   short sentences rather than single words, so they must not wrap mid-option. */
.segmented-switch {
  flex: 0 0 auto;
}

.segmented-option {
  white-space: nowrap;
}

.segmented-icon {
  margin-right: var(--spacing-1);
}
</style>
