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

<template>
  <div
    class="root-header"
    :class="containerClass"
    role="button"
    tabindex="0"
    aria-label="Reset zoom"
    title="Reset zoom"
    @click="emit('reset')"
    @keydown.enter.space.prevent="emit('reset')"
  >
    <div class="root-bars">
      <div class="bar-row">
        <span class="bar-role">Baseline</span>
        <div class="bar-track">
          <div class="bar-fill baseline" :style="{ width: baselineWidthPct + '%' }"></div>
        </div>
        <span class="bar-val">{{ formattedBaseline }}</span>
      </div>
      <div class="bar-row">
        <span class="bar-role">Primary</span>
        <div class="bar-track">
          <div class="bar-fill primary" :style="{ width: primaryWidthPct + '%' }"></div>
        </div>
        <span class="bar-val">{{ formattedPrimary }}</span>
      </div>
    </div>

    <div v-if="delta" class="root-delta">
      <span class="label">{{ delta.label }}</span>
      <span class="val">
        {{ delta.value }}<span v-if="delta.pct" class="pct">{{ delta.pct }}</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Frame from '@/services/api/model/Frame';
import EventTypes from '@/services/EventTypes';
import FormattingService from '@shared/services/FormattingService';

interface Props {
  frame: Frame;
  eventType: string;
  useWeight: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'reset'): void;
}>();

const details = computed(() => props.frame.diffDetails!);

const deltaRaw = computed(() => (props.useWeight ? details.value.weight : details.value.samples));
const baselineRaw = computed(() =>
  props.useWeight ? details.value.secondaryWeight : details.value.secondarySamples
);
const primaryRaw = computed(() => baselineRaw.value + deltaRaw.value);
const percent = computed(() =>
  props.useWeight ? details.value.percentWeight : details.value.percentSamples
);

const maxRaw = computed(() => Math.max(primaryRaw.value, baselineRaw.value) || 1);
const baselineWidthPct = computed(() => (baselineRaw.value / maxRaw.value) * 100);
const primaryWidthPct = computed(() => (primaryRaw.value / maxRaw.value) * 100);

const formatValue = (value: number): string => {
  if (props.useWeight) {
    if (EventTypes.isAllocationEventType(props.eventType)) {
      return FormattingService.formatBytes(value);
    }
    if (EventTypes.isBlockingEventType(props.eventType)) {
      return FormattingService.formatDuration2Units(value);
    }
    return value.toLocaleString();
  }
  return value.toLocaleString();
};

const formattedBaseline = computed(() =>
  baselineRaw.value === 0 ? '—' : formatValue(baselineRaw.value)
);
const formattedPrimary = computed(() =>
  primaryRaw.value === 0 ? '—' : formatValue(primaryRaw.value)
);

interface Delta {
  label: string;
  value: string;
  pct: string | null;
  kind: 'improved' | 'regressed';
}

const delta = computed<Delta | null>(() => {
  const isAdded = baselineRaw.value === 0 && primaryRaw.value > 0;
  const isRemoved = primaryRaw.value === 0 && baselineRaw.value > 0;

  if (isAdded) {
    return {
      label: 'NEW',
      value: `+${formatValue(primaryRaw.value)}`,
      pct: null,
      kind: 'regressed'
    };
  }
  if (isRemoved) {
    return {
      label: 'REMOVED',
      value: `−${formatValue(baselineRaw.value)}`,
      pct: null,
      kind: 'improved'
    };
  }
  if (deltaRaw.value === 0) {
    return null;
  }
  const improved = deltaRaw.value < 0;
  const sign = deltaRaw.value > 0 ? '+' : '−';
  const pctSign = percent.value > 0 ? '+' : '−';
  const pctText = Number.isFinite(percent.value)
    ? `${pctSign}${Math.abs(percent.value).toFixed(2)}%`
    : null;
  return {
    label: improved ? 'IMPROVED' : 'REGRESSED',
    value: `${sign}${formatValue(Math.abs(deltaRaw.value))}`,
    pct: pctText,
    kind: improved ? 'improved' : 'regressed'
  };
});

const containerClass = computed(() => (delta.value ? `is-${delta.value.kind}` : ''));
</script>

<style scoped>
.root-header {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 24px;
  align-items: center;
  padding: 8px 14px;
  background: var(--color-primary-light);
  border: none;
  border-radius: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  min-height: 44px;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.12s ease;
}

.root-header:hover {
  background: var(--color-primary-bg);
}

.root-header:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.root-bars {
  display: grid;
  grid-template-rows: auto auto;
  gap: 5px;
  min-width: 0;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.bar-role {
  width: 56px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 8px;
  background: var(--color-neutral-bg);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.bar-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 4px;
}

.bar-fill.baseline {
  background: linear-gradient(90deg, var(--color-slate-light), #94a3b8);
}

.bar-fill.primary {
  background: linear-gradient(90deg, var(--color-primary), var(--color-primary-hover));
}

.bar-val {
  min-width: 80px;
  text-align: right;
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.root-delta {
  text-align: right;
  font-variant-numeric: tabular-nums;
  min-width: 0;
}

.root-delta .label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.root-delta .val {
  display: block;
  font-size: 14px;
  font-weight: 800;
  margin-top: 1px;
}

.root-delta .pct {
  font-size: 11px;
  font-weight: 600;
  opacity: 0.85;
  margin-left: 5px;
}

.is-improved .root-delta .label,
.is-improved .root-delta .val,
.is-improved .root-delta .pct {
  color: var(--color-emerald);
}

.is-regressed .root-delta .label,
.is-regressed .root-delta .val,
.is-regressed .root-delta .pct {
  color: var(--color-danger);
}
</style>
