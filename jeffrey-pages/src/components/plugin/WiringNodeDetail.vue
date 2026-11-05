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
import { computed } from 'vue';
import PluginNodeChip from '@/components/plugin/PluginNodeChip.vue';
import {
  formatCount,
  nodeId,
  parseNodeId,
  usePluginUsage,
  type NodeKind
} from '@/composables/usePluginUsage';
import type { Replay } from '@/types/pluginUsage';

// What a picked skill, agent, tool family or tool is: what it names, who it hands over to, what the
// runs called through it, and which replays to watch it in.

const props = defineProps<{ id: string | null }>();
const emit = defineEmits<{ (e: 'select', id: string): void; (e: 'open-case', index: number): void }>();

interface ChipItem {
  id: string;
  kind: NodeKind;
  label: string;
  count: number | null;
}

interface ChipGroup {
  title: string;
  chips: ChipItem[];
  empty?: string;
}

interface Detail {
  eyebrow: string;
  kind: NodeKind;
  title: string;
  description: string | null;
  facts: string;
  groups: ChipGroup[];
  replays: Replay[];
}

const model = usePluginUsage();

function chip(id: string, count: number | null = null): ChipItem {
  const { kind, name } = parseNodeId(id);
  return { id, kind, label: name, count };
}

function observedFrom(id: string): ChipItem[] {
  return Object.entries(model.data.observed[id] ?? {})
    .sort((a, b) => b[1] - a[1])
    .map(([target, count]) => chip(target, count));
}

function skillDetail(name: string): Detail {
  const skill = model.skills.get(name)!;
  const named = Object.keys(skill.tools).sort((a, b) => model.toolCalls(b) - model.toolCalls(a));
  const handOvers = [
    ...Object.keys(skill.agents).map((agent) => chip(nodeId('agent', agent))),
    ...Object.keys(skill.skills).map((other) => chip(nodeId('skill', other)))
  ];
  return {
    eyebrow: `Skill · /microscope:${name}`,
    kind: 'skill',
    title: name,
    description: skill.description,
    facts: `Invoked ${formatCount(model.skillInvocations(name))} times · ${skill.lines} lines of instructions · names ${named.length} tools`,
    groups: [
      { title: 'Hands over to', chips: handOvers, empty: 'No other skill or agent.' },
      { title: 'Tools it names · calls in runs', chips: named.map((tool) => chip(nodeId('tool', tool), model.toolCalls(tool))) },
      { title: 'What the runs called through it', chips: observedFrom(nodeId('skill', name)), empty: 'Nothing yet.' }
    ],
    replays: model.replaysWhere((step) => step.kind === 'skill' && step.name === name)
  };
}

function agentDetail(name: string): Detail {
  const agent = model.agents.get(name)!;
  const withheld = agent.denied.length ? ` · ${agent.denied.length} withheld` : '';
  return {
    eyebrow: 'Agent · delegated work',
    kind: 'agent',
    title: name,
    description: agent.description,
    facts: `Spawned ${formatCount(model.agentSpawns(name))} times · may call ${agent.tools.length} of ${model.totals.tools} tools${withheld}`,
    groups: [
      { title: 'What it called', chips: observedFrom(nodeId('agent', name)), empty: 'Nothing yet.' },
      { title: 'Withheld from it', chips: agent.denied.map((tool) => chip(nodeId('tool', tool))) }
    ],
    replays: model.replaysWhere((step) => step.kind === 'agent' && step.name === name)
  };
}

function familyDetail(name: string): Detail {
  const tools = model.familyTools.get(name) ?? [];
  const never = tools.filter((tool) => !model.toolCalls(tool)).length;
  return {
    eyebrow: 'Tool family',
    kind: 'family',
    title: `${name}_*`,
    description: null,
    facts: `${tools.length} tools · ${formatCount(model.familyCalls(name))} calls in runs · ${never} never called`,
    groups: [{ title: 'Its tools · calls in runs', chips: tools.map((tool) => chip(nodeId('tool', tool), model.toolCalls(tool))) }],
    replays: model.replaysWhere((step) => step.kind === 'jeffrey' && model.familyOf(step.name) === name)
  };
}

function toolDetail(name: string): Detail {
  const tool = model.tools.get(name)!;
  const id = nodeId('tool', name);
  const access = tool.destructive ? 'Destructive' : tool.readOnly ? 'Read-only' : 'Writes';
  const needs = tool.requires.length ? ` · needs ${tool.requires.join(', ').toLowerCase().replace(/_/g, ' ')}` : '';
  const callers = Object.entries(model.data.observed)
    .filter(([, targets]) => targets[id])
    .sort((a, b) => b[1][id] - a[1][id])
    .map(([caller, targets]) => chip(caller, targets[id]));
  return {
    eyebrow: `Tool · ${tool.family} family`,
    kind: 'tool',
    title: name,
    description: tool.title,
    facts: `${access} · cost ${(tool.cost ?? 'unstated').toLowerCase()}${needs} · ${formatCount(model.toolCalls(name))} calls in runs`,
    groups: [
      { title: 'Named by skills', chips: model.namedBy(name).map((skill) => chip(nodeId('skill', skill))), empty: 'No skill names it directly.' },
      { title: 'Agents that may call it', chips: model.agentsAllowing(name).map((agent) => chip(nodeId('agent', agent))), empty: 'Neither agent.' },
      { title: 'Called while active', chips: callers }
    ],
    replays: model.replaysWhere((step) => step.kind === 'jeffrey' && step.name === name)
  };
}

const DETAILS: Record<NodeKind, (name: string) => Detail> = {
  skill: skillDetail,
  agent: agentDetail,
  family: familyDetail,
  tool: toolDetail
};

const detail = computed<Detail | null>(() => {
  if (!props.id) {
    return null;
  }
  const { kind, name } = parseNodeId(props.id);
  return DETAILS[kind](name);
});

const caseIndex = (replay: Replay): number => model.data.replays.indexOf(replay);
</script>

<template>
  <div class="detail">
    <template v-if="detail">
      <div class="detail-eyebrow" :class="`detail-eyebrow--${detail.kind}`">{{ detail.eyebrow }}</div>
      <h3 class="detail-title" :class="{ 'detail-title--mono': detail.kind !== 'skill' && detail.kind !== 'agent' }">{{ detail.title }}</h3>
      <p v-if="detail.description" class="detail-description">{{ detail.description }}</p>
      <p class="detail-facts">{{ detail.facts }}</p>

      <template v-for="group in detail.groups" :key="group.title">
        <section v-if="group.chips.length || group.empty" class="detail-group">
          <h4 class="detail-group-title">{{ group.title }}</h4>
          <div v-if="group.chips.length" class="detail-chips">
            <PluginNodeChip
              v-for="item in group.chips"
              :key="item.id"
              :kind="item.kind"
              :label="item.label"
              :count="item.count"
              @pick="emit('select', item.id)"
            />
          </div>
          <p v-else class="detail-empty">{{ group.empty }}</p>
        </section>
      </template>

      <section v-if="detail.replays.length" class="detail-group">
        <h4 class="detail-group-title">Replays to watch it in</h4>
        <div class="detail-chips">
          <button
            v-for="replay in detail.replays"
            :key="replay.case"
            type="button"
            class="detail-case"
            @click="emit('open-case', caseIndex(replay))"
          >
            <i class="bi bi-play-circle" aria-hidden="true"></i> {{ replay.case }}
          </button>
        </div>
      </section>
    </template>

    <template v-else>
      <h3 class="detail-title">Pick anything on the map</h3>
      <p class="detail-description">
        A skill, agent, tool family or tool opens here: what it names, what it hands over to, and how often the
        {{ formatCount(model.totals.runs) }} recorded runs used it. Click a family to open it into its tools.
      </p>
    </template>
  </div>
</template>

<style scoped>
.detail-eyebrow {
  font-family: var(--site-font-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.detail-eyebrow--skill {
  color: var(--ap-skill);
}

.detail-eyebrow--agent {
  color: var(--ap-agent);
}

.detail-eyebrow--family,
.detail-eyebrow--tool {
  color: var(--ap-tool);
}

.detail-title {
  margin: 6px 0 8px;
  font-size: 20px;
  font-weight: 700;
  word-break: break-word;
}

.detail-title--mono {
  font-family: var(--site-font-mono);
  font-size: 17px;
}

.detail-description {
  margin: 0 0 8px;
  font-size: 13.5px;
  line-height: 1.55;
  color: var(--site-muted);
}

.detail-facts {
  margin: 0;
  font-size: 12.5px;
  color: var(--site-faint);
}

.detail-group {
  margin-top: 16px;
}

.detail-group-title {
  margin: 0 0 7px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--site-faint);
}

.detail-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-empty {
  margin: 0;
  font-size: 12.5px;
  color: var(--site-faint);
}

.detail-case {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--ap-radius-pill);
  background: var(--site-surface);
  color: var(--site-muted);
  font-size: 12px;
  cursor: pointer;
}

.detail-case:hover {
  border-color: var(--site-ember);
  color: var(--site-ember);
}
</style>
