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
import PluginNodeChip from '@/components/plugin/PluginNodeChip.vue';
import RunDots from '@/components/plugin/RunDots.vue';
import WorkloadMark from '@/components/plugin/WorkloadMark.vue';
import { nodeId, usePluginUsage, type NodeKind, type UseCaseSummary } from '@/composables/usePluginUsage';
import type { PluginUseCase, Replay } from '@/types/pluginUsage';

// The plugin by the question a reader brings: one card per use case, the eval cases that ask it — each a
// question to replay — and what answered them in the recorded runs: the skills, the agent and the tool
// families, with how often the runs called each family.

defineProps<{ focused: string | null }>();
const emit = defineEmits<{
  (e: 'open-case', index: number): void;
  (e: 'pick', id: string): void;
}>();

interface UseCaseCard {
  useCase: PluginUseCase;
  cases: { index: number; replay: Replay }[];
  summary: UseCaseSummary;
}

const model = usePluginUsage();
const replays = model.data.replays;

const CARDS: UseCaseCard[] = model.data.useCases.map((useCase) => ({
  useCase,
  cases: model.casesOfUseCase(useCase.name).map((index) => ({ index, replay: replays[index] })),
  summary: model.useCaseSummary(useCase.name)
}));

const answeredBy = (summary: UseCaseSummary): { kind: NodeKind; name: string; count: number | null }[] => [
  ...summary.skills.map(([name]) => ({ kind: 'skill' as const, name, count: null })),
  ...summary.agents.map(([name]) => ({ kind: 'agent' as const, name, count: null })),
  ...summary.families.map(([name, count]) => ({ kind: 'family' as const, name, count }))
];
</script>

<template>
  <div class="use-cases">
    <p class="use-cases-lede">
      Each card is a question you can bring to Jeffrey. Its examples are recorded eval runs: pick one to replay it call by call,
      or pick a skill, agent or tool family to see it on the map.
    </p>
    <div class="use-cases-grid">
      <section
        v-for="card in CARDS"
        :id="`use-case-${card.useCase.name}`"
        :key="card.useCase.name"
        class="use-case"
        :class="{ 'use-case--focused': card.useCase.name === focused }"
        :aria-labelledby="`use-case-${card.useCase.name}-title`"
      >
        <header class="use-case-head">
          <span class="use-case-icon"><i class="bi" :class="card.useCase.icon" aria-hidden="true"></i></span>
          <div>
            <h2 :id="`use-case-${card.useCase.name}-title`" class="use-case-title">{{ card.useCase.label }}</h2>
            <p class="use-case-question">{{ card.useCase.question }}</p>
          </div>
        </header>

        <h3 class="use-case-label">
          Ask it · {{ card.cases.length }} {{ card.cases.length === 1 ? 'example' : 'examples' }}
          <span v-if="card.summary.runs" class="use-case-passed">{{ card.summary.passed }}/{{ card.summary.runs }} runs passed</span>
        </h3>
        <p v-if="!card.cases.length" class="use-case-none">No recorded runs yet.</p>
        <button
          v-for="item in card.cases"
          :key="item.index"
          type="button"
          class="use-case-ask"
          :title="item.replay.prompt"
          @click="emit('open-case', item.index)"
        >
          <i class="bi bi-play-circle use-case-play" aria-hidden="true"></i>
          <span class="use-case-ask-text">{{ model.questionOf(item.replay) }}</span>
          <span class="use-case-ask-marks">
            <span v-if="item.replay.suite === 'live'" class="use-case-live" title="Run against a live Jeffrey">live</span>
            <WorkloadMark v-if="model.workloadOf(item.replay)" :workload="model.workloadOf(item.replay)!" />
            <RunDots :runs="item.replay.runs" />
          </span>
        </button>

        <template v-if="card.summary.runs">
          <h3 class="use-case-label">What answers it</h3>
          <div class="use-case-chips">
            <PluginNodeChip
              v-for="node in answeredBy(card.summary)"
              :key="`${node.kind}:${node.name}`"
              :kind="node.kind"
              :label="node.name"
              :count="node.count"
              :title="node.count === null ? `Open ${node.name} on the map` : `${node.count} calls to the ${node.name} tools in these runs`"
              @pick="emit('pick', nodeId(node.kind, node.name))"
            />
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.use-cases {
  padding: 24px 28px 48px;
}

.use-cases-lede {
  max-width: 820px;
  margin: 0 0 18px;
  font-size: 14px;
  line-height: 1.55;
  color: var(--site-muted);
}

.use-cases-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  align-items: start;
}

.use-case {
  padding: 18px 18px 16px;
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
  scroll-margin-top: calc(var(--site-header-height) + var(--ap-bar-height) + 16px);
}

.use-case--focused {
  border-color: var(--site-ember);
}

.use-case-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.use-case-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--site-radius-sm);
  background: var(--ap-current);
  color: var(--site-ember);
  font-size: 17px;
}

.use-case-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.use-case-question {
  margin: 2px 0 0;
  font-size: 13.5px;
  line-height: 1.45;
  color: var(--site-muted);
}

.use-case-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin: 16px 0 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--site-faint);
}

.use-case-passed {
  font-weight: 500;
  letter-spacing: 0;
  text-transform: none;
}

.use-case-none {
  margin: 0;
  font-size: 13px;
  color: var(--site-faint);
}

.use-case-ask {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  width: 100%;
  padding: 6px 8px;
  border: 0;
  border-radius: var(--site-radius-sm);
  background: transparent;
  color: var(--site-ink);
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
}

.use-case-ask:hover,
.use-case-ask:focus-visible {
  background: var(--ap-hover);
}

.use-case-play {
  flex: none;
  color: var(--site-faint);
}

.use-case-ask:hover .use-case-play {
  color: var(--site-ember);
}

.use-case-ask-text {
  flex: 1 1 200px;
  min-width: 0;
}

.use-case-ask-marks {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.use-case-live {
  padding: 0 6px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--ap-radius-pill);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--site-faint);
}

.use-case-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

@media (max-width: 859px) {
  .use-cases {
    padding: 18px 16px 32px;
  }

  .use-cases-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
