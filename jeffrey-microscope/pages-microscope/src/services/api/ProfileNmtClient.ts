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
