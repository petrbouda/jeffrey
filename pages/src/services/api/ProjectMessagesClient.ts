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

import GlobalVars from '@/services/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/services/HttpUtils';
import type { ImportantMessage } from '@/services/api/model/ImportantMessage';

export default class ProjectMessagesClient {

    private baseUrl: string;

    constructor(workspaceId: string, projectId: string) {
        this.baseUrl = GlobalVars.internalUrl + "/workspaces/" + workspaceId + '/projects/' + projectId + '/messages';
    }

    /**
     * Retrieves all messages within an optional time range.
     * @param startMillis Optional start time in epoch milliseconds
     * @param endMillis Optional end time in epoch milliseconds
     */
    async getMessages(startMillis?: number, endMillis?: number): Promise<ImportantMessage[]> {
        const params: Record<string, number> = {};
        if (startMillis !== undefined) params.start = startMillis;
        if (endMillis !== undefined) params.end = endMillis;

        return axios.get<ImportantMessage[]>(this.baseUrl, {
            ...HttpUtils.JSON_ACCEPT_HEADER,
            params
        }).then(HttpUtils.RETURN_DATA);
    }

    /**
     * Retrieves only alert messages within an optional time range.
     * @param startMillis Optional start time in epoch milliseconds
     * @param endMillis Optional end time in epoch milliseconds
     */
    async getAlerts(startMillis?: number, endMillis?: number): Promise<ImportantMessage[]> {
        const params: Record<string, number> = {};
        if (startMillis !== undefined) params.start = startMillis;
        if (endMillis !== undefined) params.end = endMillis;

        return axios.get<ImportantMessage[]>(this.baseUrl + '/alerts', {
            ...HttpUtils.JSON_ACCEPT_HEADER,
            params
        }).then(HttpUtils.RETURN_DATA);
    }
}
