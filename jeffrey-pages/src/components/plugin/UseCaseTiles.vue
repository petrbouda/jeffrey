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
import { usePluginUsage } from '@/composables/usePluginUsage';

// The way into the plugin by question: one tile per use case — an example question its cases ask, what it
// answers, the tool families its runs called most, how many examples and how many of their runs passed.

const emit = defineEmits<{ (e: 'open-use-case', name: string): void }>();

const TOP_FAMILIES = 3;

const model = usePluginUsage();
const replays = model.data.replays;

const TILES = model.data.useCases.map((useCase) => {
  const cases = model.casesOfUseCase(useCase.name);
  const summary = model.useCaseSummary(useCase.name);
  const example = replays.find((replay) => replay.case === useCase.example) ?? replays[cases[0]];
  return {
    useCase,
    example: example ? model.questionOf(example) : null,
    families: summary.families.slice(0, TOP_FAMILIES),
    cases: cases.length,
    passed: summary.passed,
    runs: summary.runs
  };
});
</script>

<template>
  <section class="tiles-section" aria-labelledby="tiles-title">
    <h2 id="tiles-title" class="tiles-title">What do you want to know?</h2>
    <p class="tiles-lede">Pick a question: each opens the eval runs that answer it, replayed call by call.</p>
    <div class="tiles">
      <button
        v-for="tile in TILES"
        :key="tile.useCase.name"
        type="button"
        class="tile"
        @click="emit('open-use-case', tile.useCase.name)"
      >
        <span class="tile-head">
          <span class="tile-icon"><i class="bi" :class="tile.useCase.icon" aria-hidden="true"></i></span>
          <span class="tile-label">{{ tile.useCase.label }}</span>
        </span>
        <span v-if="tile.example" class="tile-example">“{{ tile.example }}”</span>
        <span class="tile-question">{{ tile.useCase.question }}</span>
        <span v-if="tile.families.length" class="tile-families">
          <span v-for="[family, calls] in tile.families" :key="family" class="tile-family">{{ family }} <b>{{ calls }}</b></span>
        </span>
        <span class="tile-meta">
          {{ tile.cases }} {{ tile.cases === 1 ? 'example' : 'examples' }}<template v-if="tile.runs"> · {{ tile.passed }}/{{ tile.runs }} runs passed</template>
        </span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.tiles-section {
  margin-top: 18px;
  padding: 20px 22px 22px;
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
}

.tiles-title {
  margin: 0;
  font-size: 21px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.tiles-lede {
  margin: 4px 0 16px;
  font-size: 14px;
  color: var(--site-muted);
}

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}

.tile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
  color: var(--site-ink);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.tile:hover,
.tile:focus-visible {
  border-color: var(--site-ember);
  background: var(--ap-current);
}

.tile-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tile-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--site-radius-sm);
  background: var(--ap-current);
  color: var(--site-ember);
  font-size: 15px;
}

.tile-label {
  font-size: 15px;
  font-weight: 600;
}

.tile-example {
  font-size: 13.5px;
  line-height: 1.45;
}

.tile-question {
  font-size: 13px;
  line-height: 1.45;
  color: var(--site-muted);
}

.tile-families {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.tile-family {
  padding: 1px 8px;
  border-radius: var(--ap-radius-pill);
  background: var(--ap-tool-bg);
  color: var(--ap-tool);
  font-family: var(--site-font-mono);
  font-size: 11.5px;
}

.tile-family b {
  font-weight: 500;
  color: var(--site-faint);
}

.tile-meta {
  margin-top: auto;
  font-size: 12px;
  color: var(--site-faint);
}

@media (max-width: 859px) {
  .tiles-section {
    padding: 16px;
  }

  .tiles {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
