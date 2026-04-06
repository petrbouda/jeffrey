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
import Project from '@/services/api/model/Project.ts';

export default class ProjectsClient extends BasePlatformClient {
  constructor() {
    super('');
  }

  async list(workspaceId: string): Promise<Project[]> {
    return super.get<Project[]>(`/workspaces/${workspaceId}/projects?includeDeleted=true`);
  }

  async namespaces(workspaceId: string): Promise<string[]> {
    return super.get<string[]>(`/workspaces/${workspaceId}/projects/namespaces`);
  }
}
