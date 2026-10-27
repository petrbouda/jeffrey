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

import ProjectInstanceSession from '@hubs/services/api/model/ProjectInstanceSession.ts';

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
