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

import ProjectInstanceSession from '@workspaces/services/api/model/ProjectInstanceSession.ts';

/**
 * A run of consecutive failed sessions in an instance's timeline, merged into
 * one renderable block spanning from the first failure's start to the last
 * failure's end.
 */
export interface FailedSessionBlock {
  /** Stable key derived from the first (oldest) grouped session id. */
  key: string;
  sessions: ProjectInstanceSession[];
  /** Start of the block: createdAt of the oldest failed session (epoch millis). */
  startAt: number;
  /** End of the block: end of the newest failed session (epoch millis). */
  endAt: number;
}

export interface TimelineSessionSplit {
  /** Sessions rendered as regular bars, in the input's order. */
  realSessions: ProjectInstanceSession[];
  /** Consecutive failed sessions merged into blocks, oldest first. */
  failedBlocks: FailedSessionBlock[];
}

function sessionEnd(session: ProjectInstanceSession): number {
  return session.finishedAt ?? session.createdAt + session.duration;
}

/**
 * Splits an instance's sessions into real sessions and merged failed blocks.
 * Consecutiveness is evaluated in chronological order (by createdAt): a run of
 * failed sessions not interrupted by a real one folds into a single block.
 */
export function splitTimelineSessions(sessions: ProjectInstanceSession[]): TimelineSessionSplit {
  const chronological = [...sessions].sort((a, b) => a.createdAt - b.createdAt);

  const realSessions: ProjectInstanceSession[] = [];
  const failedBlocks: FailedSessionBlock[] = [];
  let currentRun: ProjectInstanceSession[] = [];

  const flushRun = () => {
    if (currentRun.length === 0) {
      return;
    }
    failedBlocks.push({
      key: currentRun[0].id,
      sessions: currentRun,
      startAt: currentRun[0].createdAt,
      endAt: sessionEnd(currentRun[currentRun.length - 1])
    });
    currentRun = [];
  };

  for (const session of chronological) {
    if (session.failed) {
      currentRun.push(session);
    } else {
      flushRun();
      realSessions.push(session);
    }
  }
  flushRun();

  return { realSessions, failedBlocks };
}
