/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import BasePlatformClient from '@shared/services/api/BasePlatformClient';
import type AdvisorProjectFolder from '@/services/api/model/AdvisorProjectFolder';
import type { AdvisorProjectFolderRequest } from '@/services/api/model/AdvisorProjectFolder';

/**
 * CRUD client for the installation-wide list of working copies the Advisor may read.
 */
export default class AdvisorProjectFoldersClient extends BasePlatformClient {
  constructor() {
    super('/advisor/project-folders');
  }

  list(): Promise<AdvisorProjectFolder[]> {
    return super.get<AdvisorProjectFolder[]>();
  }

  create(request: AdvisorProjectFolderRequest): Promise<AdvisorProjectFolder> {
    return super.post<AdvisorProjectFolder>('', request);
  }

  update(folderId: string, request: AdvisorProjectFolderRequest): Promise<AdvisorProjectFolder> {
    return super.put<AdvisorProjectFolder>(`/${folderId}`, request);
  }

  remove(folderId: string): Promise<void> {
    return super.del<void>(`/${folderId}`);
  }
}
