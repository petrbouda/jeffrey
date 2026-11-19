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
import HttpOverviewData from '@/services/api/model/HttpOverviewData';
import HttpSingleUriData from '@/services/api/model/HttpSingleUriData';

export default class ProfileHttpClient extends BaseProfileClient {
  private readonly mode: string;

  constructor(mode: 'client' | 'server', profileId: string) {
    super(profileId, 'http/overview');
    this.mode = mode;
  }

  public getOverview(): Promise<HttpOverviewData> {
    return super.get<HttpOverviewData>('', { mode: this.mode });
  }

  public getOverviewUri(uri: string | null): Promise<HttpSingleUriData> {
    return super.get<HttpSingleUriData>('/single', { uri, mode: this.mode });
  }
}
