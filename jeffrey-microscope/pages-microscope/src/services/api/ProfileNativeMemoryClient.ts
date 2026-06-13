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
import type { NativeLibraryInfo, NativeMemoryOverview } from '@/services/api/model/NativeMemoryModels';

export default class ProfileNativeMemoryClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'native-memory');
  }

  public getOverview(): Promise<NativeMemoryOverview> {
    return this.get<NativeMemoryOverview>('');
  }

  public getRssTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/timeline');
  }

  public getDirectBufferTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/direct-buffers/timeline');
  }

  public getNativeLibraries(): Promise<NativeLibraryInfo[]> {
    return this.get<NativeLibraryInfo[]>('/native-libraries');
  }
}
