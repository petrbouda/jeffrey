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
import Workspace from '@/services/api/model/Workspace';

export default class RemoteWorkspaceClient extends BasePlatformClient {

    constructor() {
        super('/remote-workspaces');
    }

    async listRemote(hostname: string, port: number): Promise<Workspace[]> {
        return super.post<Workspace[]>('/list', {hostname, port});
    }

    async createRemote(hostname: string, port: number, workspaceIds: string[]): Promise<void> {
        return super.post<void>('/create', {hostname, port, workspaceIds});
    }
}
