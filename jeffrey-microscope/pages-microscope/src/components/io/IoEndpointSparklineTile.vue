<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<!--
  A single endpoint in the peer gallery: its name, its throughput shape and its share of total
  bytes. The sparkline is what makes the gallery worth having — the shape of every peer is visible
  before one is chosen, so an outlier announces itself without a click.
-->
<template>
  <button
    type="button"
    class="endpoint-tile"
    :class="{ 'is-selected': selected }"
    :aria-pressed="selected"
    :title="target"
    @click="emit('select', target)"
  >
    <span class="tile-name">{{ target }}</span>
    <SparklineChart
      :points="points"
      :color="sparklineColor"
      variant="area"
      :height="SPARKLINE_HEIGHT"
      :aria-label="`Throughput of ${target}`"
    />
    <span class="tile-figures">
      <span class="tile-bytes">{{ FormattingService.formatBytes(bytes) }}</span>
      <span class="tile-share">{{ FormattingService.formatPercentage(shareOfBytes) }}</span>
    </span>
  </button>
</template>

<script setup lang="ts">
import SparklineChart from '@shared/components/SparklineChart.vue';
import ChartColors from '@shared/services/ChartColors';
import FormattingService from '@shared/services/FormattingService';

interface Props {
  target: string;
  points: number[][];
  bytes: number;
  /** Fraction (0..1) of the profile's total I/O bytes that belongs to this endpoint. */
  shareOfBytes: number;
  selected: boolean;
}

defineProps<Props>();

const emit = defineEmits<{
  select: [target: string];
}>();

const SPARKLINE_HEIGHT = 34;
const sparklineColor = ChartColors.chartColor('color-violet');
</script>

<style scoped>
.endpoint-tile {
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 100%;
  padding: 10px 12px 8px;
  text-align: left;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.endpoint-tile:hover {
  border-color: var(--color-violet);
  box-shadow: var(--shadow-sm);
}

.endpoint-tile:focus-visible {
  outline: 2px solid var(--color-violet);
  outline-offset: 2px;
}

.endpoint-tile.is-selected {
  border-color: var(--color-violet);
  background: var(--color-violet-light-bg);
  box-shadow: var(--shadow-sm);
}

.tile-name {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.72rem;
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile-figures {
  display: flex;
  justify-content: space-between;
  font-size: 0.68rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.tile-bytes {
  font-weight: 600;
  color: var(--color-text);
}
</style>
