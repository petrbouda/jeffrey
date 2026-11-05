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

// Skills → agents → tool families, each line as wide as how often its two ends meet: in the
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

const WIDTH = 780;
const TOP = 34;
const COLUMN_X = { skill: 52, agent: 254, tool: 446 } as const;
const COLUMN_W = { skill: 172, agent: 156, tool: 326 } as const;
const SKILL_PITCH = 32;
const FAMILY_PITCH = 27;
const TOOL_PITCH = 22;
const TOOL_INDENT = 14;
const MIN_QUERY = 2;
const BAND_HEAD = 28;
const BAND_PAD = 10;
const BAND_GAP = 10;

const model = usePluginUsage();
const hover = ref<string | null>(null);

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

/** One horizontal band per area under the lens, each holding its skills, its agent and its families. */
const layout = computed(() => {
  const nodes = new Map<string, MapNode>();
  const bands: MapBand[] = [];
  let y = TOP;
  for (const area of model.areasFor(props.lens)) {
    const bandTop = y;
    const rowTop = bandTop + BAND_HEAD;
    const skills = model.skillsByUse.filter((skill) => area.skills.includes(skill.name));
    skills.forEach((skill, i) => {
      const id = nodeId('skill', skill.name);
      nodes.set(id, { id, kind: 'skill', x: COLUMN_X.skill, y: rowTop + i * SKILL_PITCH, w: COLUMN_W.skill, h: 24,
        label: skill.name, count: model.skillInvocations(skill.name) });
    });
    const skillBottom = rowTop + skills.length * SKILL_PITCH;
    let familyY = rowTop;
    for (const family of model.families.filter((candidate) => area.families.includes(candidate))) {
      const open = props.expanded.has(family);
      const id = nodeId('family', family);
      const tools = model.familyTools.get(family) ?? [];
      nodes.set(id, { id, kind: 'family', x: COLUMN_X.tool, y: familyY, w: COLUMN_W.tool, h: 22,
        label: `${open ? '▾' : '▸'} ${family}_*  (${tools.length})`, count: model.familyCalls(family) });
      familyY += FAMILY_PITCH;
      if (open) {
        for (const tool of tools) {
          const toolId = nodeId('tool', tool);
          nodes.set(toolId, { id: toolId, kind: 'tool', x: COLUMN_X.tool + TOOL_INDENT, y: familyY, w: COLUMN_W.tool - TOOL_INDENT, h: 19,
            label: tool, count: model.toolCalls(tool) });
          familyY += TOOL_PITCH;
        }
        familyY += 4;
      }
    }
    if (area.agent) {
      const id = nodeId('agent', area.agent);
      const centre = (rowTop + Math.max(skillBottom, rowTop + SKILL_PITCH)) / 2;
      nodes.set(id, { id, kind: 'agent', x: COLUMN_X.agent, y: centre - 13, w: COLUMN_W.agent, h: 26,
        label: area.agent, count: model.agentSpawns(area.agent) });
    }
    const bandBottom = Math.max(skillBottom, familyY) + BAND_PAD;
    bands.push({ area: area.name, title: area.agent ? `${area.label} · worked by ${area.agent}` : `${area.label} · used by both`,
      y: bandTop, h: bandBottom - bandTop });
    y = bandBottom + BAND_GAP;
  }
  return { nodes, bands, height: y };
});

function edgePath(from: MapNode, to: MapNode): string {
  const y1 = from.y + from.h / 2;
  const y2 = to.y + to.h / 2;
  if (from.kind === 'skill' && to.kind === 'skill') {
    const bend = 22 + Math.abs(y2 - y1) * 0.18;
    return `M${from.x},${y1} C${from.x - bend},${y1} ${to.x - bend},${y2} ${to.x},${y2}`;
  }
  const x1 = from.x + from.w;
  const x2 = to.x;
  const mid = (x1 + x2) / 2;
  return `M${x1},${y1} C${mid},${y1} ${mid},${y2} ${x2},${y2}`;
}

const edges = computed<MapEdge[]>(() => {
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
const focus = computed(() => hover.value ?? props.selected);

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
  <div class="map-scroll">
    <svg
      class="map"
      :viewBox="`0 0 ${WIDTH} ${layout.height}`"
      :style="{ aspectRatio: `${WIDTH} / ${layout.height}` }"
      role="group"
      aria-label="Skills, agents and tool families"
      @mouseleave="hover = null"
    >
      <text class="map-column" :x="COLUMN_X.skill" y="18">Skills · invocations</text>
      <text class="map-column" :x="COLUMN_X.agent" y="18">Agents · spawns</text>
      <text class="map-column" :x="COLUMN_X.tool" y="18">Tools · calls</text>
      <g v-for="band in layout.bands" :key="band.area" class="map-band" :class="`map-band--${band.area}`">
        <rect x="4" :y="band.y" :width="WIDTH - 8" :height="band.h" rx="10" />
        <text class="map-band-title" :x="COLUMN_X.skill - 36" :y="band.y + 18">{{ band.title }}</text>
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
        <rect :x="node.x" :y="node.y" :width="node.w" :height="node.h" rx="6" />
        <text class="map-label" :x="node.x + 8" :y="node.y + node.h / 2 + 4">{{ node.label }}</text>
        <text class="map-count" :x="node.x + node.w - 8" :y="node.y + node.h / 2 + 4">{{ node.count ? formatCount(node.count) : '–' }}</text>
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
  width: 100%;
  min-width: 640px;
  height: auto;
}

.map-column {
  font-size: 10.5px;
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
  font-size: 11px;
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

.map-edge--trail {
  opacity: 1;
}

.map-node {
  cursor: pointer;
  outline: none;
  transition: opacity 0.15s;
}

.map-node rect {
  stroke: transparent;
  stroke-width: 1.6;
}

.map-node--skill rect {
  fill: var(--ap-skill-bg);
}

.map-node--agent rect {
  fill: var(--ap-agent-bg);
}

.map-node--family rect,
.map-node--tool rect {
  fill: var(--ap-tool-bg);
}

.map-node--tool rect {
  fill: var(--site-surface);
  stroke: var(--ap-tool-bg);
}

.map-node--skill.map-node--trail rect,
.map-node--skill.map-node--selected rect,
.map-node--skill:focus-visible rect {
  stroke: var(--ap-skill);
}

.map-node--agent.map-node--trail rect,
.map-node--agent.map-node--selected rect,
.map-node--agent:focus-visible rect {
  stroke: var(--ap-agent);
}

.map-node--family.map-node--trail rect,
.map-node--family.map-node--selected rect,
.map-node--family:focus-visible rect,
.map-node--tool.map-node--trail rect,
.map-node--tool.map-node--selected rect,
.map-node--tool:focus-visible rect {
  stroke: var(--ap-tool);
}

.map-node--selected rect {
  stroke-width: 2.4;
}

.map-node--match rect {
  stroke: var(--site-flame);
  stroke-dasharray: 4 3;
}

.map-node--dim {
  opacity: 0.35;
}

.map-label {
  font-family: var(--site-font-mono);
  font-size: 11.5px;
  fill: var(--site-ink);
}

.map-node--unused .map-label {
  fill: var(--site-faint);
}

.map-count {
  font-size: 11px;
  text-anchor: end;
  fill: var(--site-faint);
  font-variant-numeric: tabular-nums;
}
</style>
