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
import { nextTick, ref, watch } from 'vue';
import { useRoute, useRouter, type LocationQuery } from 'vue-router';
import MapTab from '@/components/plugin/MapTab.vue';
import ReplayTab from '@/components/plugin/ReplayTab.vue';
import ToolsTab from '@/components/plugin/ToolsTab.vue';
import {
  formatCount,
  nodeId,
  parseNodeId,
  usePluginUsage,
  type WiringMode
} from '@/composables/usePluginUsage';

// The microscope coding-agent plugin in action: its recorded eval runs replayed call by call, the
// wiring of its skills, agents and tools, and every tool with how often the runs called it. The data
// is src/data/plugin-usage.json, written by build/scripts/plugin-usage-map.py.

type Tab = 'replay' | 'map' | 'tools';

const TABS: Tab[] = ['replay', 'map', 'tools'];
const DEFAULT_TAB: Tab = 'replay';
const DEFAULT_CASE = 'heap-dump-what-holds-memory';
const QUERY_TAB = 'tab';
const QUERY_CASE = 'case';
const QUERY_RUN = 'run';

const model = usePluginUsage();
const { totals } = model;
const replays = model.data.replays;
const route = useRoute();
const router = useRouter();

const TAB_LABELS: Record<Tab, { label: string; count: string }> = {
  replay: { label: 'Replay', count: `${replays.length} cases` },
  map: { label: 'Map', count: `${totals.skills} · ${totals.agents} · ${totals.families}` },
  tools: { label: 'Tools', count: `${totals.tools}` }
};

const FACTS: { value: number; label: string }[] = [
  { value: totals.skills, label: 'skills' },
  { value: totals.agents, label: 'agents' },
  { value: totals.tools, label: `tools in ${totals.families} families` },
  { value: totals.runs, label: 'recorded eval runs' },
  { value: totals.skillInvocations, label: 'skill invocations' },
  { value: totals.handOffs, label: 'agent hand-offs' },
  { value: totals.toolCalls, label: 'Jeffrey tool calls' }
];

function single(query: LocationQuery, key: string): string | null {
  const value = query[key];
  return typeof value === 'string' ? value : null;
}

function caseIndexOf(name: string | null): number {
  const index = replays.findIndex((replay) => replay.case === (name ?? DEFAULT_CASE));
  return Math.max(0, index);
}

const tab = ref<Tab>(DEFAULT_TAB);
const caseIndex = ref(0);
const runIndex = ref(0);

function readQuery(query: LocationQuery): void {
  const asked = single(query, QUERY_TAB);
  tab.value = TABS.find((candidate) => candidate === asked) ?? DEFAULT_TAB;
  caseIndex.value = caseIndexOf(single(query, QUERY_CASE));
  const run = Number(single(query, QUERY_RUN) ?? 1);
  const runs = replays[caseIndex.value]?.runs ?? [];
  runIndex.value = Math.max(0, runs.findIndex((item) => item.run === run));
}

readQuery(route.query);
watch(() => route.query, readQuery);

watch([tab, caseIndex, runIndex], () => {
  const replay = replays[caseIndex.value];
  const query = {
    [QUERY_TAB]: tab.value,
    [QUERY_CASE]: replay?.case,
    [QUERY_RUN]: String(replay?.runs[runIndex.value]?.run ?? 1)
  };
  const same = Object.entries(query).every(([key, value]) => single(route.query, key) === value);
  if (!same) {
    router.replace({ query });
  }
});

// A tab opens at its top: the reader may have scrolled far down the previous one.
const tabsBar = ref<HTMLElement | null>(null);
const HEADER_OFFSET_PX = 70;

watch(tab, async () => {
  await nextTick();
  const bar = tabsBar.value;
  if (!bar) {
    return;
  }
  const top = bar.getBoundingClientRect().top + window.scrollY - HEADER_OFFSET_PX;
  if (window.scrollY > top) {
    window.scrollTo({ top });
  }
});

// ---------- the map's state, shared by every tab that opens something on it ----------

const mode = ref<WiringMode>('observed');
const selected = ref<string | null>(null);
const expanded = ref(new Set<string>());
const trail = ref(new Set<string>());
const trailLabel = ref<string | null>(null);

function expand(family: string | undefined): void {
  if (family && !expanded.value.has(family)) {
    expanded.value = new Set([...expanded.value, family]);
  }
}

/** A family opens or closes; a tool opens its family; anything else toggles its selection. */
function select(id: string): void {
  const { kind, name } = parseNodeId(id);
  if (kind === 'family') {
    const next = new Set(expanded.value);
    if (next.has(name) && selected.value === id) {
      next.delete(name);
    } else {
      next.add(name);
    }
    expanded.value = next;
    selected.value = id;
  } else {
    if (kind === 'tool') {
      expand(model.familyOf(name));
    }
    selected.value = selected.value === id && kind !== 'tool' ? null : id;
  }
}

function pickOnMap(id: string): void {
  select(id);
  tab.value = 'map';
}

function openMapWithTrail(ids: string[]): void {
  trail.value = new Set(ids);
  const replay = replays[caseIndex.value];
  trailLabel.value = `${replay.case} · run ${replay.runs[runIndex.value]?.run ?? 1}`;
  for (const id of ids) {
    const { kind, name } = parseNodeId(id);
    if (kind === 'tool') {
      expand(model.familyOf(name));
    }
  }
  tab.value = 'map';
}

function clearTrail(): void {
  trail.value = new Set();
  trailLabel.value = null;
}

function openCase(index: number): void {
  caseIndex.value = index;
  runIndex.value = 0;
  tab.value = 'replay';
}

function selectCase(index: number): void {
  caseIndex.value = index;
  runIndex.value = 0;
}

</script>

<template>
  <main class="agent-plugin">
    <header class="ap-header">
      <div class="ap-header-inner">
        <div class="ap-eyebrow">Coding-agent plugin · v{{ model.data.plugin }}</div>
        <h1 class="ap-title">The plugin in action</h1>
        <p class="ap-lede">
          How the <code>microscope</code> plugin's skills and agents route a coding agent's performance questions
          to Jeffrey's MCP tools, replayed call by call from the plugin's recorded eval runs.
        </p>
        <ul class="ap-facts">
          <li v-for="fact in FACTS" :key="fact.label"><b>{{ formatCount(fact.value) }}</b> {{ fact.label }}</li>
        </ul>
        <nav ref="tabsBar" class="ap-tabs" role="tablist" aria-label="Agent plugin">
          <button
            v-for="item in TABS"
            :key="item"
            type="button"
            role="tab"
            class="ap-tab"
            :class="{ 'ap-tab--on': item === tab }"
            :aria-selected="item === tab"
            @click="tab = item"
          >
            {{ TAB_LABELS[item].label }}<span class="ap-tab-count">{{ TAB_LABELS[item].count }}</span>
          </button>
        </nav>
      </div>
    </header>

    <ReplayTab
      v-if="tab === 'replay'"
      :case-index="caseIndex"
      :run-index="runIndex"
      @select-case="selectCase"
      @select-run="runIndex = $event"
      @open-map="openMapWithTrail"
      @pick="pickOnMap"
    />
    <MapTab
      v-else-if="tab === 'map'"
      :mode="mode"
      :selected="selected"
      :expanded="expanded"
      :trail="trail"
      :trail-label="trailLabel"
      @mode="mode = $event"
      @select="select"
      @clear-trail="clearTrail"
      @open-case="openCase"
    />
    <ToolsTab v-else @show="pickOnMap(nodeId('tool', $event))" />
  </main>
</template>

<style scoped>
/* The page's own palette, on the site's warm paper: skills in flame, agents in the MCP green and
   tools in the Microscope blue of the homepage constellation. */
.agent-plugin {
  --ap-skill: var(--site-ember);
  --ap-skill-bg: #fff1e6;
  --ap-agent: #0b7a55;
  --ap-agent-bg: #e3f4ec;
  --ap-tool: #1d3fd8;
  --ap-tool-bg: #e8edfc;
  --ap-ok: #1f8a4c;
  --ap-ok-line: rgba(31, 138, 76, 0.35);
  --ap-no: #c2410c;
  --ap-no-line: rgba(194, 65, 12, 0.35);
  --ap-hover: rgba(48, 32, 12, 0.04);
  --ap-current: #fff4ea;
  --ap-tab-start: #ffab00;
  --ap-edge: rgba(48, 32, 12, 0.14);
  --ap-radius-xs: 3px;
  --ap-radius-md: 14px;
  --ap-radius-pill: 999px;
  background: var(--site-paper);
  color: var(--site-ink);
}

.ap-header {
  border-bottom: 1px solid var(--site-line);
  background: var(--site-surface);
}

.ap-header-inner {
  max-width: 1360px;
  margin: 0 auto;
  padding: 26px 40px 0;
}

.ap-eyebrow {
  font-family: var(--site-font-mono);
  font-size: 11.5px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--site-ember);
}

.ap-title {
  margin: 8px 0 6px;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.ap-lede {
  max-width: 760px;
  margin: 0;
  font-size: 15px;
  line-height: 1.55;
  color: var(--site-muted);
}

.ap-lede code {
  font-family: var(--site-font-mono);
  font-size: 13.5px;
  color: var(--site-ink);
}

.ap-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 26px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
  font-size: 13.5px;
  color: var(--site-muted);
}

.ap-facts b {
  margin-right: 4px;
  font-size: 17px;
  color: var(--site-ink);
}

.ap-tabs {
  display: flex;
  gap: 4px;
  margin-top: 16px;
  overflow-x: auto;
}

.ap-tab {
  position: relative;
  padding: 10px 16px 12px;
  border: 0;
  background: transparent;
  color: var(--site-muted);
  font-size: 14.5px;
  white-space: nowrap;
  cursor: pointer;
}

.ap-tab:hover {
  color: var(--site-ink);
}

.ap-tab--on {
  color: var(--site-ink);
  font-weight: 600;
}

.ap-tab--on::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 2px;
  border-radius: var(--ap-radius-xs);
  background: linear-gradient(90deg, var(--ap-tab-start), var(--site-flame));
}

.ap-tab-count {
  margin-left: 7px;
  font-size: 11.5px;
  font-weight: 400;
  color: var(--site-faint);
}

@media (max-width: 859px) {
  .ap-header-inner {
    padding: 18px 16px 0;
  }

  .ap-title {
    font-size: 26px;
  }

  .ap-lede {
    font-size: 14px;
  }

  .ap-facts {
    gap: 4px 16px;
    font-size: 12.5px;
  }

  .ap-facts b {
    font-size: 15px;
  }

  .ap-tab {
    padding: 10px 12px 12px;
  }
}
</style>
