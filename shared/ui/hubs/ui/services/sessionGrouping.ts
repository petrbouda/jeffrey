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

import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';
import RecordingStatus from '@hubs/services/api/model/RecordingStatus.ts';

/**
 * How long a session may have lived and still read as a crash, in millis. Mirrors
 * RecordingSession.CRASHED_SESSION_LIFETIME on the backend — keep the two in step.
 */
const CRASHED_SESSION_LIFETIME_MS = 60_000;

/** Total bytes across a session's files, counting an unknown size as zero. */
export function totalSizeBytes(session: RecordingSession): number {
  return session.files.reduce((sum, file) => sum + (file.size ?? 0), 0);
}

/**
 * A finished session that produced no data at all — typically a prematurely killed
 * process (OOM kill, container healthcheck restart loop). Mirrors the backend
 * predicate RecordingSession.isFailedEmpty(), including its guard: zero bytes counts as
 * a crash only when the session produced no files at all, or when it died too early to
 * have written anything. Past that window the sizes are the more likely thing to be
 * wrong — see hasUnreportedSizes.
 */
export function isFailedSession(session: RecordingSession): boolean {
  if (session.status !== RecordingStatus.FINISHED) {
    return false;
  }
  if (totalSizeBytes(session) > 0) {
    return false;
  }
  return session.files.length === 0 || session.duration < CRASHED_SESSION_LIFETIME_MS;
}

/**
 * A session holding files that all measure zero, after running long enough that the
 * process cannot be the explanation. Jeffrey reads these sizes straight off the hub's
 * repository directory, so this is what a filesystem that never refreshed them looks
 * like — an object-storage FUSE mount or an NFS mount with attribute caching serves each
 * file's size as of its creation. The files still hold their bytes and still download;
 * it is only the figures that are missing, and the UI says so rather than reporting the
 * session as a crash.
 */
export function hasUnreportedSizes(session: RecordingSession): boolean {
  return (
    session.files.length > 0 &&
    totalSizeBytes(session) === 0 &&
    session.duration >= CRASHED_SESSION_LIFETIME_MS
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
