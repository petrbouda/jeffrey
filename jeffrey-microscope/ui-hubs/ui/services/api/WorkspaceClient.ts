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
import type { RequestOptions } from '@shared/services/api/BasePlatformClient';
import Workspace from '@hubs/services/api/model/Workspace';

export interface CreateWorkspaceRequest {
  referenceId: string;
  name: string;
}

/**
 * API client for workspace operations under a connected jeffrey-hub.
 * One instance per (hubId) — workspaces are listed live via gRPC.
 */
export default class WorkspaceClient extends BasePlatformClient {
  constructor(hubId: string) {
    super(`/hubs/${hubId}/workspaces`);
  }

  async list(options?: RequestOptions): Promise<Workspace[]> {
    return super.get<Workspace[]>('', undefined, options);
  }

  async getById(workspaceId: string): Promise<Workspace> {
    return super.get<Workspace>(`/${workspaceId}`);
  }

  async create(request: CreateWorkspaceRequest): Promise<Workspace> {
    return super.post<Workspace>('', request);
  }

  async delete(workspaceId: string): Promise<void> {
    return super.del<void>(`/${workspaceId}`);
  }
}
