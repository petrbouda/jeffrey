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
