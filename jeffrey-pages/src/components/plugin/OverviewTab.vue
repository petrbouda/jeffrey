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
import { formatCount, usePluginUsage, type AreaLens } from '@/composables/usePluginUsage';
import type { Replay } from '@/types/pluginUsage';

// The figures of the recorded runs, one card per area with its agent, and the cases whose runs did
// not all pass.

const emit = defineEmits<{
  (e: 'open-area', area: AreaLens, tab: 'replay' | 'map'): void;
  (e: 'open-case', index: number): void;
}>();

interface AreaSummary {
  lens: AreaLens;
  label: string;
  agent: string | null;
  skills: number;
  families: number;
  cases: number;
  passed: number;
  runs: number;
}

const AREA_LENSES_SHOWN: AreaLens[] = ['jfr', 'heap'];

const model = usePluginUsage();
const { totals } = model;
const replays = model.data.replays;

const FACTS: { value: number; label: string }[] = [
  { value: totals.skills, label: 'skills' },
  { value: totals.agents, label: 'agents' },
  { value: totals.tools, label: `tools in ${totals.families} families` },
  { value: totals.runs, label: 'recorded eval runs' },
  { value: totals.skillInvocations, label: 'skill invocations' },
  { value: totals.handOffs, label: 'agent hand-offs' },
  { value: totals.toolCalls, label: 'Jeffrey tool calls' }
];

const AREAS: AreaSummary[] = AREA_LENSES_SHOWN.map((lens) => {
  const area = model.areas.get(lens)!;
  const runs = model.casesOf(lens).flatMap((index) => replays[index].runs);
  return {
    lens,
    label: area.label,
    agent: area.agent,
    skills: area.skills.length,
    families: area.families.length,
    cases: model.casesOf(lens).length,
    passed: runs.filter((run) => run.passed).length,
    runs: runs.length
  };
});

const FAILING: { index: number; replay: Replay; area: string }[] = replays
  .map((replay, index) => ({ index, replay, area: model.areas.get(model.caseArea(replay))?.label ?? '' }))
  .filter(({ replay }) => replay.runs.some((run) => run.passed === false));

const differentWiring = model.data.plugin !== model.data.wiringPlugin;

function runState(passed: boolean | null): string {
  if (passed === null) {
    return 'unknown';
  }
  return passed ? 'passed' : 'failed';
}
</script>

<template>
  <div class="overview">
    <div class="overview-eyebrow">Coding-agent plugin · runs v{{ model.data.plugin }} · wiring v{{ model.data.wiringPlugin }}</div>
    <p v-if="differentWiring" class="overview-history">
      The runs were recorded with v{{ model.data.plugin }}; their agent names are linked to the current ones, and they do not
      validate changes made in v{{ model.data.wiringPlugin }}.
    </p>

    <ul class="overview-facts">
      <li v-for="fact in FACTS" :key="fact.label" class="overview-fact">
        <b>{{ formatCount(fact.value) }}</b>
        <span>{{ fact.label }}</span>
      </li>
    </ul>

    <div class="overview-areas">
      <section v-for="area in AREAS" :key="area.lens" class="overview-area" :class="`overview-area--${area.lens}`">
        <h2 class="overview-area-label">{{ area.label }}</h2>
        <div class="overview-area-agent">{{ area.agent }}</div>
        <p class="overview-area-facts">
          {{ area.skills }} skills · {{ area.families }} {{ area.families === 1 ? 'tool family' : 'tool families' }} · {{ area.cases }} cases ·
          {{ area.passed }}/{{ area.runs }} runs passed
        </p>
        <div class="overview-area-actions">
          <button type="button" class="overview-button" @click="emit('open-area', area.lens, 'replay')">
            Replay its cases <i class="bi bi-arrow-right" aria-hidden="true"></i>
          </button>
          <button type="button" class="overview-button" @click="emit('open-area', area.lens, 'map')">
            Open its map <i class="bi bi-arrow-right" aria-hidden="true"></i>
          </button>
        </div>
      </section>
    </div>

    <section class="overview-failing">
      <h2 class="overview-section-title">Cases with a failed run</h2>
      <p v-if="!FAILING.length" class="overview-none">Every run of every case passed.</p>
      <button
        v-for="item in FAILING"
        :key="item.index"
        type="button"
        class="overview-case"
        :title="item.replay.prompt"
        @click="emit('open-case', item.index)"
      >
        <span class="overview-case-name">{{ item.replay.case }}</span>
        <span class="overview-case-area">{{ item.area }}</span>
        <span class="overview-dots" :aria-label="item.replay.runs.map((run) => `run ${run.run} ${runState(run.passed)}`).join(', ')">
          <i v-for="run in item.replay.runs" :key="run.run" class="overview-dot" :class="`overview-dot--${runState(run.passed)}`"></i>
        </span>
      </button>
    </section>
  </div>
</template>

<style scoped>
.overview {
  padding: 24px 28px 48px;
}

.overview-eyebrow {
  font-family: var(--site-font-mono);
  font-size: 11.5px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--site-ember);
}

.overview-history {
  max-width: 820px;
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--site-faint);
}

.overview-facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.overview-fact {
  padding: 14px 16px;
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
}

.overview-fact b {
  display: block;
  font-size: 26px;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.overview-fact span {
  font-size: 13px;
  color: var(--site-muted);
}

.overview-areas {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.overview-area {
  padding: 18px 20px;
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
}

.overview-area--jfr {
  background: linear-gradient(var(--ap-jfr-band), var(--ap-jfr-band)), var(--site-surface);
}

.overview-area--heap {
  background: linear-gradient(var(--ap-heap-band), var(--ap-heap-band)), var(--site-surface);
}

.overview-area-label {
  margin: 0;
  font-family: var(--site-font-mono);
  font-size: 11.5px;
  font-weight: 500;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.overview-area--jfr .overview-area-label {
  color: var(--ap-jfr);
}

.overview-area--heap .overview-area-label {
  color: var(--ap-heap);
}

.overview-area-agent {
  margin-top: 6px;
  font-family: var(--site-font-mono);
  font-size: 20px;
  font-weight: 600;
}

.overview-area-facts {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--site-faint);
}

.overview-area-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.overview-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  color: var(--site-ink);
  font-size: 13px;
  cursor: pointer;
}

.overview-button:hover {
  border-color: var(--site-ember);
  color: var(--site-ember);
}

.overview-failing {
  margin-top: 18px;
  padding: 16px 18px;
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
}

.overview-section-title {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--site-faint);
}

.overview-none {
  margin: 0;
  font-size: 13px;
  color: var(--site-faint);
}

.overview-case {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 7px 6px;
  border: 0;
  border-radius: var(--site-radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.overview-case:hover {
  background: var(--ap-hover);
}

.overview-case-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  color: var(--site-ink);
}

.overview-case-area {
  font-size: 12.5px;
  color: var(--site-faint);
}

.overview-dots {
  display: inline-flex;
  gap: 3px;
}

.overview-dot {
  width: 7px;
  height: 7px;
  border-radius: var(--ap-radius-pill);
}

.overview-dot--passed {
  background: var(--ap-ok);
}

.overview-dot--failed {
  background: var(--ap-no);
}

.overview-dot--unknown {
  background: var(--site-line-strong);
}

@media (max-width: 859px) {
  .overview {
    padding: 18px 16px 32px;
  }

  .overview-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-areas {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
