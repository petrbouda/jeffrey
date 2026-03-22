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
import RecordingFolder from "@/services/api/model/RecordingFolder.ts";

export default class ProjectRecordingFolderClient extends BasePlatformClient {

    constructor(workspaceId: string, projectId: string) {
        super(`/workspaces/${workspaceId}/projects/${projectId}/recordings/folders`);
    }

    async create(folderName: string): Promise<RecordingFolder> {
        const requestBody = {
            folderName: folderName
        }
        return super.post<RecordingFolder>('', requestBody);
    }

    async list(): Promise<RecordingFolder[]> {
        return super.get<RecordingFolder[]>();
    }

    async delete(id: string) {
        return super.del('/' + id);
    }
}
