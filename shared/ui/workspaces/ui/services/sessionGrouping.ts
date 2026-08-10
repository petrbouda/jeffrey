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

import RecordingSession from '@workspaces/services/api/model/RecordingSession.ts';
import RecordingStatus from '@workspaces/services/api/model/RecordingStatus.ts';

/**
 * A finished session that produced no data at all — typically a prematurely killed
 * process (OOM kill, container healthcheck restart loop). Mirrors the backend
 * predicate RecordingSession.isFailedEmpty().
 */
export function isFailedSession(session: RecordingSession): boolean {
  if (session.status !== RecordingStatus.FINISHED) {
    return false;
  }
  return session.files.reduce((sum, file) => sum + (file.size ?? 0), 0) === 0;
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
