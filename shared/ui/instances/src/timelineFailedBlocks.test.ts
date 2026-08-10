/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import { describe, expect, it } from 'vitest';
import { splitTimelineSessions } from '@instances/timelineFailedBlocks.ts';
import ProjectInstanceSession from '@workspaces/services/api/model/ProjectInstanceSession.ts';

const BASE = 1_750_000_000_000;
const MINUTE = 60_000;

function session(
  id: string,
  createdAtOffsetMin: number,
  durationMin: number,
  failed: boolean
): ProjectInstanceSession {
  const createdAt = BASE + createdAtOffsetMin * MINUTE;
  const duration = durationMin * MINUTE;
  return new ProjectInstanceSession(
    id,
    'repo-1',
    createdAt,
    duration,
    createdAt + duration,
    false,
    failed
  );
}

describe('splitTimelineSessions', () => {
  it('returns all sessions as real when none failed', () => {
    const split = splitTimelineSessions([
      session('s1', 0, 10, false),
      session('s2', 15, 10, false)
    ]);

    expect(split.realSessions.map(s => s.id)).toEqual(['s1', 's2']);
    expect(split.failedBlocks).toEqual([]);
  });

  it('merges consecutive failed sessions into one block spanning first start to last end', () => {
    const split = splitTimelineSessions([
      session('real-1', 0, 10, false),
      session('failed-1', 12, 1, true),
      session('failed-2', 14, 1, true),
      session('failed-3', 16, 2, true),
      session('real-2', 20, 10, false)
    ]);

    expect(split.realSessions.map(s => s.id)).toEqual(['real-1', 'real-2']);
    expect(split.failedBlocks).toHaveLength(1);

    const block = split.failedBlocks[0];
    expect(block.key).toBe('failed-1');
    expect(block.sessions.map(s => s.id)).toEqual(['failed-1', 'failed-2', 'failed-3']);
    expect(block.startAt).toBe(BASE + 12 * MINUTE);
    expect(block.endAt).toBe(BASE + 18 * MINUTE);
  });

  it('keeps runs separated by a real session as distinct blocks', () => {
    const split = splitTimelineSessions([
      session('failed-1', 0, 1, true),
      session('real-1', 5, 10, false),
      session('failed-2', 20, 1, true),
      session('failed-3', 22, 1, true)
    ]);

    expect(split.failedBlocks).toHaveLength(2);
    expect(split.failedBlocks[0].sessions.map(s => s.id)).toEqual(['failed-1']);
    expect(split.failedBlocks[1].sessions.map(s => s.id)).toEqual(['failed-2', 'failed-3']);
  });

  it('sorts unordered input chronologically before grouping', () => {
    const split = splitTimelineSessions([
      session('failed-2', 14, 1, true),
      session('real-1', 0, 10, false),
      session('failed-1', 12, 1, true)
    ]);

    expect(split.realSessions.map(s => s.id)).toEqual(['real-1']);
    expect(split.failedBlocks).toHaveLength(1);
    expect(split.failedBlocks[0].sessions.map(s => s.id)).toEqual(['failed-1', 'failed-2']);
  });

  it('falls back to createdAt + duration when a failed session has no finishedAt', () => {
    const noFinish = new ProjectInstanceSession('failed-1', 'repo-1', BASE, 3 * MINUTE, undefined, false, true);

    const split = splitTimelineSessions([noFinish]);

    expect(split.failedBlocks[0].endAt).toBe(BASE + 3 * MINUTE);
  });

  it('handles an empty session list', () => {
    expect(splitTimelineSessions([])).toEqual({ realSessions: [], failedBlocks: [] });
  });
});
