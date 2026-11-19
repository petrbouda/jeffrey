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
  The softer sibling of SegmentedSwitch: the options share one track that fills its row, and
  a white thumb slides under the picked one. Use it where the switch leads a panel rather than sitting
  among toolbar buttons, and the labels are short enough to share the width equally.

  Takes the same SegmentedOption list and v-model contract as SegmentedSwitch.
-->
<template>
  <div
    class="pill-switch"
    role="group"
    :aria-label="groupLabel"
    :style="{ gridTemplateColumns: `repeat(${options.length}, 1fr)` }"
  >
    <span
      v-if="activeIndex >= 0"
      class="pill-thumb"
      :style="{
        width: `calc((100% - 2 * var(--spacing-1)) / ${options.length})`,
        transform: `translateX(${activeIndex * 100}%)`,
      }"
    ></span>
    <button
      v-for="option in options"
      :key="option.id"
      type="button"
      class="pill-option"
      :class="{ active: modelValue === option.id }"
      :aria-pressed="modelValue === option.id"
      :title="option.title"
      :disabled="option.disabled"
      @click="select(option)"
    >
      <i v-if="option.icon" :class="['bi', `bi-${option.icon}`]"></i>
      {{ option.label }}
    </button>
  </div>
</template>

<script setup lang="ts" generic="T extends string">
import { computed } from "vue";
import type { SegmentedOption } from "@shared/components/SegmentedSwitch.vue";

const props = defineProps<{
  options: SegmentedOption<T>[];
  /** Accessible name for the group, e.g. `Copy as`. */
  groupLabel: string;
}>();

const modelValue = defineModel<T>({ required: true });

const activeIndex = computed((): number =>
  props.options.findIndex((option) => option.id === modelValue.value),
);

const select = (option: SegmentedOption<T>): void => {
  if (option.disabled) {
    return;
  }
  modelValue.value = option.id;
};
</script>

<style scoped>
.pill-switch {
  position: relative;
  display: grid;
  padding: var(--spacing-1);
  background: var(--color-bg-hover-alt);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.pill-thumb {
  position: absolute;
  top: var(--spacing-1);
  bottom: var(--spacing-1);
  left: var(--spacing-1);
  background: var(--color-white);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.pill-option {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  font-size: 0.8rem;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
  white-space: nowrap;
  cursor: pointer;
  transition: color var(--transition-base);
}

.pill-option:hover:not(:disabled) {
  color: var(--color-dark);
}

.pill-option.active {
  color: var(--color-primary);
}

.pill-option:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pill-option:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .pill-thumb {
    transition: none;
  }
}
</style>
