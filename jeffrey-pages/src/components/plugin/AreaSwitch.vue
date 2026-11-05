<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { AREA_LENSES, usePluginUsage, type AreaLens } from '@/composables/usePluginUsage';

// One of the plugin's two areas, with the agent that works it: JFR recordings (jfr-analyst) or
// heap dumps (heap-analyst). The shared skills and tools show under both.

defineProps<{ lens: AreaLens }>();
const emit = defineEmits<{ (e: 'change', lens: AreaLens): void }>();

interface LensOption {
  lens: AreaLens;
  label: string;
  detail: string;
  icon: string;
}

const ICONS: Record<AreaLens, string> = { jfr: 'bi-fire', heap: 'bi-memory' };

const model = usePluginUsage();
const plural = (count: number, one: string, many: string): string => `${count} ${count === 1 ? one : many}`;

const OPTIONS: LensOption[] = AREA_LENSES.map((lens) => {
  const area = model.areas.get(lens)!;
  const cases = model.data.replays.filter((replay) => model.caseArea(replay) === lens).length;
  return {
    lens,
    label: area.label,
    detail: `${area.agent} · ${plural(area.skills.length, 'skill', 'skills')} · ${plural(cases, 'case', 'cases')}`,
    icon: ICONS[lens]
  };
});
</script>

<template>
  <div class="area-switch" role="radiogroup" aria-label="Area of the plugin">
    <button
      v-for="option in OPTIONS"
      :key="option.lens"
      type="button"
      role="radio"
      class="area-option"
      :class="[`area-option--${option.lens}`, { 'area-option--on': option.lens === lens }]"
      :aria-checked="option.lens === lens"
      @click="emit('change', option.lens)"
    >
      <i class="bi area-icon" :class="option.icon" aria-hidden="true"></i>
      <span class="area-text">
        <span class="area-label">{{ option.label }}</span>
        <span class="area-detail">{{ option.detail }}</span>
      </span>
    </button>
  </div>
</template>

<style scoped>
.area-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.area-option {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px 8px 12px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  color: var(--site-ink);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.area-option:hover {
  border-color: var(--site-faint);
}

.area-icon {
  font-size: 17px;
  color: var(--site-faint);
}

.area-option--jfr .area-icon {
  color: var(--ap-jfr);
}

.area-option--heap .area-icon {
  color: var(--ap-heap);
}

.area-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.area-label {
  font-size: 13.5px;
  font-weight: 600;
}

.area-detail {
  font-size: 11.5px;
  color: var(--site-faint);
}

.area-option--on {
  border-color: var(--site-ink);
  background: var(--site-ink);
  color: var(--site-surface);
}

.area-option--on .area-icon,
.area-option--on .area-detail {
  color: inherit;
  opacity: 0.85;
}

@media (max-width: 859px) {
  .area-switch {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
  }

  .area-option {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    padding: 8px 10px;
  }

  .area-detail {
    display: none;
  }
}
</style>
