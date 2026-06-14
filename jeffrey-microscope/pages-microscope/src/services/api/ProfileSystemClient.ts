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
