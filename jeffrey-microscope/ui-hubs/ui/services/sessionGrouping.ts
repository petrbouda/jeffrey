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

import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';
import RecordingStatus from '@hubs/services/api/model/RecordingStatus.ts';

/**
 * How long a session must have been going before all-zero sizes are worth remarking on.
 * Below this a session is simply new — its files exist and nothing has been flushed into
 * them yet, which is the normal first seconds of every recording.
 */
const UNREPORTED_SIZES_GRACE_MS = 60_000;

/** Total bytes across a session's files, counting an unknown size as zero. */
export function totalSizeBytes(session: RecordingSession): number {
  return session.files.reduce((sum, file) => sum + (file.size ?? 0), 0);
}

/**
 * A finished session that produced no data at all — typically a prematurely killed
 * process (OOM kill, container healthcheck restart loop). Mirrors the backend
 * predicate RecordingSession.isFailedEmpty().
 */
export function isFailedSession(session: RecordingSession): boolean {
  if (session.status !== RecordingStatus.FINISHED) {
    return false;
  }
  return totalSizeBytes(session) === 0;
}

/**
 * A session shown as itself — not collapsed as a crash — whose files nonetheless all
 * measure zero, after it has been going long enough to have produced something. Jeffrey
 * reads these sizes straight off the hub's repository directory, so this is what a
 * filesystem that never refreshed them looks like: an object-storage FUSE mount, or an NFS
 * mount with attribute caching, serves each file's size as of when the file was created, and
 * an SMB share lists a file another client still holds open at the size of its last flush.
 * The files still hold their bytes and still download; only the figures are missing, and
 * the UI says so rather than printing a confident zero.
 *
 * <p>Deliberately narrower than "zero bytes". A finished session with nothing in it is a
 * failed session and reads as one; what this catches is the case a crash cannot explain —
 * above all a session that is still recording.
 */
export function hasUnreportedSizes(session: RecordingSession): boolean {
  if (isFailedSession(session)) {
    return false;
  }
  return (
    session.files.length > 0 &&
    totalSizeBytes(session) === 0 &&
    session.duration >= UNREPORTED_SIZES_GRACE_MS
  );
}

export interface FailedSessionGroup {
  type: 'failedGroup';
  /** Stable key derived from the first (newest) grouped session id. */
  key: string;
  sessions: RecordingSession[];
  /** createdAt of the oldest session in the group (epoch millis). */
  firstCreatedAt: number;
  /** createdAt of the newest session in the group (epoch millis). */
  lastCreatedAt: number;
}

export interface SingleSessionEntry {
  type: 'session';
  session: RecordingSession;
}

export type SessionDisplayEntry = SingleSessionEntry | FailedSessionGroup;

/**
 * Folds consecutive failed (finished, zero-size) sessions of a createdAt-descending
 * list into collapsed group entries. A single isolated failed session also becomes
 * a group of one, so failed sessions always render through the same collapsed UI.
 */
export function buildDisplayEntries(sortedSessions: RecordingSession[]): SessionDisplayEntry[] {
  const entries: SessionDisplayEntry[] = [];
  let currentGroup: RecordingSession[] = [];

  const flushGroup = () => {
    if (currentGroup.length === 0) {
      return;
    }
    entries.push({
      type: 'failedGroup',
      key: currentGroup[0].id,
      sessions: currentGroup,
      firstCreatedAt: currentGroup[currentGroup.length - 1].createdAt,
      lastCreatedAt: currentGroup[0].createdAt
    });
    currentGroup = [];
  };

  for (const session of sortedSessions) {
    if (isFailedSession(session)) {
      currentGroup.push(session);
    } else {
      flushGroup();
      entries.push({ type: 'session', session });
    }
  }
  flushGroup();

  return entries;
}
