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

export type ThrottlingSeverity = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'NOT_APPLICABLE';

export interface ThrottlingVerdict {
  throttled: boolean;
  severity: ThrottlingSeverity;
  title: string;
  description: string;
}

export interface ThrottlingSummary {
  elapsedPeriods: number;
  throttledPeriods: number;
  throttledTimeMillis: number;
  overallRatioPct: number;
  peakRatioPct: number;
  cpuLimitCores: number | null;
  cfsPeriodMillis: number | null;
  effectiveCpuCount: number | null;
}

export interface ThrottlingPoint {
  timestampMillis: number;
  elapsedPeriodsDelta: number;
  throttledPeriodsDelta: number;
  throttledTimeMillisDelta: number;
  ratioPct: number;
}

export interface ThrottledWindow {
  startMillis: number;
  endMillis: number;
  throttledPeriods: number;
  throttledTimeMillis: number;
  ratioPct: number;
}

export default interface ContainerCpuThrottlingData {
  verdict: ThrottlingVerdict;
  summary: ThrottlingSummary;
  timeseries: ThrottlingPoint[];
  windows: ThrottledWindow[];
}
