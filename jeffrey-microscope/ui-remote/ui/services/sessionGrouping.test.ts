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
import { hasUnreportedSizes, isFailedSession } from '@remote/services/sessionGrouping.ts';
import RecordingSession from '@remote/services/api/model/RecordingSession.ts';
import RecordingFileType from '@remote/services/api/model/RecordingFileType.ts';
import RepositoryFile from '@remote/services/api/model/RepositoryFile.ts';

const BASE_CREATED_AT = 1_750_000_000_000;

function file(size: number): RepositoryFile {
  return new RepositoryFile(
    'file-1',
    'file-1.jfr',
    BASE_CREATED_AT,
    size,
    RecordingFileType.JFR,
    false,
    true
  );
}

/** A session only seconds old — nothing has been flushed into its files yet. */
const SHORT_DURATION = 5_000;
/** A session going long enough that all-zero sizes are worth remarking on. */
const LONG_DURATION = 600_000;

/** Its instance's newest session, which may still grow. */
const NEWEST = true;
/** A session a newer one in its instance has ended. */
const ENDED = false;

function session(
  id: string,
  newest: boolean,
  files: RepositoryFile[],
  createdAt: number = BASE_CREATED_AT,
  duration: number = SHORT_DURATION
): RecordingSession {
  // As a listing sends it: the files' count and size, never the files themselves
  const sizeBytes = files.reduce((sum, f) => sum + (f.size ?? 0), 0);
  return new RecordingSession(
    id,
    id,
    'inst-1',
    createdAt,
    createdAt + duration,
    newest,
    duration,
    [],
    false,
    files.length,
    sizeBytes
  );
}

describe('isFailedSession', () => {
  it('marks an ended session without files as failed', () => {
    expect(isFailedSession(session('s1', ENDED, []))).toBe(true);
  });

  it('marks an ended session with only zero-size files as failed', () => {
    expect(isFailedSession(session('s1', ENDED, [file(0), file(0)]))).toBe(true);
  });

  it('does not mark an ended session with data as failed', () => {
    expect(isFailedSession(session('s1', ENDED, [file(0), file(1)]))).toBe(false);
  });

  it('does not mark the newest zero-size session as failed', () => {
    expect(isFailedSession(session('s1', NEWEST, []))).toBe(false);
  });

  it('treats unknown file sizes as zero bytes', () => {
    const unsized = file(0);
    (unsized as unknown as { size: number | null }).size = null;
    expect(isFailedSession(session('s1', ENDED, [unsized]))).toBe(true);
  });

  it('marks a long ended session with no data as failed, however long it ran', () => {
    const long = session('s1', ENDED, [file(0)], BASE_CREATED_AT, LONG_DURATION);
    expect(isFailedSession(long)).toBe(true);
  });
});

describe('hasUnreportedSizes', () => {
  it('flags the newest session whose files all measure zero', () => {
    const running = session('s1', NEWEST, [file(0)], BASE_CREATED_AT, LONG_DURATION);
    expect(hasUnreportedSizes(running)).toBe(true);
  });

  it('does not flag a session that just started', () => {
    const fresh = session('s1', NEWEST, [file(0)], BASE_CREATED_AT, SHORT_DURATION);
    expect(hasUnreportedSizes(fresh)).toBe(false);
  });

  it('does not flag a failed session — a crash is the explanation there', () => {
    const failed = session('s1', ENDED, [file(0)], BASE_CREATED_AT, LONG_DURATION);
    expect(hasUnreportedSizes(failed)).toBe(false);
  });

  it('does not flag a session that reported data', () => {
    const real = session('s1', NEWEST, [file(100)], BASE_CREATED_AT, LONG_DURATION);
    expect(hasUnreportedSizes(real)).toBe(false);
  });

  it('does not flag the newest session with no files — there is nothing to report on', () => {
    const empty = session('s1', NEWEST, [], BASE_CREATED_AT, LONG_DURATION);
    expect(hasUnreportedSizes(empty)).toBe(false);
  });
});
