/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import BasePlatformClient from '@shared/services/api/BasePlatformClient';
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
  /** Epoch millis. */
  createdAt: number;
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
