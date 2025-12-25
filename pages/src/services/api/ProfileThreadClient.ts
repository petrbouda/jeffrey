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
import ThreadResponse from '@/services/api/model/ThreadResponse';
import ThreadStatisticsResponse from '@/services/api/model/ThreadStatisticsResponse';
import Serie from '@/services/timeseries/model/Serie';

export default class ProfileThreadClient extends BaseProfileClient {

    constructor(workspaceId: string, projectId: string, profileId: string) {
        super(workspaceId, projectId, profileId, 'thread');
    }

    public list(): Promise<ThreadResponse> {
        return this.get<ThreadResponse>('');
    }

    public statistics(): Promise<ThreadStatisticsResponse> {
        return this.get<ThreadStatisticsResponse>('/statistics');
    }

    public timeseries(): Promise<Serie> {
        return this.get<Serie>('/timeseries');
    }
}
