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
import FormattingService from '@shared/services/FormattingService';
import { storageFigure, summarizeInstances } from './instanceStats.ts';
import ProjectInstance from '@remote/services/api/model/ProjectInstance.ts';

const BASE = 1_750_000_000_000;

function instance(id: string, createdAt: number | null): ProjectInstance {
  return new ProjectInstance(id, id, createdAt);
}

describe('summarizeInstances', () => {
  it('counts the instances and finds the oldest creation', () => {
    const stats = summarizeInstances([
      instance('a', BASE + 3_000),
      instance('b', BASE + 1_000),
      instance('c', BASE),
      instance('d', BASE + 5_000)
    ]);

    expect(stats).toEqual({ total: 4, oldestCreatedAt: BASE });
  });

  it('leaves out instances a share lists no time for', () => {
    expect(summarizeInstances([instance('a', null), instance('b', BASE)])).toEqual({
      total: 2,
      oldestCreatedAt: BASE
    });
    expect(summarizeInstances([instance('a', null)]).oldestCreatedAt).toBeNull();
  });

  it('has no oldest creation time without instances', () => {
    expect(summarizeInstances([])).toEqual({ total: 0, oldestCreatedAt: null });
  });
});

describe('storageFigure', () => {
  it('is loading until the statistics arrive', () => {
    expect(storageFigure(null, false)).toEqual({ kind: 'loading' });
  });

  it('reports a failed call over anything it has', () => {
    expect(storageFigure({ totalSize: 1024 }, true)).toEqual({ kind: 'error' });
  });

  it('is unmeasured when nothing has measured the project', () => {
    expect(storageFigure({ totalSize: null }, false)).toEqual({ kind: 'unmeasured' });
  });

  it('splits a measured size into number and unit', () => {
    const figure = storageFigure({ totalSize: 1024 }, false);

    expect(figure.kind).toBe('measured');
    if (figure.kind === 'measured') {
      expect(`${figure.value} ${figure.unit}`).toBe(FormattingService.formatBytes(1024));
    }
  });
});
