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
import { ProfileWithContext } from '@/stores/profileStore';

/**
 * Response from the profiles list endpoint.
 */
export interface ProfileListResponse {
  id: string;
  name: string;
  projectId: string;
  workspaceId: string;
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
export default class DirectProfileClient {
  private static readonly BASE_URL = GlobalVars.internalUrl + '/profiles';

  /**
   * Lists all profiles across all workspaces and projects.
   */
  static async listAll(): Promise<ProfileListResponse[]> {
    return axios
      .get<ProfileListResponse[]>(this.BASE_URL, HttpUtils.JSON_ACCEPT_HEADER)
      .then(HttpUtils.RETURN_DATA);
  }

  /**
   * Gets a single profile by ID with its workspace and project context.
   */
  static async get(profileId: string): Promise<ProfileWithContext> {
    return axios
      .get<ProfileWithContext>(`${this.BASE_URL}/${profileId}`, HttpUtils.JSON_ACCEPT_HEADER)
      .then(HttpUtils.RETURN_DATA);
  }
}
