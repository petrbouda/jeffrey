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
 * Client for project-level profiler settings API.
 * Works for both LIVE and REMOTE workspaces - the backend handles the delegation.
 */
export default class ProjectProfilerClient {
    private static baseUrl(workspaceId: string, projectId: string): string {
        return `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects/${projectId}/profiler/settings`;
    }

    /**
     * Fetch effective settings for a project (resolved from hierarchy: project > workspace > global)
     * Works for both LIVE and REMOTE workspaces.
     */
    static fetch(workspaceId: string, projectId: string): Promise<ProfilerSettings> {
        return axios.get(ProjectProfilerClient.baseUrl(workspaceId, projectId), HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    /**
     * Upsert project-level settings
     * Works for both LIVE and REMOTE workspaces.
     */
    static upsert(workspaceId: string, projectId: string, agentSettings: string): Promise<void> {
        const content = {
            workspaceId: workspaceId,
            projectId: projectId,
            agentSettings: agentSettings,
        };

        return axios.post(ProjectProfilerClient.baseUrl(workspaceId, projectId), content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    /**
     * Delete project-level settings
     * Works for both LIVE and REMOTE workspaces.
     */
    static delete(workspaceId: string, projectId: string): Promise<void> {
        return axios.delete(ProjectProfilerClient.baseUrl(workspaceId, projectId))
            .then(HttpUtils.RETURN_DATA);
    }
}
