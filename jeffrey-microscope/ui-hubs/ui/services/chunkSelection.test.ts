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
import {
  chunkSelectionGap,
  chunkSelectionGapMessage,
  isContiguousChunkSelection,
  orderedChunks
} from '@hubs/services/chunkSelection.ts';
import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';
import RecordingStatus from '@hubs/services/api/model/RecordingStatus.ts';
import RecordingFileType from '@hubs/services/api/model/RecordingFileType.ts';
import RepositoryFile from '@hubs/services/api/model/RepositoryFile.ts';

const T0 = 1_750_000_000_000;
const MINUTE = 60_000;

function chunk(id: string, startMinute: number, status = RecordingStatus.FINISHED): RepositoryFile {
  return new RepositoryFile(
    id,
    `profile-${id}.jfr`,
    T0 + startMinute * MINUTE,
    status,
    1024,
    RecordingFileType.JFR,
    true
  );
}

function log(): RepositoryFile {
  return new RepositoryFile(
    'log',
    'app.log',
    T0,
    RecordingStatus.FINISHED,
    1024,
    RecordingFileType.APP_LOG,
    false
  );
}

/** Deliberately out of order, the way the backend lists them (filename descending). */
function session(...files: RepositoryFile[]): RecordingSession {
  return new RecordingSession(
    'session-1',
    'session-1',
    'inst-1',
    T0,
    T0 + 40 * MINUTE,
    RecordingStatus.FINISHED,
    40 * MINUTE,
    files,
    false
  );
}

const SESSION = session(chunk('c3', 30), chunk('c1', 10), chunk('c0', 0), chunk('c2', 20), log());

function ticked(...ids: string[]): { [fileId: string]: boolean } {
  return Object.fromEntries(ids.map(id => [id, true]));
}

describe('orderedChunks', () => {
  it('puts the finished recording files oldest first', () => {
    expect(orderedChunks(SESSION).map(f => f.id)).toEqual(['c0', 'c1', 'c2', 'c3']);
  });

  it('leaves out artifacts and the chunk still being written', () => {
    const withOpen = session(chunk('c0', 0), log(), chunk('c1', 10, RecordingStatus.ACTIVE));

    expect(orderedChunks(withOpen).map(f => f.id)).toEqual(['c0']);
  });
});

describe('isContiguousChunkSelection', () => {
  it('accepts an unbroken run', () => {
    expect(isContiguousChunkSelection(SESSION, ticked('c1', 'c2'))).toBe(true);
  });

  it('accepts every chunk', () => {
    expect(isContiguousChunkSelection(SESSION, ticked('c0', 'c1', 'c2', 'c3'))).toBe(true);
  });

  it('accepts a single chunk', () => {
    expect(isContiguousChunkSelection(SESSION, ticked('c2'))).toBe(true);
  });

  it('accepts nothing ticked, which the empty-selection check already blocks', () => {
    expect(isContiguousChunkSelection(SESSION, ticked())).toBe(true);
    expect(isContiguousChunkSelection(SESSION, undefined)).toBe(true);
  });

  it('refuses a run with a chunk skipped', () => {
    expect(isContiguousChunkSelection(SESSION, ticked('c1', 'c3'))).toBe(false);
  });

  it('refuses a run with several chunks skipped', () => {
    expect(isContiguousChunkSelection(SESSION, ticked('c0', 'c3'))).toBe(false);
  });

  it('ignores artifacts ticked beside a run', () => {
    expect(isContiguousChunkSelection(SESSION, ticked('c0', 'c1', 'log'))).toBe(true);
  });

  it('accepts an artifact on its own', () => {
    expect(isContiguousChunkSelection(SESSION, ticked('log'))).toBe(true);
  });
});

describe('chunkSelectionGap', () => {
  it('names the chunks that would close the gap', () => {
    expect(chunkSelectionGap(SESSION, ticked('c0', 'c3')).map(f => f.id)).toEqual(['c1', 'c2']);
  });

  it('is empty for an unbroken run', () => {
    expect(chunkSelectionGap(SESSION, ticked('c2', 'c3'))).toEqual([]);
  });
});

describe('chunkSelectionGapMessage', () => {
  it('names one missing file in the singular', () => {
    expect(chunkSelectionGapMessage(SESSION, ticked('c1', 'c3'))).toContain('profile-c2.jfr lies between them');
  });

  it('names several in the plural', () => {
    expect(chunkSelectionGapMessage(SESSION, ticked('c0', 'c3'))).toContain(
      'profile-c1.jfr, profile-c2.jfr lie between them'
    );
  });

  it('says nothing when there is no gap', () => {
    expect(chunkSelectionGapMessage(SESSION, ticked('c0', 'c1'))).toBe('');
  });
});
