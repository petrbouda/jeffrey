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
