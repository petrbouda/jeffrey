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

/** CPU values are basis points (percent x 100), e.g. 442 = 4.42%. */
export interface SystemOverview {
  avgMachineCpuBp: number;
  maxMachineCpuBp: number;
  avgJvmCpuBp: number;
  avgOtherCpuBp: number;
  maxContextSwitchRateHz: number;
  processCount: number;
  networkInterfaceCount: number;
}

export interface SystemProcessInfo {
  pid: string;
  commandLine: string;
}

export interface LaunchedProcessInfo {
  timeOffsetMillis: number;
  pid: number;
  command: string | null;
  directory: string | null;
  thread: string | null;
}

export interface ModuleEdge {
  source: string | null;
  required: string | null;
}

export interface ModuleExport {
  packageName: string | null;
  targetModule: string | null;
}
