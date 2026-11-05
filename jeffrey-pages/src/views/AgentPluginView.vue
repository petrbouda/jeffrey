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
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter, type LocationQuery } from 'vue-router';
import AreaSwitch from '@/components/plugin/AreaSwitch.vue';
import MapTab from '@/components/plugin/MapTab.vue';
import OverviewTab from '@/components/plugin/OverviewTab.vue';
import ReplayCaseList from '@/components/plugin/ReplayCaseList.vue';
import ReplayTab from '@/components/plugin/ReplayTab.vue';
import ToolsTab from '@/components/plugin/ToolsTab.vue';
import {
  nodeId,
  parseNodeId,
  usePluginUsage,
  AREA_LENSES,
  type AreaLens,
  type ReplayTrail,
  type WiringMode
} from '@/composables/usePluginUsage';
import type { AreaName } from '@/types/pluginUsage';

// The microscope coding-agent plugin in action. One bar under the site header holds the tabs and, where a
// tab is scoped to an area, the JFR recordings / Heap dumps switch; Overview carries the figures of the
// recorded runs, Replay plays them call by call beside the case list, Map and Tools show the wiring. The
// data is src/data/plugin-usage.json, written by build/scripts/plugin-usage-map.py.

type Tab = 'overview' | 'replay' | 'map' | 'tools';

const TABS: Tab[] = ['overview', 'replay', 'map', 'tools'];
const DEFAULT_TAB: Tab = 'overview';
const LINKED_CASE_TAB: Tab = 'replay';
/** The tabs that show one area at a time, and so carry the area switch. */
const AREA_TABS = new Set<Tab>(['replay', 'map', 'tools']);
const QUERY_TAB = 'tab';
const QUERY_CASE = 'case';
const QUERY_RUN = 'run';
const QUERY_AREA = 'area';
const DEFAULT_LENS: AreaLens = 'jfr';

const model = usePluginUsage();
const replays = model.data.replays;
const route = useRoute();
const router = useRouter();


function single(query: LocationQuery, key: string): string | null {
  const value = query[key];
  return typeof value === 'string' ? value : null;
}

/** The named case, or the first one of the lens's own area. */
function caseIndexOf(name: string | null, lens: AreaLens): number {
  const index = replays.findIndex((replay) => replay.case === name);
  return index >= 0 ? index : (model.areasFor(lens).flatMap((area) => model.casesOf(area.name))[0] ?? 0);
}

const tab = ref<Tab>(DEFAULT_TAB);
const caseIndex = ref(0);
const runIndex = ref(0);
const lens = ref<AreaLens>(DEFAULT_LENS);

function readQuery(query: LocationQuery): void {
  const asked = single(query, QUERY_TAB);
  // A link to a case opens its replay; the bare page opens on the overview.
  tab.value = TABS.find((candidate) => candidate === asked) ?? (single(query, QUERY_CASE) ? LINKED_CASE_TAB : DEFAULT_TAB);
  const area = single(query, QUERY_AREA);
  const askedLens = AREA_LENSES.find((candidate) => candidate === area) ?? DEFAULT_LENS;
  caseIndex.value = caseIndexOf(single(query, QUERY_CASE), askedLens);
  const caseArea = model.caseArea(replays[caseIndex.value]);
  lens.value = caseArea === 'shared' ? askedLens : caseArea;
  const run = Number(single(query, QUERY_RUN) ?? 1);
  const runs = replays[caseIndex.value]?.runs ?? [];
  runIndex.value = Math.max(0, runs.findIndex((item) => item.run === run));
}

readQuery(route.query);
watch(() => route.query, readQuery);

watch([tab, lens, caseIndex, runIndex], () => {
  const replay = replays[caseIndex.value];
  const query = {
    [QUERY_TAB]: tab.value,
    [QUERY_AREA]: lens.value,
    [QUERY_CASE]: replay?.case,
    [QUERY_RUN]: String(replay?.runs[runIndex.value]?.run ?? 1)
  };
  const same = Object.entries(query).every(([key, value]) => single(route.query, key) === value);
  if (!same) {
    router.replace({ query });
  }
}, { immediate: true });

// A tab opens at its top: the reader may have scrolled far down the previous one.
const bar = ref<HTMLElement | null>(null);
const HEADER_OFFSET_PX = 70;

watch(tab, async () => {
  await nextTick();
  const element = bar.value;
  if (!element) {
    return;
  }
  const top = element.getBoundingClientRect().top + window.scrollY - HEADER_OFFSET_PX;
  if (window.scrollY > top) {
    window.scrollTo({ top });
  }
});

// ---------- the map's state, shared by every tab that opens something on it ----------

const mode = ref<WiringMode>('observed');
const selected = ref<string | null>(null);
const expanded = ref(new Set<string>());
const trail = ref<ReplayTrail>({ nodes: [], edges: [] });
const trailLabel = ref<string | null>(null);

/** Each tab's count under the lens: cases; skills · agents · families; tools. */
const tabLabels = computed<Record<Tab, { label: string; count: string }>>(() => {
  const shown = model.areasFor(lens.value);
  const skills = shown.reduce((sum, area) => sum + area.skills.length, 0);
  const agents = shown.filter((area) => area.agent).length;
  const families = shown.flatMap((area) => area.families);
  const tools = families.reduce((sum, family) => sum + (model.familyTools.get(family)?.length ?? 0), 0);
  const cases = shown.reduce((sum, area) => sum + model.casesOf(area.name).length, 0);
  return {
    overview: { label: 'Overview', count: '' },
    replay: { label: 'Replay', count: `${cases} cases` },
    map: { label: 'Map', count: `${skills} · ${agents} · ${families.length}` },
    tools: { label: 'Tools', count: `${tools}` }
  };
});

// Keep the area and its destinations coherent before the query watcher sees a change.
function changeLens(next: AreaLens): void {
  lens.value = next;
  if (!model.inLens(next, model.caseArea(replays[caseIndex.value]))) {
    caseIndex.value = model.areasFor(next).flatMap((area) => model.casesOf(area.name))[0] ?? 0;
    runIndex.value = 0;
  }
}

function revealArea(area: AreaName): void {
  if (area !== 'shared' && area !== lens.value) {
    changeLens(area);
  }
}

watch(lens, () => {
  if (selected.value && !model.inLens(lens.value, model.areaOfNode(selected.value))) {
    selected.value = null;
  }
});

function expand(family: string | undefined): void {
  if (family && !expanded.value.has(family)) {
    expanded.value = new Set([...expanded.value, family]);
  }
}

const CALLER_KINDS = new Set(['skill', 'agent']);

/** A picked skill or agent stays picked while its families are opened and closed to follow its lines. */
const callerPinned = (): boolean => selected.value !== null && CALLER_KINDS.has(parseNodeId(selected.value).kind);

/**
 * A family opens or closes (and is picked, unless a skill or agent is pinned); a tool opens its family;
 * anything else toggles its selection.
 */
function select(id: string): void {
  revealArea(model.areaOfNode(id));
  const { kind, name } = parseNodeId(id);
  if (kind === 'family') {
    const keepsPin = callerPinned();
    const next = new Set(expanded.value);
    if (next.has(name) && (keepsPin || selected.value === id)) {
      next.delete(name);
    } else {
      next.add(name);
    }
    expanded.value = next;
    if (!keepsPin) {
      selected.value = id;
    }
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

function openMapWithTrail(runTrail: ReplayTrail): void {
  mode.value = 'observed';
  trail.value = runTrail;
  const replay = replays[caseIndex.value];
  trailLabel.value = `${replay.case} · run ${replay.runs[runIndex.value]?.run ?? 1}`;
  for (const id of runTrail.nodes) {
    const { kind, name } = parseNodeId(id);
    if (kind === 'tool') {
      expand(model.familyOf(name));
    }
  }
  tab.value = 'map';
}

function clearTrail(): void {
  trail.value = { nodes: [], edges: [] };
  trailLabel.value = null;
}

function openCase(index: number): void {
  revealArea(model.caseArea(replays[index]));
  caseIndex.value = index;
  runIndex.value = 0;
  tab.value = 'replay';
}

function selectCase(index: number): void {
  caseIndex.value = index;
  runIndex.value = 0;
}

function openArea(area: AreaLens, next: 'replay' | 'map'): void {
  changeLens(area);
  tab.value = next;
}

</script>

<template>
  <main class="agent-plugin">
    <h1 class="visually-hidden">Agent Plugin</h1>
    <div ref="bar" class="ap-bar">
      <nav class="ap-tabs" role="tablist" aria-label="Agent plugin">
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
          {{ tabLabels[item].label }}<span v-if="tabLabels[item].count" class="ap-tab-count">{{ tabLabels[item].count }}</span>
        </button>
      </nav>
      <AreaSwitch v-if="AREA_TABS.has(tab)" class="ap-areas" :lens="lens" @change="changeLens" />
    </div>

    <div class="ap-shell" :class="{ 'ap-shell--rail': tab === 'replay' }">
      <aside v-if="tab === 'replay'" class="ap-rail">
        <ReplayCaseList :replays="replays" :selected="caseIndex" :lens="lens" @select="selectCase" />
      </aside>

      <div class="ap-content">
    <OverviewTab v-if="tab === 'overview'" @open-area="openArea" @open-case="openCase" />
    <ReplayTab
      v-else-if="tab === 'replay'"
      :case-index="caseIndex"
      :run-index="runIndex"
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
      :lens="lens"
      @mode="mode = $event"
      @select="select"
      @clear-trail="clearTrail"
      @open-case="openCase"
    />
    <ToolsTab v-else :lens="lens" @show="pickOnMap(nodeId('tool', $event))" />
      </div>
    </div>
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
  --ap-jfr: var(--site-ember);
  --ap-jfr-band: rgba(255, 109, 0, 0.055);
  --ap-heap: #6d3fd6;
  --ap-heap-band: rgba(109, 63, 214, 0.06);
  --ap-shared-band: rgba(48, 32, 12, 0.03);
  --ap-tab-start: #ffab00;
  --ap-bar-height: 52px;
  --ap-edge: rgba(48, 32, 12, 0.14);
  --ap-radius-xs: 3px;
  --ap-radius-md: 14px;
  --ap-radius-pill: 999px;
  min-height: calc(100vh - var(--site-header-height));
  background: var(--site-paper);
  color: var(--site-ink);
}

/* One bar under the site header: the tabs keep their place, the area switch sits at its right end. */
.ap-bar {
  position: sticky;
  top: var(--site-header-height);
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid var(--site-line);
  background: var(--site-surface);
}

.ap-tabs {
  display: flex;
  gap: 4px;
  overflow-x: auto;
}

.ap-tab {
  position: relative;
  padding: 15px 14px 14px;
  border: 0;
  background: transparent;
  color: var(--site-muted);
  font-size: 14.5px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
}

.ap-tab:hover {
  color: var(--site-ink);
}

/* The active tab changes colour, not weight, so no tab moves when another is picked. */
.ap-tab--on {
  color: var(--site-ink);
}

.ap-tab--on::after {
  content: '';
  position: absolute;
  left: 10px;
  right: 10px;
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

.ap-areas {
  margin-left: auto;
}

/* Full width like the docs; on Replay the case list is a rail on the left, under the bar. */
.ap-shell--rail {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
}

.ap-rail {
  position: sticky;
  top: calc(var(--site-header-height) + var(--ap-bar-height));
  align-self: start;
  max-height: calc(100vh - var(--site-header-height) - var(--ap-bar-height));
  overflow-y: auto;
  padding: 14px 12px 24px;
  border-right: 1px solid var(--site-line);
  background: var(--site-surface);
}

.ap-content {
  min-width: 0;
}

@media (max-width: 859px) {
  .ap-bar {
    flex-wrap: wrap;
    gap: 0 12px;
    padding: 0 8px 8px;
  }

  .ap-tab {
    padding: 14px 10px 12px;
  }

  .ap-tab-count {
    display: none;
  }

  .ap-areas {
    margin-left: 6px;
  }

  .ap-shell--rail {
    grid-template-columns: minmax(0, 1fr);
  }

  .ap-rail {
    position: static;
    max-height: none;
    padding: 12px 16px;
    border-right: 0;
    border-bottom: 1px solid var(--site-line);
  }
}
</style>
