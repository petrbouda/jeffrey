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
import type { MmuEntry } from '@/services/api/model/GCTuningModels';

export interface G1Header {
  youngCount: number;
  mixedCount: number;
  fullCount: number;
  totalPauseNanos: number;
  avgPauseNanos: number;
  maxPauseNanos: number;
  p99PauseNanos: number;
  evacuationFailureCount: number;
  regionCount: number;
}

export interface PausePhase {
  name: string;
  level: number;
  count: number;
  totalNanos: number;
  maxNanos: number;
  avgNanos: number;
}

export interface RegionCell {
  index: number;
  type: string;
  usedBytes: number;
}

export interface RegionSnapshot {
  timeOffsetMillis: number;
  regions: RegionCell[];
}

export interface EvacuationEntry {
  gcId: number;
  cSetRegions: number;
  cSetUsedBefore: number;
  cSetUsedAfter: number;
  allocationRegions: number;
  bytesCopied: number;
  regionsFreed: number;
}

export interface EvacuationFailure {
  gcId: number;
  count: number;
}

export interface SystemGcEntry {
  timeOffsetMillis: number;
  durationNanos: number;
  invokedConcurrent: boolean;
}

export interface GcLockerEntry {
  timeOffsetMillis: number;
  durationNanos: number;
  lockCount: number;
  stallCount: number;
}

export default interface G1AnalysisData {
  header: G1Header;
  pausePhases: PausePhase[];
  regionComposition: TimeseriesData;
  regionSnapshots: RegionSnapshot[];
  evacuations: EvacuationEntry[];
  evacuationFailures: EvacuationFailure[];
  ihopTimeline: TimeseriesData;
  mmu: MmuEntry[];
  systemGcs: SystemGcEntry[];
  gcLockers: GcLockerEntry[];
}
