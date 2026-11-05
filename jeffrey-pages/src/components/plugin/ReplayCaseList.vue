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
import { computed, ref } from 'vue';
import WorkloadMark from '@/components/plugin/WorkloadMark.vue';
import { usePluginUsage, type AreaLens } from '@/composables/usePluginUsage';
import type { AreaName, Replay } from '@/types/pluginUsage';

// Every replayed eval case under the lens, grouped by the area whose skills it invokes, with one dot
// per run: green passed, orange failed; a case run against a live Jeffrey says so, and each case is
// marked with what it ran on: a real service's recorded runs or a made-up workload. On a phone the list
// folds into a select.

const props = defineProps<{ replays: Replay[]; selected: number; lens: AreaLens }>();
const emit = defineEmits<{ (e: 'select', index: number): void }>();

interface AreaGroup {
  area: AreaName;
  label: string;
  agent: string | null;
  size: number;
  cases: { index: number; replay: Replay }[];
}

const model = usePluginUsage();
const query = ref('');

const groups = computed<AreaGroup[]>(() => {
  const needle = query.value.trim().toLowerCase();
  return model
    .areasFor(props.lens)
    .map((area) => {
      const inArea = model.casesOf(area.name).map((index) => ({ index, replay: props.replays[index] }));
      return {
        area: area.name,
        label: area.label,
        agent: area.agent,
        size: inArea.length,
        cases: inArea.filter(({ replay }) => !needle || replay.case.includes(needle) || replay.prompt.toLowerCase().includes(needle))
      };
    })
    .filter((group) => group.size > 0);
});

function runState(passed: boolean | null): string {
  if (passed === null) {
    return 'unknown';
  }
  return passed ? 'passed' : 'failed';
}

/** On a phone the select carries what the row's mark says. */
function optionSuffix(replay: Replay): string {
  const workload = model.workloadOf(replay);
  if (!workload) {
    return '';
  }
  return workload.real ? ' · real service' : ' · made-up';
}

function onSelectChange(event: Event): void {
  emit('select', Number((event.target as HTMLSelectElement).value));
}
</script>

<template>
  <div class="case-list">
    <label class="case-select-wrap">
      <span class="visually-hidden">Eval case</span>
      <select class="case-select" :value="selected" @change="onSelectChange">
        <optgroup v-for="group in groups" :key="group.area" :label="`${group.label} (${group.size})`">
          <option v-for="item in group.cases" :key="item.index" :value="item.index">{{ item.replay.case }}{{ optionSuffix(item.replay) }}</option>
        </optgroup>
      </select>
    </label>

    <div class="case-list-full">
      <label class="case-search">
        <i class="bi bi-search" aria-hidden="true"></i>
        <span class="visually-hidden">Search cases</span>
        <input v-model="query" type="search" placeholder="Search cases or prompts…" />
      </label>

      <p class="case-key">
        <span>Data:</span>
        <WorkloadMark v-for="workload in model.data.workloads" :key="workload.name" :workload="workload" />
      </p>

      <section v-for="group in groups" :key="group.area" class="case-group" :class="`case-group--${group.area}`">
        <h3 class="case-group-title">
          {{ group.label }} ({{ group.size }})
          <span v-if="group.agent" class="case-group-agent">{{ group.agent }}</span>
        </h3>
        <p v-if="!group.cases.length" class="case-none">No case matches.</p>
        <button
          v-for="item in group.cases"
          :key="item.index"
          type="button"
          class="case-item"
          :class="{ 'case-item--on': item.index === selected }"
          :aria-current="item.index === selected ? 'true' : undefined"
          :title="item.replay.prompt"
          @click="emit('select', item.index)"
        >
          <span class="case-name">{{ item.replay.case }}</span>
          <span v-if="item.replay.suite === 'live'" class="case-live" title="Run against a live Jeffrey">live</span>
          <WorkloadMark v-if="model.workloadOf(item.replay)" :workload="model.workloadOf(item.replay)!" compact />
          <span class="case-dots" :aria-label="item.replay.runs.map((run) => `run ${run.run} ${runState(run.passed)}`).join(', ')">
            <i v-for="run in item.replay.runs" :key="run.run" class="case-dot" :class="`case-dot--${runState(run.passed)}`"></i>
          </span>
        </button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.case-select-wrap {
  display: none;
  width: 100%;
}

.case-select {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  color: var(--site-ink);
  font-size: 14px;
}

.case-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  color: var(--site-faint);
}

.case-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--site-ink);
  font-size: 13px;
}

.case-group-title {
  margin: 18px 10px 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--site-faint);
}

.case-group-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-left: 8px;
  border-left: 3px solid var(--site-line-strong);
}

.case-group--jfr .case-group-title {
  border-left-color: var(--ap-jfr);
}

.case-group--heap .case-group-title {
  border-left-color: var(--ap-heap);
}

.case-group-agent {
  white-space: nowrap;
  font-family: var(--site-font-mono);
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 0;
  text-transform: none;
  color: var(--ap-agent);
}

.case-live {
  flex: none;
  padding: 0 6px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--ap-radius-pill);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--site-faint);
}

.case-key {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 8px 2px 0;
  font-size: 12px;
  color: var(--site-faint);
}

.case-none {
  margin: 0 10px;
  font-size: 12.5px;
  color: var(--site-faint);
}

.case-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 6px 10px;
  border: 0;
  border-radius: var(--site-radius-sm);
  background: transparent;
  color: var(--site-muted);
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}

.case-item:hover {
  background: var(--ap-hover);
  color: var(--site-ink);
}

.case-item--on,
.case-item--on:hover {
  background: var(--ap-skill-bg);
  color: var(--site-ink);
  font-weight: 600;
}

.case-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-dots {
  display: inline-flex;
  flex: none;
  gap: 3px;
}

.case-dot {
  width: 7px;
  height: 7px;
  border-radius: var(--ap-radius-pill);
}

.case-dot--passed {
  background: var(--ap-ok);
}

.case-dot--failed {
  background: var(--ap-no);
}

.case-dot--unknown {
  background: var(--site-line-strong);
}

@media (max-width: 859px) {
  .case-select-wrap {
    display: block;
  }

  .case-list-full {
    display: none;
  }
}
</style>
