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
import type { ExceptionsOverview, ExceptionTypeStat } from '@/services/api/model/ExceptionsModels';

export default class ProfileExceptionsClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'exceptions');
  }

  public getOverview(): Promise<ExceptionsOverview> {
    return this.get<ExceptionsOverview>('');
  }

  public getTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/timeline');
  }

  public getTopTypes(): Promise<ExceptionTypeStat[]> {
    return this.get<ExceptionTypeStat[]>('/top-types');
  }
}
