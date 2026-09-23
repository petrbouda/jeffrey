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
import JdbcOverviewData from '@/services/api/model/JdbcOverviewData';
import JdbcSlowStatement from '@/services/api/model/JdbcSlowStatement';
import Serie from '@/services/timeseries/model/Serie';

export default class ProfileJdbcStatementClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'jdbc/statement/overview');
  }

  public getOverview(): Promise<JdbcOverviewData> {
    return this.get<JdbcOverviewData>('');
  }

  public getOverviewGroup(group: string | null): Promise<JdbcOverviewData> {
    return this.get<JdbcOverviewData>('/single', { group });
  }

  public getTimeseries(group: string, statementName: string): Promise<Serie[]> {
    return this.get<Serie[]>('/timeseries', { group, statementName });
  }

  public getSlowestStatements(group: string, statementName: string): Promise<JdbcSlowStatement[]> {
    return this.get<JdbcSlowStatement[]>('/slowest', { group, statementName });
  }
}
