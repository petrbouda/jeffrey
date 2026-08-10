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
import { buildDisplayEntries, isFailedSession } from '@workspaces/services/sessionGrouping.ts';
import RecordingSession from '@workspaces/services/api/model/RecordingSession.ts';
import RecordingStatus from '@workspaces/services/api/model/RecordingStatus.ts';
import RecordingFileType from '@workspaces/services/api/model/RecordingFileType.ts';
import RepositoryFile from '@workspaces/services/api/model/RepositoryFile.ts';

const BASE_CREATED_AT = 1_750_000_000_000;

function file(size: number): RepositoryFile {
  return new RepositoryFile(
    'file-1',
    'file-1.jfr',
    BASE_CREATED_AT,
    RecordingStatus.FINISHED,
    size,
    RecordingFileType.JFR,
    true
  );
}

function session(
  id: string,
  status: RecordingStatus,
  files: RepositoryFile[],
  createdAt: number = BASE_CREATED_AT
): RecordingSession {
  const finishedAt = status === RecordingStatus.FINISHED ? createdAt + 5_000 : null;
  return new RecordingSession(id, id, 'inst-1', createdAt, finishedAt, status, 5_000, files, false);
}

describe('isFailedSession', () => {
  it('marks a finished session without files as failed', () => {
    expect(isFailedSession(session('s1', RecordingStatus.FINISHED, []))).toBe(true);
  });

  it('marks a finished session with only zero-size files as failed', () => {
    expect(isFailedSession(session('s1', RecordingStatus.FINISHED, [file(0), file(0)]))).toBe(true);
  });

  it('does not mark a finished session with data as failed', () => {
    expect(isFailedSession(session('s1', RecordingStatus.FINISHED, [file(0), file(1)]))).toBe(false);
  });

  it('does not mark an active zero-size session as failed', () => {
    expect(isFailedSession(session('s1', RecordingStatus.ACTIVE, []))).toBe(false);
  });

  it('treats unknown file sizes as zero bytes', () => {
    const unsized = file(0);
    (unsized as unknown as { size: number | null }).size = null;
    expect(isFailedSession(session('s1', RecordingStatus.FINISHED, [unsized]))).toBe(true);
  });
});

describe('buildDisplayEntries', () => {
  it('returns plain session entries when nothing failed', () => {
    const sessions = [
      session('s1', RecordingStatus.ACTIVE, [], BASE_CREATED_AT + 2_000),
      session('s2', RecordingStatus.FINISHED, [file(100)], BASE_CREATED_AT + 1_000)
    ];

    const entries = buildDisplayEntries(sessions);

    expect(entries).toHaveLength(2);
    expect(entries.every(entry => entry.type === 'session')).toBe(true);
  });

  it('folds consecutive failed sessions into a single group', () => {
    const sessions = [
      session('active', RecordingStatus.ACTIVE, [], BASE_CREATED_AT + 4_000),
      session('failed-1', RecordingStatus.FINISHED, [], BASE_CREATED_AT + 3_000),
      session('failed-2', RecordingStatus.FINISHED, [file(0)], BASE_CREATED_AT + 2_000),
      session('real', RecordingStatus.FINISHED, [file(100)], BASE_CREATED_AT + 1_000)
    ];

    const entries = buildDisplayEntries(sessions);

    expect(entries).toHaveLength(3);
    expect(entries[0].type).toBe('session');
    expect(entries[1].type).toBe('failedGroup');
    expect(entries[2].type).toBe('session');

    const group = entries[1];
    if (group.type !== 'failedGroup') {
      throw new Error('expected a failedGroup entry');
    }
    expect(group.key).toBe('failed-1');
    expect(group.sessions.map(s => s.id)).toEqual(['failed-1', 'failed-2']);
    expect(group.lastCreatedAt).toBe(BASE_CREATED_AT + 3_000);
    expect(group.firstCreatedAt).toBe(BASE_CREATED_AT + 2_000);
  });

  it('keeps non-consecutive failed sessions in separate groups', () => {
    const sessions = [
      session('failed-1', RecordingStatus.FINISHED, [], BASE_CREATED_AT + 4_000),
      session('real-1', RecordingStatus.FINISHED, [file(100)], BASE_CREATED_AT + 3_000),
      session('failed-2', RecordingStatus.FINISHED, [], BASE_CREATED_AT + 2_000)
    ];

    const entries = buildDisplayEntries(sessions);

    expect(entries.map(entry => entry.type)).toEqual(['failedGroup', 'session', 'failedGroup']);
  });

  it('wraps a single isolated failed session into a group of one', () => {
    const entries = buildDisplayEntries([session('failed-1', RecordingStatus.FINISHED, [])]);

    expect(entries).toHaveLength(1);
    expect(entries[0].type).toBe('failedGroup');
  });

  it('returns no entries for an empty list', () => {
    expect(buildDisplayEntries([])).toEqual([]);
  });
});
