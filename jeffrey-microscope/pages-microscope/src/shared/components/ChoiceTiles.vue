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
  A single-choice picker of two or three side-by-side tiles, each an icon, a title and a sentence
  saying what picking it means. Use it for a setup decision that deserves its explanation on
  screen — which library a command runs on, where data comes from — rather than for a compact
  view toggle.

  Reach for SegmentedSwitch instead when the options are short labels that reshape what is
  already on screen, and for TabBar when the choice navigates between panels.

  Selection is owned by the parent through v-model, the same contract as SegmentedSwitch.
-->
<template>
  <div class="choice-tiles" role="radiogroup" :aria-label="groupLabel">
    <button
      v-for="option in options"
      :key="option.id"
      type="button"
      role="radio"
      class="choice-tile"
      :class="{ selected: modelValue === option.id }"
      :aria-checked="modelValue === option.id"
      :disabled="option.disabled"
      @click="select(option)"
    >
      <span class="choice-tile-icon" :class="`tone-${option.tone ?? 'primary'}`">
        <i :class="['bi', `bi-${option.icon}`]"></i>
      </span>
      <span class="choice-tile-body">
        <span class="choice-tile-title-row">
          <span class="choice-tile-title">{{ option.label }}</span>
          <Badge v-if="option.badge" :value="option.badge" variant="success" size="xs" />
        </span>
        <span class="choice-tile-description">{{ option.description }}</span>
      </span>
      <span class="choice-tile-mark" aria-hidden="true">
        <i v-if="modelValue === option.id" class="bi bi-check-lg"></i>
      </span>
    </button>
  </div>
</template>

<script setup lang="ts" generic="T extends string">
import Badge from '@shared/components/Badge.vue';

/** Tint of a tile's icon square. */
export type ChoiceTileTone = 'primary' | 'warning' | 'info' | 'success';

/** A single selectable tile. `id` is what v-model carries. */
export interface ChoiceTileOption<T extends string> {
  /** Value written to v-model when this tile is picked. */
  id: T;
  /** Tile title. */
  label: string;
  /** One sentence saying what picking this tile means. */
  description: string;
  /** Bootstrap icon name without the `bi-` prefix, e.g. `box-seam`. */
  icon: string;
  /** Tint of the icon square; defaults to `primary`. */
  tone?: ChoiceTileTone;
  /** Short badge next to the title, e.g. `Recommended`. */
  badge?: string;
  /** Greyed-out and not clickable. */
  disabled?: boolean;
}

defineProps<{
  options: ChoiceTileOption<T>[];
  /** Accessible name for the group, e.g. `Async-profiler library`. */
  groupLabel: string;
}>();

const modelValue = defineModel<T>({ required: true });

const select = (option: ChoiceTileOption<T>): void => {
  if (option.disabled) {
    return;
  }
  modelValue.value = option.id;
};
</script>

<style scoped>
.choice-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--spacing-3);
}

.choice-tile {
  appearance: none;
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-3);
  padding: var(--spacing-4);
  font: inherit;
  text-align: left;
  background: var(--color-white);
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}

.choice-tile:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--color-primary) 40%, var(--color-border));
}

.choice-tile:focus {
  outline: none;
}

.choice-tile:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.choice-tile.selected,
.choice-tile.selected:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
}

.choice-tile:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.choice-tile-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
}

.tone-primary {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.tone-warning {
  background: var(--color-warning-light);
  color: var(--color-warning-hover);
}

.tone-info {
  background: var(--color-info-light);
  color: var(--color-info-hover);
}

.tone-success {
  background: var(--color-success-light);
  color: var(--color-success-hover);
}

.choice-tile-body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-1);
  flex-grow: 1;
  min-width: 0;
}

.choice-tile-title-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.choice-tile-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
}

.choice-tile-description {
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--color-text);
}

/* The radio mark: an empty ring, filled with a check once the tile is picked */
.choice-tile-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  box-sizing: border-box;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-circle);
  background: var(--color-white);
  color: var(--color-white);
  font-size: var(--font-size-sm);
}

.choice-tile.selected .choice-tile-mark {
  border-color: var(--color-primary);
  background: var(--color-primary);
}
</style>
