/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import JdbcOverviewData from '@/services/api/model/JdbcOverviewData';
import JdbcSlowStatement from '@/services/api/model/JdbcSlowStatement';
import Serie from '@/services/timeseries/model/Serie';

export default class ProfileJdbcStatementClient extends BaseProfileClient {

    constructor(workspaceId: string, projectId: string, profileId: string) {
        super(workspaceId, projectId, profileId, 'jdbc/statement/overview');
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
