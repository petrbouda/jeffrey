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
import type { NmtCategory, NmtOverview } from '@/services/api/model/NmtModels';

export default class ProfileNmtClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'nmt');
  }

  public getOverview(): Promise<NmtOverview> {
    return this.get<NmtOverview>('');
  }

  public getCategories(): Promise<NmtCategory[]> {
    return this.get<NmtCategory[]>('/categories');
  }

  public getCategoryTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/category-timeline');
  }

  public getTotalTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/total-timeline');
  }

  public getRssVsTracked(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/rss-vs-tracked');
  }
}
