/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import raw from '@/data/plugin-usage.json';
import type {
  AreaName,
  PluginAgent,
  PluginArea,
  PluginSkill,
  PluginTool,
  PluginUsage,
  Replay,
  ReplayRun,
  ReplayStep,
  StepOwner,
  Suite
} from '@/types/pluginUsage';

// The map of the microscope plugin's skills, agents and tools, and of how its recorded eval runs used
// them, as plugin-usage-map.py writes it. One model per page load; every view reads it, none edits it.

const data = raw as unknown as PluginUsage;

const NODE_PREFIX = { skill: 's:', agent: 'a:', family: 'f:', tool: 't:' } as const;
export type NodeKind = keyof typeof NODE_PREFIX;

export interface NodeRef {
  kind: NodeKind;
  name: string;
}

const KIND_OF_PREFIX = new Map<string, NodeKind>(
  (Object.entries(NODE_PREFIX) as [NodeKind, string][]).map(([kind, prefix]) => [prefix, kind])
);

export function nodeId(kind: NodeKind, name: string): string {
  return NODE_PREFIX[kind] + name;
}

export function parseNodeId(id: string): NodeRef {
  const kind = KIND_OF_PREFIX.get(id.slice(0, 2));
  if (!kind) {
    throw new Error(`Not a map node: ${id}`);
  }
  return { kind, name: id.slice(2) };
}

export function ownerNodeId(owner: StepOwner): string {
  return nodeId(owner[0], owner[1]);
}

/** As the skills' and agents' instructions name it, or as the runs used it. */
export type WiringMode = 'described' | 'observed';

export interface WiringEdge {
  from: string;
  to: string;
  count: number;
}

const COST_ORDER: Record<string, number> = { cheap: 0, moderate: 1, expensive: 2, slow: 3 };
const UNKNOWN_COST_ORDER = 9;
const MAIN_LANE = 'main';

const sum = (values: Iterable<number>): number => {
  let total = 0;
  for (const value of values) {
    total += value;
  }
  return total;
};

const tools = new Map<string, PluginTool>(data.tools.map((tool) => [tool.name, tool]));
const skills = new Map<string, PluginSkill>(data.skills.map((skill) => [skill.name, skill]));
const agents = new Map<string, PluginAgent>(data.agents.map((agent) => [agent.name, agent]));

const familyTools = new Map<string, string[]>();
for (const tool of data.tools) {
  const list = familyTools.get(tool.family) ?? [];
  list.push(tool.name);
  familyTools.set(tool.family, list);
}
for (const list of familyTools.values()) {
  list.sort();
}

const toolCalls = (tool: string): number => data.usage.jeffrey[tool] ?? 0;
const familyCalls = (family: string): number => sum((familyTools.get(family) ?? []).map(toolCalls));

/** Busiest first, then by name. */
const families = [...familyTools.keys()].sort((a, b) => familyCalls(b) - familyCalls(a) || a.localeCompare(b));
const skillsByUse = [...data.skills].sort(
  (a, b) => (data.usage.skill[b.name] ?? 0) - (data.usage.skill[a.name] ?? 0) || a.name.localeCompare(b.name)
);

function nodeKnown(id: string): boolean {
  const { kind, name } = parseNodeId(id);
  const known: Record<NodeKind, (n: string) => boolean> = {
    skill: (n) => skills.has(n),
    agent: (n) => agents.has(n),
    family: (n) => familyTools.has(n),
    tool: (n) => tools.has(n)
  };
  return known[kind](name);
}

function describedEdges(): WiringEdge[] {
  const edges: WiringEdge[] = [];
  for (const skill of data.skills) {
    const from = nodeId('skill', skill.name);
    for (const [tool, count] of Object.entries(skill.tools)) {
      edges.push({ from, to: nodeId('tool', tool), count });
    }
    for (const [agent, count] of Object.entries(skill.agents)) {
      edges.push({ from, to: nodeId('agent', agent), count });
    }
    for (const [other, count] of Object.entries(skill.skills)) {
      edges.push({ from, to: nodeId('skill', other), count });
    }
  }
  for (const agent of data.agents) {
    for (const [tool, count] of Object.entries(agent.named)) {
      edges.push({ from: nodeId('agent', agent.name), to: nodeId('tool', tool), count });
    }
  }
  return edges;
}

function observedEdges(): WiringEdge[] {
  return Object.entries(data.observed).flatMap(([from, targets]) =>
    Object.entries(targets).map(([to, count]) => ({ from, to, count }))
  );
}

const EDGES: Record<WiringMode, WiringEdge[]> = {
  described: describedEdges().filter((e) => nodeKnown(e.from) && nodeKnown(e.to)),
  observed: observedEdges().filter((e) => nodeKnown(e.from) && nodeKnown(e.to))
};

/** The map nodes a step lights up: the skill, agent or tool it calls, and who it is charged to. */
function stepNodes(step: ReplayStep): string[] {
  const ids: string[] = [];
  if (step.kind === 'skill') {
    ids.push(nodeId('skill', step.name));
  } else if (step.kind === 'agent') {
    ids.push(nodeId('agent', step.name));
  } else if (step.kind === 'jeffrey' && tools.has(step.name)) {
    ids.push(nodeId('tool', step.name));
  }
  if (step.by && step.kind !== 'other') {
    ids.push(ownerNodeId(step.by));
  }
  return ids;
}

/** The main conversation first, then each agent's lane in the order its first call appears. */
function orderedLanes(run: ReplayRun): string[] {
  const lanes = [MAIN_LANE];
  for (const step of run.steps) {
    if (!lanes.includes(step.lane)) {
      lanes.push(step.lane);
    }
  }
  return lanes;
}

/** "Main conversation", or the agent's name, numbered when the run spawned that agent more than once. */
function laneLabel(run: ReplayRun, lane: string): string {
  if (lane === MAIN_LANE) {
    return 'Main conversation';
  }
  const agent = run.lanes[lane] ?? 'agent';
  const same = orderedLanes(run).filter((other) => run.lanes[other] === agent);
  return same.length > 1 ? `${agent} #${same.indexOf(lane) + 1} (agent)` : `${agent} (agent)`;
}

/** Cases whose latest runs make at least one call the predicate accepts. */
function replaysWhere(accepts: (step: ReplayStep) => boolean): Replay[] {
  return data.replays.filter((replay) => replay.runs.some((run) => run.steps.some(accepts)));
}

// ---------- areas: JFR recordings and heap dumps, each worked by its own agent, and what both use ----------

/** Which part of the plugin a view shows: one area, with what it shares with the other. */
export type AreaLens = Exclude<AreaName, 'shared'>;

export const AREA_LENSES: AreaLens[] = ['jfr', 'heap'];

const SHARED: AreaName = 'shared';

const areas = new Map<AreaName, PluginArea>(data.areas.map((area) => [area.name, area]));

function areaIndex(pick: (area: PluginArea) => (string | null)[]): Map<string, AreaName> {
  const byName = new Map<string, AreaName>();
  for (const area of data.areas) {
    for (const name of pick(area)) {
      if (name) {
        byName.set(name, area.name);
      }
    }
  }
  return byName;
}

const skillArea = areaIndex((area) => area.skills);
const familyArea = areaIndex((area) => area.families);
const agentArea = areaIndex((area) => [area.agent]);

function areaOfNode(id: string): AreaName {
  const { kind, name } = parseNodeId(id);
  const lookup: Record<NodeKind, () => AreaName | undefined> = {
    skill: () => skillArea.get(name),
    agent: () => agentArea.get(name),
    family: () => familyArea.get(name),
    tool: () => familyArea.get(tools.get(name)?.family ?? '')
  };
  return lookup[kind]() ?? SHARED;
}

/** The area whose skills a case's runs invoke; a case that invokes only shared skills, or both areas', is shared. */
function caseArea(replay: Replay): AreaName {
  const invoked = new Set<AreaName>();
  for (const run of replay.runs) {
    for (const step of run.steps) {
      const area = step.kind === 'skill' ? skillArea.get(step.name) : undefined;
      if (area && area !== SHARED) {
        invoked.add(area);
      }
    }
  }
  return invoked.size === 1 ? [...invoked][0] : SHARED;
}

/** Whether something of this area shows under the lens: the lens's own area and the shared one do. */
function inLens(lens: AreaLens, area: AreaName): boolean {
  return area === lens || area === SHARED;
}

/** The areas a lens shows, in order: its own first, then the shared one. */
function areasFor(lens: AreaLens): PluginArea[] {
  return [lens, SHARED].flatMap((name) => areas.get(name) ?? []);
}

const SUITE_ORDER: Record<Suite, number> = { mocked: 0, live: 1 };

/** The cases of an area, as indices into the replays: the mocked ones first, then by name. */
function casesOf(area: AreaName): number[] {
  return data.replays
    .map((replay, index) => ({ replay, index }))
    .filter(({ replay }) => caseArea(replay) === area)
    .sort((a, b) => SUITE_ORDER[a.replay.suite] - SUITE_ORDER[b.replay.suite] || a.replay.case.localeCompare(b.replay.case))
    .map(({ index }) => index);
}

const MODEL = {
  data,
  tools,
  skills,
  agents,
  families,
  familyTools,
  skillsByUse,
  totals: {
    skills: data.skills.length,
    agents: data.agents.length,
    tools: data.tools.length,
    families: familyTools.size,
    runs: data.usage.runs,
    skillInvocations: sum(Object.values(data.usage.skill)),
    handOffs: sum(Object.values(data.usage.agent)),
    toolCalls: sum(Object.values(data.usage.jeffrey)),
    toolsCalled: data.tools.filter((tool) => toolCalls(tool.name) > 0).length
  },
  toolCalls,
  familyCalls,
  skillInvocations: (skill: string): number => data.usage.skill[skill] ?? 0,
  agentSpawns: (agent: string): number => data.usage.agent[agent] ?? 0,
  familyOf: (tool: string): string | undefined => tools.get(tool)?.family,
  namedBy: (tool: string): string[] => data.skills.filter((skill) => skill.tools[tool]).map((skill) => skill.name),
  agentsAllowing: (tool: string): string[] => data.agents.filter((agent) => agent.tools.includes(tool)).map((a) => a.name),
  costOrder: (cost: string | null): number => COST_ORDER[(cost ?? '').toLowerCase()] ?? UNKNOWN_COST_ORDER,
  edges: (mode: WiringMode): WiringEdge[] => EDGES[mode],
  stepNodes,
  orderedLanes,
  laneLabel,
  replaysWhere,
  areas,
  areaOfNode,
  caseArea,
  inLens,
  areasFor,
  casesOf
};

export type PluginUsageModel = typeof MODEL;

export function usePluginUsage(): PluginUsageModel {
  return MODEL;
}

const COUNT = new Intl.NumberFormat('en-US');
const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function formatCount(value: number): string {
  return COUNT.format(value);
}

export function formatUsd(value: number): string {
  return USD.format(value);
}

/** A step's offset from the run's first call, e.g. "+18.8 s". */
export function formatOffset(ms: number | null): string {
  return ms === null ? '–' : `+${(ms / 1000).toFixed(1)} s`;
}

/** The calendar day of a results file's ISO start time, e.g. "2026-11-04". */
export function formatDay(iso: string | null): string {
  return iso ? iso.slice(0, 10) : '–';
}
