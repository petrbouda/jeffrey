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
