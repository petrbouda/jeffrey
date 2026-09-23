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

export type LibraryOperationKind = 'LOAD' | 'UNLOAD';

export interface NativeLibraryActivityHeader {
  totalLoads: number;
  failedLoads: number;
  totalUnloads: number;
  slowestLoadNanos: number;
  totalLoadNanos: number;
  slowestLibrary: string | null;
}

export interface LibraryOperation {
  operation: LibraryOperationKind;
  name: string;
  timeOffsetMillis: number;
  durationNanos: number;
  success: boolean;
  errorMessage: string | null;
}

export interface NativeLibraryActivityData {
  header: NativeLibraryActivityHeader;
  operations: LibraryOperation[];
  timeline: TimeseriesData;
}
