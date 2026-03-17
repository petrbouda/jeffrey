/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import BasePlatformClient from '@/services/api/BasePlatformClient';
import JobInfo from "@/services/api/model/JobInfo.ts";

export default class GlobalSchedulerClient extends BasePlatformClient {

    constructor() {
        super('/scheduler');
    }

    create(jobType: string, params: Map<string, string>): Promise<JobInfo> {
        const content = {
            jobType: jobType,
            params: params,
        };

        return super.post<JobInfo>('', content);
    }

    all(): Promise<JobInfo[]> {
        return super.get<JobInfo[]>();
    }

    updateEnabled(jobId: string, enabled: boolean): Promise<any> {
        const content = {
            enabled: enabled,
        };

        return super.put<any>('/' + jobId + '/enabled', content);
    }

    delete(jobId: string): Promise<any> {
        return super.del<any>('/' + jobId);
    }
}
