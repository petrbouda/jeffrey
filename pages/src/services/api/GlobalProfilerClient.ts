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

import GlobalVars from '@/services/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/services/HttpUtils';
import ProfilerSettings from "@/services/api/model/ProfilerSettings.ts";

/**
 * Client for global profiler settings API.
 * Used for managing settings at global, workspace, or project level from a central location.
 */
export default class GlobalProfilerClient {
    private static baseUrl: string = GlobalVars.internalUrl + '/profiler/settings'

    /**
     * Upsert settings at any level (global, workspace, or project)
     */
    static upsert(workspaceId: string | null, projectId: string | null, agentSettings: string): Promise<void> {
        const content = {
            workspaceId: workspaceId,
            projectId: projectId,
            agentSettings: agentSettings,
        };

        return axios.post(GlobalProfilerClient.baseUrl, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    /**
     * Fetch all settings across all levels
     */
    static fetchAll(): Promise<ProfilerSettings[]> {
        return axios.get(GlobalProfilerClient.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    /**
     * Delete settings at a specific level
     */
    static delete(workspaceId: string | null, projectId: string | null): Promise<ProfilerSettings> {
        const data = {
            params: {
                workspaceId: workspaceId,
                projectId: projectId
            }
        }

        return axios.delete(GlobalProfilerClient.baseUrl, data)
            .then(HttpUtils.RETURN_DATA);
    }
}
