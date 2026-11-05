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
import { ref } from 'vue';
import WiringMap from '@/components/plugin/WiringMap.vue';
import WiringNodeDetail from '@/components/plugin/WiringNodeDetail.vue';
import type { AreaLens, ReplayTrail, WiringMode } from '@/composables/usePluginUsage';

// The whole wiring: the map on the left, what is picked on it on the right.

defineProps<{
  mode: WiringMode;
  selected: string | null;
  expanded: Set<string>;
  trail: ReplayTrail;
  trailLabel: string | null;
  lens: AreaLens;
}>();

const emit = defineEmits<{
  (e: 'mode', mode: WiringMode): void;
  (e: 'select', id: string): void;
  (e: 'clear-trail'): void;
  (e: 'open-case', index: number): void;
}>();

const MODES: { mode: WiringMode; label: string }[] = [
  { mode: 'described', label: 'As the instructions describe it' },
  { mode: 'observed', label: 'As the runs used it' }
];

const query = ref('');
</script>

<template>
  <div class="map-tab">
    <div class="map-toolbar">
      <div class="map-modes" role="radiogroup" aria-label="Wiring">
        <button
          v-for="item in MODES"
          :key="item.mode"
          type="button"
          role="radio"
          class="map-mode"
          :class="{ 'map-mode--on': item.mode === mode }"
          :aria-checked="item.mode === mode"
          @click="emit('mode', item.mode)"
        >{{ item.label }}</button>
      </div>
      <label class="map-search">
        <i class="bi bi-search" aria-hidden="true"></i>
        <span class="visually-hidden">Find on the map</span>
        <input v-model="query" type="search" placeholder="Find a skill, agent or family…" />
      </label>
      <span v-if="trailLabel" class="map-trail">
        <i class="bi bi-signpost-split" aria-hidden="true"></i> {{ trailLabel }}
        <button type="button" aria-label="Stop showing this run" @click="emit('clear-trail')">
          <i class="bi bi-x-lg" aria-hidden="true"></i>
        </button>
      </span>
    </div>

    <div class="map-body">
      <section class="map-card">
        <div class="map-legend">
          <span><i class="map-swatch map-swatch--skill"></i>Skill</span>
          <span><i class="map-swatch map-swatch--agent"></i>Agent</span>
          <span><i class="map-swatch map-swatch--tool"></i>Tool family / tool</span>
          <span class="map-legend-note">
            {{ mode === 'described' ? 'A line: the skill or agent names it in its instructions.' : 'A line: calls made while that skill or agent was active.' }}
          </span>
        </div>
        <WiringMap :mode="mode" :selected="selected" :expanded="expanded" :trail="trail" :query="query" :lens="lens" @select="emit('select', $event)" />
      </section>
      <aside class="map-detail">
        <WiringNodeDetail :id="selected" @select="emit('select', $event)" @open-case="emit('open-case', $event)" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.map-tab {
  padding: 24px 28px 48px;
}

.map-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-bottom: 16px;
}

.map-modes {
  display: inline-flex;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  overflow: hidden;
}

.map-mode {
  padding: 7px 13px;
  border: 0;
  background: var(--site-surface);
  color: var(--site-muted);
  font-size: 13px;
  cursor: pointer;
}

.map-mode + .map-mode {
  border-left: 1px solid var(--site-line-strong);
}

.map-mode--on {
  background: var(--site-ink);
  color: var(--site-surface);
}

.map-search {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 260px;
  padding: 7px 10px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  color: var(--site-faint);
}

.map-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--site-ink);
  font-size: 13px;
}

.map-trail {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px 4px 12px;
  border-radius: var(--ap-radius-pill);
  background: var(--ap-skill-bg);
  color: var(--site-ember);
  font-size: 12.5px;
}

.map-trail button {
  display: inline-flex;
  padding: 3px 5px;
  border: 0;
  border-radius: var(--ap-radius-pill);
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.map-trail button:hover {
  background: var(--ap-hover);
}

.map-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
}

.map-card,
.map-detail {
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
}

.map-card {
  padding: 16px 20px 18px;
  min-width: 0;
}

.map-detail {
  position: sticky;
  top: calc(var(--site-header-height) + 16px);
  padding: 20px;
}

.map-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin-bottom: 8px;
  font-size: 12.5px;
  color: var(--site-muted);
}

.map-legend-note {
  margin-left: auto;
  color: var(--site-faint);
}

.map-swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 6px;
  border-radius: var(--ap-radius-xs);
  vertical-align: -1px;
}

.map-swatch--skill {
  background: var(--ap-skill);
}

.map-swatch--agent {
  background: var(--ap-agent);
}

.map-swatch--tool {
  background: var(--ap-tool);
}

@media (max-width: 1199px) {
  .map-tab {
    padding: 22px 24px 40px;
  }

  .map-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .map-detail {
    position: static;
  }
}

@media (max-width: 859px) {
  .map-tab {
    padding: 16px 16px 32px;
  }

  .map-search {
    min-width: 0;
    flex: 1 1 100%;
  }

  .map-card {
    padding: 12px;
  }

  .map-legend-note {
    margin-left: 0;
  }
}
</style>
