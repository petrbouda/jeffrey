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

export interface VtHeader {
  pinningCount: number;
  totalPinnedNanos: number;
  maxPinnedNanos: number;
  submitFailedCount: number;
  startedCount: number;
  endedCount: number;
  peakLiveCount: number;
}

export interface DurationBucket {
  label: string;
  count: number;
}

export interface PinnedThreadStat {
  threadName: string;
  count: number;
  totalNanos: number;
  maxNanos: number;
}

export interface PinningReasonStat {
  reason: string;
  count: number;
  totalNanos: number;
  maxNanos: number;
}

export interface SubmitFailure {
  timeOffsetMillis: number;
  threadName: string;
  exceptionMessage: string;
}

export default interface VirtualThreadData {
  header: VtHeader;
  pinningTimeline: TimeseriesData;
  pinningDistribution: DurationBucket[];
  topPinnedThreads: PinnedThreadStat[];
  pinningReasons: PinningReasonStat[];
  submitFailures: SubmitFailure[];
  lifecycle: TimeseriesData;
}
