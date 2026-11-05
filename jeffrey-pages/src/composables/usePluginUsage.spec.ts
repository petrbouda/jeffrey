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

import { describe, expect, it } from 'vitest';
import { usePluginUsage } from './usePluginUsage';

const model = usePluginUsage();

// What the recorded runs hold changes with every suite run, so these read the expected figures off the raw
// data and check the model's reading of it — aliases resolved, observed calls charged — not one run's numbers.
const recordedSpawns = (agent: string): number => Object.entries(model.data.usage.agent)
  .filter(([name]) => (model.data.agentAliases[name] ?? name) === agent)
  .reduce((sum, [, count]) => sum + count, 0);

describe('Historical agent aliases', () => {
  it('counts recorded delegations against their current map nodes', () => {
    expect(model.agentSpawns('jfr-analyst')).toBe(recordedSpawns('jfr-analyst'));
    expect(model.agentSpawns('heap-analyst')).toBe(recordedSpawns('heap-analyst'));
    expect(model.agentSpawns('jfr-analyst')).toBeGreaterThan(0);
  });

  it('links observed calls to current agents while keeping recorded names intact', () => {
    const charged = Object.entries(model.data.observed)
      .filter(([from]) => from.startsWith('a:') && (model.data.agentAliases[from.slice(2)] ?? from.slice(2)) === 'jfr-analyst')
      .reduce((sum, [, targets]) => sum + (targets['t:flamegraph_export'] ?? 0), 0);
    expect(charged).toBeGreaterThan(0);
    expect(model.edges('observed')).toContainEqual({ from: 'a:jfr-analyst', to: 't:flamegraph_export', count: charged });
    const replay = model.data.replays.find((item) => item.runs.some((run) => run.steps.some((step) => step.kind === 'agent'
      && model.agentName(step.name) === 'jfr-analyst')))!;
    const delegation = replay.runs.flatMap((run) => run.steps).find((step) => step.kind === 'agent'
      && model.agentName(step.name) === 'jfr-analyst')!;
    // A run recorded under the agent's earlier name lands on the same node; the step keeps the name it was given.
    const recorded = { ...delegation, name: 'profile-analyst' };
    expect(recorded.name).toBe('profile-analyst');
    expect(model.stepNodes(recorded)).toContain('a:jfr-analyst');
  });
});
