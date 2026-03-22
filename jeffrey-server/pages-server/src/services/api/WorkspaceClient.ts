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

import BasePlatformClient from '@/services/api/BasePlatformClient';
import type Workspace from '@/services/api/model/Workspace';

export default class WorkspaceClient extends BasePlatformClient {

    constructor() {
        super('/workspaces');
    }

    /**
     * Get all workspaces
     * GET /api/workspaces
     */
    async list(excludeRemote: boolean = false): Promise<Workspace[]> {
        return super.get<Workspace[]>('', {excludeRemote: excludeRemote});
    }

    /**
     * Get a single workspace by ID
     * GET /api/workspaces/{workspaceId}
     */
    async getById(workspaceId: string): Promise<Workspace> {
        return super.get<Workspace>(`/${workspaceId}`);
    }
}
