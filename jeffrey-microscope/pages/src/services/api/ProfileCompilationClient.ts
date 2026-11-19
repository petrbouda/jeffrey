/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import JITCompilationData from '@/services/api/model/JITCompilationData';
import JITLongCompilation from '@/services/api/model/JITLongCompilation';
import Serie from '@/services/timeseries/model/Serie';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import type { CodeCacheData } from '@/services/api/model/CodeCacheModels';

export default class ProfileCompilationClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'compilation');
  }

  public getStatistics(): Promise<JITCompilationData> {
    return this.get<JITCompilationData>('/statistics');
  }

  public getCompilations(): Promise<JITLongCompilation[]> {
    return this.get<JITLongCompilation[]>('/compilations');
  }

  public getTimeseries(): Promise<Serie> {
    return this.get<Serie>('/timeseries');
  }

  public getQueueTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/queue-timeline');
  }

  public getCodeCache(): Promise<CodeCacheData> {
    return this.get<CodeCacheData>('/code-cache');
  }
}
