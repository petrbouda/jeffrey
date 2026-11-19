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
  ClassLoadActivity,
  ClassLoaderStat,
  ClassLoadingOverview,
  RedefinitionData
} from '@/services/api/model/ClassLoadingModels';

export default class ProfileClassLoadingClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'class-loading');
  }

  public getOverview(): Promise<ClassLoadingOverview> {
    return this.get<ClassLoadingOverview>('');
  }

  public getTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/timeline');
  }

  public getClassLoaders(): Promise<ClassLoaderStat[]> {
    return this.get<ClassLoaderStat[]>('/class-loaders');
  }

  public getClassLoads(): Promise<ClassLoadActivity> {
    return this.get<ClassLoadActivity>('/class-loads');
  }

  public getRedefinitions(): Promise<RedefinitionData> {
    return this.get<RedefinitionData>('/redefinitions');
  }
}
