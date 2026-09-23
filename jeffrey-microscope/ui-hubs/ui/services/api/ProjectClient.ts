/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
import Project from '@hubs/services/api/model/Project';

export default class ProjectClient extends BasePlatformClient {
  constructor(hubId: string, workspaceId: string, projectId: string) {
    super(`/hubs/${hubId}/workspaces/${workspaceId}/projects/${projectId}`);
  }

  async fetch(): Promise<Project> {
    return super.get<Project>();
  }

  async isInitializing(): Promise<boolean> {
    return super.get<boolean>('/initializing');
  }

  async delete(): Promise<void> {
    return super.del<void>();
  }

  async restore(): Promise<void> {
    return super.post<void>('/restore', {});
  }
}
