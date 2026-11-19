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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import type {
  LaunchedProcessInfo,
  ModuleEdge,
  ModuleExport,
  SystemOverview,
  SystemProcessInfo
} from '@/services/api/model/SystemModels';

export default class ProfileSystemClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'system');
  }

  public getOverview(): Promise<SystemOverview> {
    return this.get<SystemOverview>('');
  }

  public getCpuTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/cpu/timeline');
  }

  public getNetworkInterfaces(): Promise<string[]> {
    return this.get<string[]>('/network/interfaces');
  }

  public getNetworkTimeline(networkInterface: string): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/network/timeline', { networkInterface });
  }

  public getContextSwitchTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/context-switches/timeline');
  }

  public getProcesses(): Promise<SystemProcessInfo[]> {
    return this.get<SystemProcessInfo[]>('/processes');
  }

  public getSwapTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/swap/timeline');
  }

  public getLaunchedProcesses(): Promise<LaunchedProcessInfo[]> {
    return this.get<LaunchedProcessInfo[]>('/launched-processes');
  }

  public getModuleRequires(): Promise<ModuleEdge[]> {
    return this.get<ModuleEdge[]>('/modules/requires');
  }

  public getModuleExports(): Promise<ModuleExport[]> {
    return this.get<ModuleExport[]>('/modules/exports');
  }
}
