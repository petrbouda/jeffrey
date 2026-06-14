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
  submitFailures: SubmitFailure[];
  lifecycle: TimeseriesData;
}
