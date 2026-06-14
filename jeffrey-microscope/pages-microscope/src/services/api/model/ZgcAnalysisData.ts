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

export interface ZgcHeader {
  youngCycles: number;
  oldCycles: number;
  stallCount: number;
  totalStallNanos: number;
  maxStallNanos: number;
  pagesAllocatedBytes: number;
  uncommittedBytes: number;
}

export interface StallType {
  type: string;
  count: number;
  totalNanos: number;
  maxNanos: number;
}

export interface StallSite {
  threadName: string;
  count: number;
  totalNanos: number;
}

export interface ZCycle {
  gcId: number;
  generation: string;
  durationNanos: number;
  tenuringThreshold: number;
}

export interface ZUncommitEntry {
  timeOffsetMillis: number;
  uncommittedBytes: number;
  durationNanos: number;
}

export interface ZRelocationEntry {
  timeOffsetMillis: number;
  total: number;
  empty: number;
  relocate: number;
}

export default interface ZgcAnalysisData {
  header: ZgcHeader;
  stallTimeline: TimeseriesData;
  stallTypes: StallType[];
  stallSites: StallSite[];
  cycles: ZCycle[];
  pageAllocation: TimeseriesData;
  uncommits: ZUncommitEntry[];
  relocations: ZRelocationEntry[];
}
