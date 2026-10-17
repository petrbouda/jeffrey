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
import { summarizeInstances } from './instanceStats.ts';
import ProjectInstance, {
  type ProjectInstanceStatus
} from '@hubs/services/api/model/ProjectInstance.ts';

const BASE = 1_750_000_000_000;

function instance(
  id: string,
  status: ProjectInstanceStatus,
  createdAt: number,
  sessionCount: number
): ProjectInstance {
  return new ProjectInstance(id, id, 'project-1', status, createdAt, 0, sessionCount);
}

describe('summarizeInstances', () => {
  it('counts every status, the sessions and the oldest creation time', () => {
    const stats = summarizeInstances([
      instance('a', 'ACTIVE', BASE + 3_000, 2),
      instance('b', 'ACTIVE', BASE + 1_000, 1),
      instance('c', 'FINISHED', BASE, 4),
      instance('d', 'PENDING', BASE + 5_000, 0),
      instance('e', 'EXPIRED', BASE + 2_000, 3)
    ]);

    expect(stats).toEqual({
      pending: 1,
      active: 2,
      finished: 1,
      expired: 1,
      total: 5,
      sessions: 10,
      oldestCreatedAt: BASE
    });
  });

  it('has no oldest creation time without instances', () => {
    expect(summarizeInstances([])).toEqual({
      pending: 0,
      active: 0,
      finished: 0,
      expired: 0,
      total: 0,
      sessions: 0,
      oldestCreatedAt: null
    });
  });
});
