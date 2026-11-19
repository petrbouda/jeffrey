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
  NativeLibraryInfo,
  NativeMemoryOverview
} from '@/services/api/model/NativeMemoryModels';
import type { NativeLibraryActivityData } from '@/services/api/model/NativeLibraryActivityModels';

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

  public getNativeLibraryActivity(): Promise<NativeLibraryActivityData> {
    return this.get<NativeLibraryActivityData>('/library-activity');
  }
}
