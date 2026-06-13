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

export interface TenuringAgeBucket {
  age: number;
  sizeBytes: number;
}

export interface TenuringGcSummary {
  gcId: number;
  totalSizeBytes: number;
  buckets: TenuringAgeBucket[];
}

export interface ReferenceStat {
  type: string;
  totalCount: number;
}

export interface TenuringData {
  gcs: TenuringGcSummary[];
  referenceStats: ReferenceStat[];
}

export interface GcCpuEntry {
  gcId: number;
  userNanos: number;
  systemNanos: number;
  realNanos: number;
}

export interface MmuEntry {
  gcId: number;
  gcTimeNanos: number;
  pauseTargetNanos: number;
}

export interface IhopData {
  ihopTimeline: TimeseriesData;
  cpuTimes: GcCpuEntry[];
  mmu: MmuEntry[];
}
