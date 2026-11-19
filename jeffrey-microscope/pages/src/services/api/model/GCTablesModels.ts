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

export interface StringSymbolTablesHeader {
  peakStringEntries: number;
  peakStringFootprint: number;
  peakSymbolEntries: number;
  peakSymbolFootprint: number;
}

export interface StringDeduplicationData {
  cycles: number;
  totalInspected: number;
  totalDeduplicated: number;
  totalNewStrings: number;
  totalBytesSaved: number;
  timeline: TimeseriesData;
}

export interface StringSymbolTablesData {
  header: StringSymbolTablesHeader;
  entries: TimeseriesData;
  footprint: TimeseriesData;
  deduplication: StringDeduplicationData;
}

export interface FinalizersHeader {
  classCount: number;
  totalPendingObjects: number;
  totalFinalizersRun: number;
}

export interface FinalizerClassStat {
  className: string;
  codeSource: string;
  peakObjects: number;
  finalizersRun: number;
}

export interface FinalizersData {
  header: FinalizersHeader;
  classes: FinalizerClassStat[];
}
