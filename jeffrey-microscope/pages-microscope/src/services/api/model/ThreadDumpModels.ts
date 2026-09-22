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

import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

export type ThreadState =
  | 'RUNNABLE'
  | 'BLOCKED'
  | 'WAITING'
  | 'TIMED_WAITING'
  | 'NEW'
  | 'TERMINATED'
  | 'UNKNOWN';

export type ThreadLockKind = 'LOCKED' | 'WAITING_TO_LOCK' | 'WAITING_ON' | 'PARKING_TO_WAIT';

export interface ThreadDumpHeader {
  dumpCount: number;
  peakThreadCount: number;
  deadlockCount: number;
  stuckThreadCount: number;
  firstOffsetMillis: number;
  lastOffsetMillis: number;
}

export interface DumpDescriptor {
  index: number;
  timeOffsetMillis: number;
  threadCount: number;
  deadlockCount: number;
}

export interface FrameStat {
  frame: string;
  occurrences: number;
  distinctThreads: number;
}

export interface DeadlockEntry {
  dumpIndex: number;
  timeOffsetMillis: number;
  description: string;
  involvedThreads: string[];
}

export interface LockContention {
  monitorId: string;
  monitorClass: string | null;
  waiterCount: number;
  owner: string | null;
}

export interface StuckThread {
  name: string;
  state: ThreadState;
  topFrame: string;
  consecutiveDumps: number;
  stuckForMillis: number;
}

export interface HeatmapRow {
  threadName: string;
  states: (ThreadState | null)[];
}

export interface Heatmap {
  dumpOffsets: number[];
  rows: HeatmapRow[];
}

export interface ThreadDumpAnalysis {
  header: ThreadDumpHeader;
  dumps: DumpDescriptor[];
  stateTimeline: TimeseriesData;
  topFrames: FrameStat[];
  deadlocks: DeadlockEntry[];
  lockContention: LockContention[];
  stuckThreads: StuckThread[];
  heatmap: Heatmap;
}

export interface ThreadLock {
  kind: ThreadLockKind;
  monitorId: string | null;
  monitorClass: string | null;
}

export interface ParsedThread {
  name: string;
  group: string;
  state: ThreadState;
  frames: string[];
  locks: ThreadLock[];
}

export interface Deadlock {
  description: string;
  involvedThreads: string[];
}

export interface ParsedDump {
  timeOffsetMillis: number;
  threads: ParsedThread[];
  deadlocks: Deadlock[];
  rawText: string;
}
