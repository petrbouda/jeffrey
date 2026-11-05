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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  formatCount,
  nodeId,
  parseNodeId,
  usePluginUsage,
  type AreaLens,
  type NodeKind,
  type ReplayTrail,
  type WiringMode
} from '@/composables/usePluginUsage';
import type { AreaName } from '@/types/pluginUsage';

// Who calls → tool families, each line as wide as how often its two ends meet: in the
// instructions (described) or in the runs (observed). A family opens into its tools. Hovering or
// picking a node lights it and its neighbours; the trail marks what a replayed run went through.

const props = defineProps<{
  mode: WiringMode;
  selected: string | null;
  expanded: Set<string>;
  trail: ReplayTrail;
  query: string;
  lens: AreaLens;
}>();

const emit = defineEmits<{ (e: 'select', id: string): void }>();

interface MapNode {
  id: string;
  kind: NodeKind;
  x: number;
  y: number;
  w: number;
  h: number;
  label: string;
  count: number;
}

interface MapEdge {
  key: string;
  from: MapNode;
  to: MapNode;
  count: number;
  path: string;
}

// Drawn one unit to one pixel, as wide as its card (never narrower than MIN_WIDTH: a phone scrolls it
// sideways), so its text stays the size of the page's.
const MIN_WIDTH = 720;
const DEFAULT_WIDTH = 1000;
const TOP = 36;
// Two columns: who calls (the area's agent first, then its skills) and the tools, right-aligned; the lines
// run between them. The agent is a helper the skills hand work to, not a stage between them and the tools,
// so it has no column of its own.
const CALLER_X = 56;
const CALLER_W = 240;
const TOOL_W = { min: 360, max: 520 } as const;
const COLUMN_GAP_MIN = 200;
const EDGE_RIGHT = 20;
const AGENT_ROW = 28;
const AGENT_GAP = 8;
const AGENT_GLYPH_PX = 18;
const SHARE_BAR_PX = 3;
// Inside the row, clear of the outline a picked or traced row gets.
const SHARE_INSET = 2;
const SKILL_ROW = 28;
const SKILL_PITCH = 34;
const FAMILY_ROW = 26;
const FAMILY_PITCH = 31;
const TOOL_ROW = 24;
const TOOL_PITCH = 28;
const TEXT_DROP = 4.5;
const TOOL_INDENT = 14;
const MIN_QUERY = 2;
const BAND_HEAD = 28;
const BAND_PAD = 10;
const BAND_GAP = 10;

const model = usePluginUsage();
const hover = ref<string | null>(null);

const scroller = ref<HTMLElement | null>(null);
const measured = ref(0);
let observer: ResizeObserver | null = null;

function measure(): void {
  measured.value = scroller.value?.clientWidth ?? 0;
}

onMounted(() => {
  measure();
  if (typeof ResizeObserver !== 'undefined' && scroller.value) {
    observer = new ResizeObserver(measure);
    observer.observe(scroller.value);
  }
});

onBeforeUnmount(() => {
  observer?.disconnect();
});

const width = computed(() => Math.max(MIN_WIDTH, measured.value || DEFAULT_WIDTH));

const columns = computed(() => {
  const toolW = Math.min(TOOL_W.max, Math.max(TOOL_W.min, width.value - CALLER_X - CALLER_W - COLUMN_GAP_MIN - EDGE_RIGHT));
  return {
    caller: { x: CALLER_X, w: CALLER_W },
    tool: { x: width.value - EDGE_RIGHT - toolW, w: toolW }
  };
});

/** A tool shows as its family until the family is open. */
function visible(id: string): string {
  const { kind, name } = parseNodeId(id);
  if (kind !== 'tool') {
    return id;
  }
  const family = model.familyOf(name);
  return family && !props.expanded.has(family) ? nodeId('family', family) : id;
}

interface MapBand {
  area: AreaName;
  title: string;
  y: number;
  h: number;
}

/** One horizontal band per area under the lens: its agent and skills on the left, its families on the right. */
const layout = computed(() => {
  const nodes = new Map<string, MapNode>();
  const bands: MapBand[] = [];
  const { caller, tool: toolColumn } = columns.value;
  let y = TOP;
  for (const area of model.areasFor(props.lens)) {
    const bandTop = y;
    const rowTop = bandTop + BAND_HEAD;
    let firstSkill = rowTop;
    if (area.agent) {
      const id = nodeId('agent', area.agent);
      nodes.set(id, { id, kind: 'agent', x: caller.x, y: rowTop, w: caller.w, h: AGENT_ROW,
        label: area.agent, count: model.agentSpawns(area.agent) });
      firstSkill = rowTop + AGENT_ROW + AGENT_GAP;
    }
    const skills = model.skillsByUse.filter((skill) => area.skills.includes(skill.name));
    skills.forEach((skill, i) => {
      const id = nodeId('skill', skill.name);
      nodes.set(id, { id, kind: 'skill', x: caller.x, y: firstSkill + i * SKILL_PITCH, w: caller.w, h: SKILL_ROW,
        label: skill.name, count: model.skillInvocations(skill.name) });
    });
    const skillBottom = firstSkill + skills.length * SKILL_PITCH;
    let familyY = rowTop;
    for (const family of model.families.filter((candidate) => area.families.includes(candidate))) {
      const open = props.expanded.has(family);
      const id = nodeId('family', family);
      const tools = model.familyTools.get(family) ?? [];
      nodes.set(id, { id, kind: 'family', x: toolColumn.x, y: familyY, w: toolColumn.w, h: FAMILY_ROW,
        label: `${open ? '▾' : '▸'} ${family}_*  (${tools.length})`, count: model.familyCalls(family) });
      familyY += FAMILY_PITCH;
      if (open) {
        for (const tool of tools) {
          const toolId = nodeId('tool', tool);
          nodes.set(toolId, { id: toolId, kind: 'tool', x: toolColumn.x + TOOL_INDENT, y: familyY, w: toolColumn.w - TOOL_INDENT, h: TOOL_ROW,
            label: tool, count: model.toolCalls(tool) });
          familyY += TOOL_PITCH;
        }
        familyY += 4;
      }
    }
    const bandBottom = Math.max(skillBottom, familyY) + BAND_PAD;
    bands.push({ area: area.name, title: area.agent ? `${area.label} · worked by ${area.agent}` : `${area.label} · used by both`,
      y: bandTop, h: bandBottom - bandTop });
    y = bandBottom + BAND_GAP;
  }
  return { nodes, bands, height: y };
});

const isCaller = (node: MapNode): boolean => node.kind === 'skill' || node.kind === 'agent';

function edgePath(from: MapNode, to: MapNode): string {
  const y1 = from.y + from.h / 2;
  const y2 = to.y + to.h / 2;
  // Between two callers (a hand-over, a hand-off to the agent): a curve on the left, kept inside the band.
  if (isCaller(from) && isCaller(to)) {
    const bend = Math.min(from.x - 14, 22 + Math.abs(y2 - y1) * 0.18);
    return `M${from.x},${y1} C${from.x - bend},${y1} ${to.x - bend},${y2} ${to.x},${y2}`;
  }
  const x1 = from.x + from.w;
  const x2 = to.x;
  const mid = (x1 + x2) / 2;
  return `M${x1},${y1} C${mid},${y1} ${mid},${y2} ${x2},${y2}`;
}

const allEdges = computed<MapEdge[]>(() => {
  const merged = new Map<string, number>();
  for (const edge of model.edges(props.mode)) {
    const key = `${visible(edge.from)}|${visible(edge.to)}`;
    merged.set(key, (merged.get(key) ?? 0) + edge.count);
  }
  const { nodes } = layout.value;
  const list: MapEdge[] = [];
  for (const [key, count] of merged) {
    const [fromId, toId] = key.split('|');
    const from = nodes.get(fromId);
    const to = nodes.get(toId);
    if (from && to && from !== to) {
      list.push({ key, from, to, count, path: edgePath(from, to) });
    }
  }
  return list;
});

const visibleTrail = computed(() => new Set(props.trail.nodes.map(visible)));
const visibleTrailEdges = computed(() => new Set(props.trail.edges.map((edge) =>
  `${visible(edge.from)}|${visible(edge.to)}`
)));
// A picked node holds the focus while the pointer moves on to look at its tools; hovering previews only
// while nothing is picked.
const focus = computed(() => props.selected ?? hover.value);

/** An agent's calls to the tools: drawn only for the agent in focus, or where the replayed run made them. */
const edges = computed<MapEdge[]>(() => allEdges.value.filter((edge) =>
  edge.from.kind !== 'agent' || edge.to.kind === 'agent' || edge.from.id === focus.value || visibleTrailEdges.value.has(edge.key)
));

/** In the runs: how many of each tool row's calls an agent made, shown as a green share of the row. */
const viaAgent = computed(() => {
  const shares = new Map<string, number>();
  if (props.mode !== 'observed') {
    return shares;
  }
  for (const edge of allEdges.value) {
    if (edge.from.kind === 'agent' && !isCaller(edge.to)) {
      shares.set(edge.to.id, (shares.get(edge.to.id) ?? 0) + edge.count);
    }
  }
  return shares;
});

/** What an agent's row says it called, when its lines are not drawn. */
function agentSummary(node: MapNode): string {
  const calls = allEdges.value.filter((edge) => edge.from.id === node.id && !isCaller(edge.to));
  const total = calls.reduce((sum, edge) => sum + edge.count, 0);
  return calls.length ? `${formatCount(total)} calls · ${calls.length} ${calls.length === 1 ? 'family' : 'families'} ▸` : '';
}

const shareWidth = (node: MapNode): number =>
  node.count ? Math.max(4, ((node.w - 2 * SHARE_INSET) * (viaAgent.value.get(node.id) ?? 0)) / node.count) : 0;

const neighbours = computed(() => {
  const near = new Set<string>();
  if (!focus.value) {
    return near;
  }
  near.add(focus.value);
  for (const edge of edges.value) {
    if (edge.from.id === focus.value) {
      near.add(edge.to.id);
    }
    if (edge.to.id === focus.value) {
      near.add(edge.from.id);
    }
  }
  return near;
});

function edgeClass(edge: MapEdge): string[] {
  const classes = ['map-edge', `map-edge--from-${edge.from.kind}`];
  if (edge.to.kind === 'agent') {
    classes.push('map-edge--handoff');
  }
  if (focus.value) {
    classes.push(edge.from.id === focus.value || edge.to.id === focus.value ? 'map-edge--hot' : 'map-edge--dim');
  }
  if (visibleTrailEdges.value.has(edge.key)) {
    classes.push('map-edge--trail');
  }
  return classes;
}

const edgeWidth = (count: number): string => `${(0.7 + Math.log2(1 + count) * 0.55).toFixed(2)}px`;

function nodeClass(node: MapNode): string[] {
  const classes = ['map-node', `map-node--${node.kind}`];
  if (node.id === props.selected) {
    classes.push('map-node--selected');
  }
  if (visibleTrail.value.has(node.id)) {
    classes.push('map-node--trail');
  }
  if (focus.value && !neighbours.value.has(node.id)) {
    classes.push('map-node--dim');
  }
  const needle = props.query.trim().toLowerCase();
  if (needle.length >= MIN_QUERY && node.label.toLowerCase().includes(needle)) {
    classes.push('map-node--match');
  }
  if (!node.count) {
    classes.push('map-node--unused');
  }
  return classes;
}

const ariaLabel = (node: MapNode): string => `${node.kind} ${node.label.replace(/^[▾▸] /, '')}, ${formatCount(node.count)}`;
</script>

<template>
  <div ref="scroller" class="map-scroll">
    <svg
      class="map"
      :width="width"
      :height="layout.height"
      :viewBox="`0 0 ${width} ${layout.height}`"
      role="group"
      aria-label="Skills, agents and tool families"
      @mouseleave="hover = null"
    >
      <text class="map-column" :x="columns.caller.x" y="20">Who calls · invocations / spawns</text>
      <text class="map-column" :x="columns.tool.x" y="20">Tools · calls</text>
      <g v-for="band in layout.bands" :key="band.area" class="map-band" :class="`map-band--${band.area}`">
        <rect x="4" :y="band.y" :width="width - 8" :height="band.h" rx="10" />
        <text class="map-band-title" :x="columns.caller.x - 36" :y="band.y + 19">{{ band.title }}</text>
      </g>
      <path
        v-for="edge in edges"
        :key="edge.key"
        :class="edgeClass(edge)"
        :d="edge.path"
        :style="{ strokeWidth: edgeWidth(edge.count) }"
      >
        <title>{{ edge.from.label }} → {{ edge.to.label }}: {{ formatCount(edge.count) }}</title>
      </path>
      <g
        v-for="node in layout.nodes.values()"
        :key="node.id"
        :class="nodeClass(node)"
        tabindex="0"
        role="button"
        :aria-label="ariaLabel(node)"
        :aria-pressed="node.id === selected"
        @mouseenter="hover = node.id"
        @focus="hover = node.id"
        @blur="hover = null"
        @click="emit('select', node.id)"
        @keydown.enter.prevent="emit('select', node.id)"
        @keydown.space.prevent="emit('select', node.id)"
      >
        <rect class="map-box" :x="node.x" :y="node.y" :width="node.w" :height="node.h" rx="6" />
        <text v-if="node.kind === 'agent'" class="map-agent-glyph" :x="node.x + 8" :y="node.y + node.h / 2 + TEXT_DROP" aria-hidden="true">⤷</text>
        <text class="map-label" :x="node.x + 8 + (node.kind === 'agent' ? AGENT_GLYPH_PX : 0)" :y="node.y + node.h / 2 + TEXT_DROP">{{ node.label }}</text>
        <text class="map-count" :x="node.x + node.w - 8" :y="node.y + node.h / 2 + TEXT_DROP">{{ node.count ? formatCount(node.count) : '–' }}</text>
        <template v-if="viaAgent.has(node.id)">
          <rect class="map-share" :x="node.x + SHARE_INSET" :y="node.y + node.h - SHARE_BAR_PX - SHARE_INSET" :width="shareWidth(node)" :height="SHARE_BAR_PX" rx="1.5" />
          <text class="map-share-text" :x="node.x + node.w - 52" :y="node.y + node.h / 2 + TEXT_DROP">{{ formatCount(viaAgent.get(node.id) ?? 0) }} via agent</text>
        </template>
        <text
          v-if="node.kind === 'agent' && node.id !== focus && agentSummary(node)"
          class="map-agent-note"
          :x="node.x + node.w + 10"
          :y="node.y + node.h / 2 + TEXT_DROP"
        >{{ agentSummary(node) }}</text>
      </g>
    </svg>
  </div>
</template>

<style scoped>
/* On a phone the map keeps a readable size and scrolls sideways inside its card. */
.map-scroll {
  overflow-x: auto;
}

.map {
  display: block;
}

.map-column {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  fill: var(--site-faint);
}

.map-band rect {
  fill: var(--ap-shared-band);
}

.map-band--jfr rect {
  fill: var(--ap-jfr-band);
}

.map-band--heap rect {
  fill: var(--ap-heap-band);
}

.map-band-title {
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  fill: var(--site-muted);
}

.map-band--jfr .map-band-title {
  fill: var(--ap-jfr);
}

.map-band--heap .map-band-title {
  fill: var(--ap-heap);
}

.map-edge {
  fill: none;
  stroke: var(--ap-edge);
  transition: stroke 0.15s, opacity 0.15s;
}

.map-edge--dim {
  opacity: 0.25;
}

.map-edge--hot.map-edge--from-skill,
.map-edge--trail.map-edge--from-skill {
  stroke: var(--ap-skill);
}

.map-edge--hot.map-edge--from-agent,
.map-edge--trail.map-edge--from-agent {
  stroke: var(--ap-agent);
}

.map-edge--handoff {
  stroke: var(--ap-agent);
  opacity: 0.55;
}

.map-share {
  fill: var(--ap-agent);
}

.map-share-text,
.map-agent-note {
  font-size: 12px;
  fill: var(--ap-agent);
}

.map-share-text {
  text-anchor: end;
}

.map-agent-glyph {
  font-size: 13px;
  fill: var(--ap-agent);
}

.map-edge--trail {
  opacity: 1;
}

.map-node {
  cursor: pointer;
  outline: none;
  transition: opacity 0.15s;
}

.map-node .map-box {
  stroke: transparent;
  stroke-width: 1.6;
}

.map-node--skill .map-box {
  fill: var(--ap-skill-bg);
}

.map-node--agent .map-box {
  fill: var(--ap-agent-bg);
}

.map-node--family .map-box,
.map-node--tool .map-box {
  fill: var(--ap-tool-bg);
}

.map-node--tool .map-box {
  fill: var(--site-surface);
  stroke: var(--ap-tool-bg);
}

.map-node--skill.map-node--trail .map-box,
.map-node--skill.map-node--selected .map-box,
.map-node--skill:focus-visible .map-box {
  stroke: var(--ap-skill);
}

.map-node--agent.map-node--trail .map-box,
.map-node--agent.map-node--selected .map-box,
.map-node--agent:focus-visible .map-box {
  stroke: var(--ap-agent);
}

.map-node--family.map-node--trail .map-box,
.map-node--family.map-node--selected .map-box,
.map-node--family:focus-visible .map-box,
.map-node--tool.map-node--trail .map-box,
.map-node--tool.map-node--selected .map-box,
.map-node--tool:focus-visible .map-box {
  stroke: var(--ap-tool);
}

.map-node--selected .map-box {
  stroke-width: 2.4;
}

.map-node--match .map-box {
  stroke: var(--site-flame);
  stroke-dasharray: 4 3;
}

.map-node--dim {
  opacity: 0.35;
}

.map-label {
  font-family: var(--site-font-mono);
  font-size: 13px;
  fill: var(--site-ink);
}

.map-node--unused .map-label {
  fill: var(--site-faint);
}

.map-count {
  font-size: 12.5px;
  text-anchor: end;
  fill: var(--site-faint);
  font-variant-numeric: tabular-nums;
}
</style>
