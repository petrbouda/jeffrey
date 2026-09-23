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
  BlockingOverview,
  ContentionStat,
  MonitorWaitStat,
  PinnedThreadEntry,
  SleepStat
} from '@/services/api/model/BlockingModels';

export default class ProfileBlockingOperationsClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'blocking-operations');
  }

  public getOverview(): Promise<BlockingOverview> {
    return this.get<BlockingOverview>('');
  }

  public getTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/timeline');
  }

  public getMonitors(): Promise<ContentionStat[]> {
    return this.get<ContentionStat[]>('/monitors');
  }

  public getMonitorWaits(): Promise<MonitorWaitStat[]> {
    return this.get<MonitorWaitStat[]>('/monitor-waits');
  }

  public getParks(): Promise<ContentionStat[]> {
    return this.get<ContentionStat[]>('/parks');
  }

  public getSleeps(): Promise<SleepStat[]> {
    return this.get<SleepStat[]>('/sleeps');
  }

  public getPinned(): Promise<PinnedThreadEntry[]> {
    return this.get<PinnedThreadEntry[]>('/pinned');
  }
}
