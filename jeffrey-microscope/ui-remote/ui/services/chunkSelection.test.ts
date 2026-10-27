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
  downloadableFiles,
  isContiguousChunkSelection,
  isDownloadable,
  orderedChunks
} from '@remote/services/chunkSelection.ts';
import RecordingFileType from '@remote/services/api/model/RecordingFileType.ts';
import RepositoryFile from '@remote/services/api/model/RepositoryFile.ts';

const T0 = 1_750_000_000_000;
const MINUTE = 60_000;

function chunk(id: string, startMinute: number, newestRecording = false): RepositoryFile {
  return new RepositoryFile(
    id,
    `profile-${id}.jfr`,
    T0 + startMinute * MINUTE,
    1024,
    RecordingFileType.JFR,
    newestRecording,
    true
  );
}

function log(): RepositoryFile {
  return new RepositoryFile(
    'log',
    'app.log',
    T0,
    1024,
    RecordingFileType.APP_LOG,
    false,
    false
  );
}

function asprofTemp(): RepositoryFile {
  return new RepositoryFile(
    'asprof',
    'profile-open.jfr.1~',
    T0,
    1024,
    RecordingFileType.ASPROF,
    false,
    false
  );
}

/** Deliberately out of order, the way the backend lists them (filename descending). */
function filesOf(...files: RepositoryFile[]): RepositoryFile[] {
  return files;
}

const FILES = filesOf(chunk('c3', 30), chunk('c1', 10), chunk('c0', 0), chunk('c2', 20), log());

function ticked(...ids: string[]): { [fileId: string]: boolean } {
  return Object.fromEntries(ids.map(id => [id, true]));
}

describe('orderedChunks', () => {
  it('puts the recording files oldest first', () => {
    expect(orderedChunks(FILES).map(f => f.id)).toEqual(['c0', 'c1', 'c2', 'c3']);
  });

  it('leaves out artifacts and keeps the newest recording', () => {
    const withNewest = filesOf(chunk('c0', 0), log(), chunk('c1', 10, true));

    expect(orderedChunks(withNewest).map(f => f.id)).toEqual(['c0', 'c1']);
  });
});

describe('isContiguousChunkSelection', () => {
  it('accepts an unbroken run', () => {
    expect(isContiguousChunkSelection(FILES, ticked('c1', 'c2'))).toBe(true);
  });

  it('accepts every chunk', () => {
    expect(isContiguousChunkSelection(FILES, ticked('c0', 'c1', 'c2', 'c3'))).toBe(true);
  });

  it('accepts a single chunk', () => {
    expect(isContiguousChunkSelection(FILES, ticked('c2'))).toBe(true);
  });

  it('accepts nothing ticked, which the empty-selection check already blocks', () => {
    expect(isContiguousChunkSelection(FILES, ticked())).toBe(true);
    expect(isContiguousChunkSelection(FILES, undefined)).toBe(true);
  });

  it('refuses a run with a chunk skipped', () => {
    expect(isContiguousChunkSelection(FILES, ticked('c1', 'c3'))).toBe(false);
  });

  it('refuses a run with several chunks skipped', () => {
    expect(isContiguousChunkSelection(FILES, ticked('c0', 'c3'))).toBe(false);
  });

  it('ignores artifacts ticked beside a run', () => {
    expect(isContiguousChunkSelection(FILES, ticked('c0', 'c1', 'log'))).toBe(true);
  });

  it('accepts an artifact on its own', () => {
    expect(isContiguousChunkSelection(FILES, ticked('log'))).toBe(true);
  });
});

describe('chunkSelectionGap', () => {
  it('names the chunks that would close the gap', () => {
    expect(chunkSelectionGap(FILES, ticked('c0', 'c3')).map(f => f.id)).toEqual(['c1', 'c2']);
  });

  it('is empty for an unbroken run', () => {
    expect(chunkSelectionGap(FILES, ticked('c2', 'c3'))).toEqual([]);
  });
});

describe('chunkSelectionGapMessage', () => {
  it('names one missing file in the singular', () => {
    expect(chunkSelectionGapMessage(FILES, ticked('c1', 'c3'))).toContain('profile-c2.jfr lies between them');
  });

  it('names several in the plural', () => {
    expect(chunkSelectionGapMessage(FILES, ticked('c0', 'c3'))).toContain(
      'profile-c1.jfr, profile-c2.jfr lie between them'
    );
  });

  it('says nothing when there is no gap', () => {
    expect(chunkSelectionGapMessage(FILES, ticked('c0', 'c1'))).toBe('');
  });
});

describe('isDownloadable', () => {
  it('takes a chunk', () => {
    expect(isDownloadable(chunk('c0', 0))).toBe(true);
  });

  it('takes the newest recording of the newest session', () => {
    expect(isDownloadable(chunk('newest', 5, true))).toBe(true);
  });

  it("leaves out async-profiler's scratch file", () => {
    expect(isDownloadable(asprofTemp())).toBe(false);
  });

  it('takes an artifact', () => {
    expect(isDownloadable(log())).toBe(true);
  });
});

describe('downloadableFiles', () => {
  it('keeps every file of the newest session but the scratch file, in listing order', () => {
    const files = filesOf(asprofTemp(), chunk('newest', 2, true), chunk('c1', 1), chunk('c0', 0), log());

    expect(downloadableFiles(files).map(f => f.id)).toEqual(['newest', 'c1', 'c0', 'log']);
  });

  it('is empty when only the scratch file exists', () => {
    expect(downloadableFiles(filesOf(asprofTemp()))).toEqual([]);
  });
});
