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
