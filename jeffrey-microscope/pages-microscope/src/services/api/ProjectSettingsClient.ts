/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import SettingsResponse from '@/services/api/model/SettingsResponse';

export default class ProjectSettingsClient extends BasePlatformClient {
  constructor(hubId: string, workspaceId: string, projectId: string) {
    super(`/hubs/${hubId}/workspaces/${workspaceId}/projects/${projectId}/settings`);
  }

  updateName(name: string): Promise<void> {
    return super.post<void>('', { name });
  }

  fetch(): Promise<SettingsResponse> {
    return super.get<SettingsResponse>();
  }
}
