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
import { ProfileWithContext } from '@/stores/profileStore';

/**
 * Response from the profiles list endpoint.
 */
export interface ProfileListResponse {
  id: string;
  name: string;
  projectId: string;
  projectName: string;
  workspaceId: string;
  workspaceName: string;
  createdAt: string;
  eventSource: string;
  enabled: boolean;
  durationInMillis: number;
  sizeInBytes: number;
}

/**
 * Direct profile API client that uses simplified URLs.
 * Works with /api/internal/profiles endpoints without requiring workspaceId/projectId.
 */
export default class DirectProfileClient extends BasePlatformClient {
  constructor() {
    super('/profiles');
  }

  /**
   * Lists all profiles across all workspaces and projects.
   */
  async listAll(): Promise<ProfileListResponse[]> {
    return super.get<ProfileListResponse[]>();
  }

  /**
   * Gets a single profile by ID with its workspace and project context.
   */
  async getById(profileId: string): Promise<ProfileWithContext> {
    return super.get<ProfileWithContext>(`/${profileId}`);
  }
}
