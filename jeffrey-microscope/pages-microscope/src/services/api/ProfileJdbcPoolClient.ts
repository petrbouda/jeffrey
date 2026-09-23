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
import PoolData from '@/services/api/model/PoolData';
import Serie from '@/services/timeseries/model/Serie';

export default class ProfileJdbcPoolClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'jdbc/pool');
  }

  public getPoolData(): Promise<PoolData[]> {
    return this.get<PoolData[]>('');
  }

  public getTimeseries(poolName: string, eventType: string): Promise<Serie> {
    return this.post<Serie>('/timeseries', { poolName, eventType });
  }
}
