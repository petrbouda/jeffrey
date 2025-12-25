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

import axios from 'axios';
import GlobalVars from '@/services/GlobalVars';
import HttpUtils from '@/services/HttpUtils';

/**
 * Base class for profile feature API clients.
 * Provides common functionality for making HTTP requests to profile-related endpoints.
 */
export default abstract class BaseProfileClient {
    protected readonly baseUrl: string;

    /**
     * Creates a new profile client instance.
     * @param workspaceId - The workspace identifier
     * @param projectId - The project identifier
     * @param profileId - The profile identifier
     * @param featurePath - The feature-specific path suffix (e.g., 'gc', 'heap-memory', 'container')
     */
    constructor(workspaceId: string, projectId: string, profileId: string, featurePath: string) {
        this.baseUrl = `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/${featurePath}`;
    }

    /**
     * Makes a GET request to the specified path.
     * @param path - The path relative to the base URL (should start with '/' or be empty)
     * @param params - Optional query parameters
     * @returns Promise resolving to the response data
     */
    protected get<T>(path: string, params?: Record<string, any>): Promise<T> {
        const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
        const config = params
            ? HttpUtils.JSON_ACCEPT_WITH_PARAMS(params)
            : HttpUtils.JSON_ACCEPT_HEADER;
        return axios.get<T>(url, config).then(HttpUtils.RETURN_DATA);
    }

    /**
     * Makes a POST request to the specified path.
     * @param path - The path relative to the base URL (should start with '/' or be empty)
     * @param body - The request body
     * @returns Promise resolving to the response data
     */
    protected post<T>(path: string, body: Record<string, any>): Promise<T> {
        const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
        return axios.post<T>(url, body, HttpUtils.JSON_CONTENT_TYPE_HEADER).then(HttpUtils.RETURN_DATA);
    }
}
