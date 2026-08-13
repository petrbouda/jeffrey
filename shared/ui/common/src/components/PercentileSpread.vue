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
  <!--
    All rows share one rail, scaled by the caller, so a wide p50-to-p95 gap -- the signature of
    something that is usually fine and occasionally awful -- reads as a shape rather than as two
    numbers to compare.
  -->
  <div class="spread">
    <span class="rail"></span>
    <span class="range" :style="{ left: position(p50), width: band }"></span>
    <span class="tick tick-p50" :style="{ left: position(p50) }"></span>
    <span class="tick tick-p95" :style="{ left: position(p95) }"></span>
    <span class="tick tick-max" :style="{ left: position(max) }"></span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { bandWidthPercent, positionPercent } from '@shared/services/percentileSpread';

const props = defineProps<{
  p50: number;
  p95: number;
  max: number;
  /** The value the rail's full width represents, shared by every row being compared. */
  scale: number;
}>();

const band = computed(() => bandWidthPercent(props.p50, props.p95, props.scale) + '%');

function position(value: number): string {
  return positionPercent(value, props.scale) + '%';
}
</script>

<style scoped>
.spread {
  position: relative;
  height: 0.85rem;
  width: 100%;
}

.rail {
  position: absolute;
  inset: 0.35rem 0 auto 0;
  height: 0.16rem;
  background: var(--color-border);
  border-radius: var(--radius-xs);
}

.range {
  position: absolute;
  top: 0.28rem;
  height: 0.3rem;
  background: var(--flamegraph-color-blue);
  border-radius: var(--radius-xs);
}

.tick {
  position: absolute;
  top: 0.05rem;
  width: 2px;
  height: 0.75rem;
  border-radius: var(--radius-xs);
}

.tick-p50 {
  background: var(--color-primary);
}

.tick-p95 {
  background: var(--color-warning);
}

.tick-max {
  background: var(--color-danger);
}
</style>
