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

// The shape of src/data/plugin-usage.json, written by build/scripts/plugin-usage-map.py.

export type StepKind = 'skill' | 'agent' | 'jeffrey' | 'other';

/** Who a call is charged to: the skill active in its lane, or the agent that owns the lane. */
export type StepOwner = ['skill' | 'agent', string];

export interface ReplayStep {
  /** Milliseconds since the run's first call; null when the trace had no timestamp. */
  t: number | null;
  /** 'main' for the conversation, or the id of the Agent call whose agent made this call. */
  lane: string;
  kind: StepKind;
  name: string;
  input: string;
  by?: StepOwner | null;
}

export interface GraderResult {
  name: string;
  passed: boolean | null;
  /** Why the grader decided as it did: the judge's reason, or what a rule matched. Absent when it gave none. */
  explanation?: string;
}

export interface ReplayRun {
  run: number;
  score: number | null;
  passed: boolean | null;
  turns: number | null;
  costUsd: number | null;
  durationSeconds: number | null;
  graders: GraderResult[];
  /** The run's last reply in the main conversation, cut for the page; absent on runs recorded before it was kept. */
  reply?: string | null;
  /** The reply's full length, in characters. */
  replyChars?: number;
  /** Lane id → 'main' or the agent's name. */
  lanes: Record<string, string>;
  steps: ReplayStep[];
}

export type Suite = 'mocked' | 'live';

export interface Replay {
  suite: Suite;
  case: string;
  prompt: string;
  /** The plugin version these runs were recorded with. */
  plugin?: string;
  at: string | null;
  runs: ReplayRun[];
}

export interface PluginTool {
  name: string;
  title: string | null;
  family: string;
  readOnly: boolean;
  destructive: boolean;
  cost: string | null;
  requires: string[];
}

export interface PluginSkill {
  name: string;
  description: string | null;
  lines: number;
  /** Tools its instructions name, with how often. */
  tools: Record<string, number>;
  skills: Record<string, number>;
  agents: Record<string, number>;
}

export interface PluginAgent {
  name: string;
  description: string | null;
  /** The tools it may call, its withheld ones already removed. */
  tools: string[];
  denied: string[];
  /** Tools its instructions name, with how often. */
  named: Record<string, number>;
  skills: Record<string, number>;
  agents: Record<string, number>;
}

/** 'jfr' and 'heap' are each worked by one agent; 'shared' is what both of them use. */
export type AreaName = 'jfr' | 'heap' | 'shared';

export interface PluginArea {
  name: AreaName;
  label: string;
  /** The agent that works this area; null for the shared one. */
  agent: string | null;
  skills: string[];
  families: string[];
}

export interface UsageTotals {
  runs: number;
  missingTraces: number;
  skill: Record<string, number>;
  agent: Record<string, number>;
  jeffrey: Record<string, number>;
  other: Record<string, number>;
}

export interface PluginWorkload {
  name: string;
  label: string;
  /** True for a real service's recorded runs, false for a workload written for the evals. */
  real: boolean;
  detail: string;
}

export interface PluginUsage {
  /** The version the newest runs were recorded with. */
  plugin: string;
  /** Every version the kept runs were recorded with, oldest first. */
  pluginVersions?: string[];
  wiringPlugin: string;
  /** Recorded agent name → current agent name; history keeps its original identities. */
  agentAliases: Record<string, string>;
  tools: PluginTool[];
  skills: PluginSkill[];
  agents: PluginAgent[];
  /** Every skill and tool family in exactly one of them. */
  areas: PluginArea[];
  /** What the eval cases run on: a real service's recorded runs, or a workload made up for the evals. */
  workloads: PluginWorkload[];
  /** Eval case name → the workload its prompt's tags declare; null for a case that reads no recording. */
  caseWorkloads: Record<string, string | null>;
  usage: UsageTotals;
  /** Node id (s:, a:) → node id (s:, a:, t:) → calls charged to the first while it was active. */
  observed: Record<string, Record<string, number>>;
  replays: Replay[];
}
