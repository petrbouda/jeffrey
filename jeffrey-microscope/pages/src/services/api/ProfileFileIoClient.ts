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
  FileForceStats,
  IoEndpoint,
  IoOperation,
  IoOverview
} from '@/services/api/model/IoModels';

export default class ProfileFileIoClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'io/file');
  }

  public getOverview(): Promise<IoOverview> {
    return this.get<IoOverview>('');
  }

  public getTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/timeline');
  }

  public getSlowest(): Promise<IoOperation[]> {
    return this.get<IoOperation[]>('/slowest');
  }

  public getFiles(): Promise<IoEndpoint[]> {
    return this.get<IoEndpoint[]>('/endpoints');
  }

  public getDirectories(): Promise<IoEndpoint[]> {
    return this.get<IoEndpoint[]>('/directories');
  }

  public getForce(): Promise<FileForceStats> {
    return this.get<FileForceStats>('/force');
  }
}
