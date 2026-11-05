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

describe('Historical agent aliases', () => {
  it('counts recorded delegations against their current map nodes', () => {
    expect(model.agentSpawns('jfr-analyst')).toBe(13);
    expect(model.agentSpawns('heap-analyst')).toBe(6);
  });

  it('links observed calls to current agents while keeping recorded names intact', () => {
    expect(model.edges('observed')).toContainEqual({
      from: 'a:jfr-analyst', to: 't:flamegraph_export', count: 28
    });
    const replay = model.data.replays.find((item) => item.case === 'wall-vs-cpu')!;
    const delegation = replay.runs[1].steps.find((step) => step.kind === 'agent')!;
    // A run recorded under the agent's earlier name lands on the same node; the step keeps the name it was given.
    const recorded = { ...delegation, name: 'profile-analyst' };
    expect(recorded.name).toBe('profile-analyst');
    expect(model.stepNodes(recorded)).toContain('a:jfr-analyst');
  });
});
