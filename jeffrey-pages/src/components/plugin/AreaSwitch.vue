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
      :title="option.detail"
      @click="emit('change', option.lens)"
    >
      <i class="bi area-icon" :class="option.icon" aria-hidden="true"></i>
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
/* A two-option toggle for the page's bar; each option's agent, skills and cases are in its title. */
.area-switch {
  display: inline-flex;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  overflow: hidden;
}

.area-option {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 12px;
  border: 0;
  background: var(--site-surface);
  color: var(--site-muted);
  font-size: 13.5px;
  white-space: nowrap;
  cursor: pointer;
}

.area-option + .area-option {
  border-left: 1px solid var(--site-line-strong);
}

.area-option:hover {
  color: var(--site-ink);
}

.area-icon {
  font-size: 14px;
}

.area-option--jfr .area-icon {
  color: var(--ap-jfr);
}

.area-option--heap .area-icon {
  color: var(--ap-heap);
}

.area-option--on,
.area-option--on:hover {
  background: var(--site-ink);
  color: var(--site-surface);
}

.area-option--on .area-icon {
  color: inherit;
}
</style>
