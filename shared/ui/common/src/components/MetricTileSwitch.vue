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
  A row of metric tiles that doubles as a segmented switch: each tile carries its own headline
  figure, so the control answers "how much?" before it is clicked and the chart below only has to
  explain "when?". Use it where the choice is between two or three views of the same data — bytes
  versus operation counts, for instance — and each view has a total worth showing up front.

  Selection is owned by the parent through v-model, the same contract as TabBar.
-->
<template>
  <div class="metric-tile-switch" role="group" :aria-label="groupLabel">
    <button
      v-for="tile in tiles"
      :key="tile.id"
      type="button"
      class="metric-tile"
      :class="{ 'is-selected': modelValue === tile.id }"
      :aria-pressed="modelValue === tile.id"
      @click="select(tile)"
    >
      <span class="tile-icon">
        <i :class="['bi', `bi-${tile.icon}`]"></i>
      </span>
      <span class="tile-body">
        <span class="tile-caption">{{ tile.caption }}</span>
        <span class="tile-value">{{ tile.value }}</span>
        <span v-if="tile.subLabel" class="tile-sub">{{ tile.subLabel }}</span>
      </span>
    </button>
  </div>
</template>

<script setup lang="ts">
/** A single selectable tile. `id` is what v-model carries. */
export interface MetricTile {
  /** Value written to v-model when this tile is picked. */
  id: string;
  /** Bootstrap icon name without the `bi-` prefix, e.g. `arrow-down-up`. */
  icon: string;
  /** Short uppercase caption above the figure, e.g. `Throughput`. */
  caption: string;
  /** The headline figure — pre-formatted by the caller via FormattingService. */
  value: string | number;
  /** Optional qualifier under the figure, e.g. `read · 1.25 MiB written`. */
  subLabel?: string;
}

defineProps<{
  tiles: MetricTile[];
  groupLabel: string;
}>();

const modelValue = defineModel<string>({ required: true });

const select = (tile: MetricTile): void => {
  modelValue.value = tile.id;
};
</script>

<style scoped>
.metric-tile-switch {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--spacing-3);
}

.metric-tile {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  width: 100%;
  padding: var(--spacing-3) var(--spacing-4);
  text-align: left;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border-input);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.metric-tile:hover:not(.is-selected) {
  background: var(--color-bg-hover);
}

.metric-tile:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.metric-tile.is-selected {
  background: var(--color-primary-lighter);
  border-color: var(--color-primary-border-light);
  border-left-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.tile-icon {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-base);
  background: var(--color-code-bg);
  color: var(--color-text-muted);
  font-size: var(--font-size-base);
  transition:
    background var(--transition-fast),
    color var(--transition-fast);
}

.metric-tile.is-selected .tile-icon {
  background: var(--color-primary);
  color: var(--color-white);
}

.tile-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.tile-caption {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.tile-value {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-tight);
  color: var(--color-heading-dark);
  font-variant-numeric: tabular-nums;
}

.tile-sub {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
