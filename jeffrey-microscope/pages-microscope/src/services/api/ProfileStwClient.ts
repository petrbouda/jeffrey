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
import type { StwEvent } from '@/services/api/model/stw/StwModels';

export default class ProfileStwClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'stw');
  }

  public getTimeline(minDurationNanos?: number): Promise<StwEvent[]> {
    const params = minDurationNanos === undefined ? undefined : { minDurationNanos };
    return this.get<StwEvent[]>('/timeline', params);
  }

  public getBudget(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/budget');
  }
}
